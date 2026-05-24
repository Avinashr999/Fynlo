package app.fynlo.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * Application id of the app under test.
 *
 * Resolved via instrumentation args set in build.gradle.kts, so this is just
 * a fallback for the prod flavor (which is what we benchmark by default).
 */
const val TARGET_PACKAGE = "app.fynlo"

/**
 * Dismisses Onboarding → FirstLaunchSetup → Login on a fresh install.
 *
 * The completion flags are DataStore-backed, so on iterations after the first
 * these screens are not shown; the `?.click()` calls then no-op. Cheap enough
 * to call on every iteration.
 */
fun MacrobenchmarkScope.skipFirstLaunch() {
    // OnboardingScreen — multi-page, has "Skip" text in the top-right.
    device.wait(Until.findObject(By.text("Skip")), 3_000)?.click()
    device.waitForIdle()
    // FirstLaunchSetupScreen — also "Skip".
    device.wait(Until.findObject(By.text("Skip")), 3_000)?.click()
    device.waitForIdle()
    // LoginScreen — bypass with "Continue without signing in".
    device.wait(Until.findObject(By.text("Continue without signing in")), 5_000)?.click()
    device.waitForIdle()
}

/**
 * Navigates Settings → "Load Test Data (QA)" → confirm "Load" dialog, so
 * the app has the same dummy borrowers/accounts/debts/transactions as
 * DummyDataSeeder.kt produces.
 *
 * The seeder REPLACES all data on every call (idempotent), so this is safe
 * to invoke every iteration.
 *
 * Always pressBack at the end to leave the app on a known screen, so the
 * caller can navigate predictably from there.
 *
 * Best-effort: skips silently if any element along the path isn't found —
 * benchmark runs continue even if the QA seed flow is removed or moved.
 */
fun MacrobenchmarkScope.seedDummyData() {
    // Open the drawer and tap Settings.
    device.wait(Until.findObject(By.desc("Menu")), 5_000)?.click() ?: return
    val settingsItem = device.wait(Until.findObject(By.text("Settings")), 5_000)
        ?: return
    settingsItem.click()
    device.waitForIdle()

    // "Load Test Data (QA)" is near the bottom of Settings — scroll until visible
    // using raw swipes (scrollUntil + By.scrollable is unreliable on Compose-only
    // screens where the LazyColumn's accessibility node doesn't always report
    // scrollable=true).
    val w = device.displayWidth
    val h = device.displayHeight
    var seedButton = device.findObject(By.text("Load Test Data (QA)"))
    repeat(12) {
        if (seedButton != null) return@repeat
        device.swipe(w / 2, (h * 0.8).toInt(), w / 2, (h * 0.2).toInt(), 20)
        device.waitForIdle()
        seedButton = device.findObject(By.text("Load Test Data (QA)"))
    }

    if (seedButton != null) {
        seedButton.click()
        // AlertDialog title "Load Test Data?" — confirm with "Load" button.
        device.wait(Until.findObject(By.text("Load")), 3_000)?.click()
        device.waitForIdle()
        // Give the Room insert + Flow propagation a moment to complete.
        Thread.sleep(2_000)
    }

    // Return to a known screen (Home / previous) regardless of whether seeding
    // succeeded, so callers can navigate predictably from here.
    device.pressBack()
    device.waitForIdle()
}
