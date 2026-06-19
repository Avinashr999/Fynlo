/*
 * macrobenchmark/build.gradle.kts
 *
 * com.android.test module — not part of the shipped APK. Builds and runs
 * on a connected device/emulator and measures the `benchmark` variant of :app.
 */

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.fynlo.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28      // Macrobenchmark needs API 28+ for accurate measurements
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        // Mirror the :app benchmark build type so the test module
        // picks the right variant of the app to benchmark.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // Match the :app product flavors. :app has `prod` and `dev` — we benchmark
    // `prod` since that's what ships to Play Store.
    flavorDimensions += "env"
    productFlavors {
        create("prod") { dimension = "env" }
        create("dev")  { dimension = "env" }
    }
}

baselineProfile {
    // Use whatever device is connected via USB or emulator. Add a managed
    // device here later if you want fully reproducible runs in CI.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Inject the tested app's applicationId into the instrumentation args so
// uiautomator selectors that use TARGET_PACKAGE resolve correctly across
// flavors (prod = app.fynlo, dev = app.fynlo.dev).
androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId ?: "" }
        )
    }
}
