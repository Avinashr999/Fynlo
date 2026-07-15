import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

abstract class VerifyPlayReadinessTask : DefaultTask() {
    @get:InputFile
    abstract val aabFile: RegularFileProperty

    @get:InputFile
    abstract val mappingFile: RegularFileProperty

    @get:InputFile
    abstract val nativeSymbolsFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val aab = aabFile.get().asFile
        val mapping = mappingFile.get().asFile
        val nativeSymbols = nativeSymbolsFile.get().asFile

        check(aab.isFile) { "Missing prod release AAB: ${aab.absolutePath}" }
        check(mapping.isFile && mapping.length() > 0L) { "Missing R8 mapping file: ${mapping.absolutePath}" }
        check(nativeSymbols.isFile && nativeSymbols.length() > 0L) {
            "Missing native symbols zip: ${nativeSymbols.absolutePath}"
        }

        logger.lifecycle("Play readiness verified:")
        logger.lifecycle(" - R8 minification: minifyProdReleaseWithR8 completed")
        logger.lifecycle(" - Resource shrinking: optimizeProdReleaseResources completed")
        logger.lifecycle(" - AAB: ${aab.absolutePath}")
        logger.lifecycle(" - Mapping: ${mapping.absolutePath}")
        logger.lifecycle(" - Native symbols: ${nativeSymbols.absolutePath}")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics.plugin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)
    alias(libs.plugins.baselineprofile)
}
android {
    namespace = "app.fynlo"
    compileSdk = 36

    // Signing is only configured when valid credentials exist. Without them,
    // assembleRelease produces app-release-unsigned.apk (useful for verification).
    // Prefer secrets/keystore.properties (gitignored folder); fall back to root.
    val keystorePropsFile = listOf("secrets/keystore.properties", "keystore.properties")
        .map(rootProject::file).firstOrNull { it.exists() }
        ?: rootProject.file("secrets/keystore.properties")
    val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
    val hasReleaseSigning   = keystorePropsFile.exists() || envKeystorePassword.isNotEmpty()
    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                if (keystorePropsFile.exists()) {
                    val keystoreProps = Properties()
                    keystoreProps.load(keystorePropsFile.inputStream())
                    // storeFile is resolved relative to the repo root, not the app/ module.
                    storeFile     = rootProject.file(keystoreProps["storeFile"] as String)
                    storePassword = keystoreProps["storePassword"] as String
                    keyAlias      = keystoreProps["keyAlias"] as String
                    keyPassword   = keystoreProps["keyPassword"] as String
                } else {
                    storeFile     = file(System.getenv("KEYSTORE_FILE") ?: "fynlo-release.jks")
                    storePassword = envKeystorePassword
                    keyAlias      = System.getenv("KEY_ALIAS") ?: "fynlo"
                    keyPassword   = System.getenv("KEY_PASSWORD").orEmpty()
                }
            }
        }
    }

    defaultConfig {
        applicationId = "app.fynlo"
        minSdk = 26
        targetSdk = 36
        versionCode = 238
        versionName = "3.2.114"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── Dev / Prod separation (#01/#11) ───────────────────────────────────────
    // prod  → app.fynlo,      reads app/google-services.json (the live project)
    // dev   → app.fynlo.dev,  reads app/src/dev/google-services.json
    //
    // The dev google-services.json checked in is a PLACEHOLDER pointing at the
    // same project so the build stays green. To get true isolation (so wiping
    // dev never touches prod data), create a SEPARATE Firebase project, register
    // app.fynlo.dev in it, and drop its google-services.json at
    // app/src/dev/google-services.json. See app/src/dev/README.md.
    flavorDimensions += "env"
    productFlavors {
        create("prod") {
            dimension = "env"
            isDefault = true
        }
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix   = "-dev"
            // app name override lives in app/src/dev/res/values/strings.xml
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("staging") {
            initWith(getByName("debug"))
            versionNameSuffix  = "-staging"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
        // Release-shaped build used by :macrobenchmark. Not debuggable
        // (Macrobenchmark refuses to run against a debuggable APK) but the
        // `<profileable>` marker in AndroidManifest.xml lets the harness read
        // tracing data. signingConfig fallback to debug so no keystore is required.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "ExtraTranslation"
    }

    // Room schema export for migration validation
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    // Unit-test config — Robolectric needs Android resources to spin up
    // an in-memory Room database (FynloDatabase). Used by data-integrity
    // tests in app/src/test/java/app/fynlo/data/.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Expose Room's exported schema JSONs (under app/schemas/) to the
    // androidTest APK as assets. MigrationTestHelper reads them from there
    // when spinning up a database at a prior schema version. Without this
    // config the instrumented FynloDatabaseMigrationTest can't find the
    // schemas and fails with a confusing "Schema for X not found" error.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Excel Generation

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.analytics)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.billing.ktx)

    // Baseline Profile installer (consumes profile produced by :macrobenchmark)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":macrobenchmark"))

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    // Robolectric + AndroidX test core — lets JVM unit tests spin up an
    // in-memory Room database (FynloDatabase needs a Context, which
    // Robolectric provides). Used by RecalculateBalancesDataIntegrityTest
    // and any future C01/C02-style real-SQL regression tests.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val prodReleaseNativeSymbols by tasks.registering(Zip::class) {
    group = "release"
    description = "Packages prod release native symbol tables for Play Console upload."
    dependsOn("stripProdReleaseDebugSymbols")

    from(layout.buildDirectory.dir("intermediates/stripped_native_libs/prodRelease/stripProdReleaseDebugSymbols/out/lib")) {
        include("**/*.so")
    }

    archiveFileName.set("fynlo-prod-release-native-symbols.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/prodRelease"))
}

tasks.matching { it.name == "bundleProdRelease" }.configureEach {
    finalizedBy(prodReleaseNativeSymbols)
}

tasks.register<VerifyPlayReadinessTask>("verifyProdReleasePlayReadiness") {
    group = "verification"
    description = "Verifies the Play upload artifacts that silence R8 and native-symbol warnings."
    dependsOn("bundleProdRelease", "minifyProdReleaseWithR8", "optimizeProdReleaseResources", prodReleaseNativeSymbols)

    aabFile.set(layout.buildDirectory.file("outputs/bundle/prodRelease/app-prod-release.aab"))
    mappingFile.set(layout.buildDirectory.file("outputs/mapping/prodRelease/mapping.txt"))
    nativeSymbolsFile.set(prodReleaseNativeSymbols.flatMap { it.archiveFile })
}
