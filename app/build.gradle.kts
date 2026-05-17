import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics.plugin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)
}

android {
    namespace = "app.fynlo"
    compileSdk = 36

    // Signing is only configured when valid credentials exist. Without them,
    // assembleRelease produces app-release-unsigned.apk (useful for verification).
    val keystorePropsFile = rootProject.file("keystore.properties")
    val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
    val hasReleaseSigning   = keystorePropsFile.exists() || envKeystorePassword.isNotEmpty()
    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                if (keystorePropsFile.exists()) {
                    val keystoreProps = Properties()
                    keystoreProps.load(keystorePropsFile.inputStream())
                    storeFile     = file(keystoreProps["storeFile"] as String)
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
        versionCode = 122
        versionName = "3.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("staging") {
            initWith(getByName("debug"))
            isMinifyEnabled   = true
            isShrinkResources = true
            versionNameSuffix  = "-staging"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
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
    implementation("androidx.datastore:datastore-preferences:1.1.4")

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
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.google.signin)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}