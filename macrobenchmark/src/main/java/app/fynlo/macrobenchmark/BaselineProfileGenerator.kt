package app.fynlo.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile by walking Fynlo's Critical User Journey:
 * launch → Contact Book → scroll → back → Reports → back → Settings → back.
 *
 * Output:
 *   macrobenchmark/build/outputs/managed_device_android_test_additional_output/
 *     pixel6Api33/BaselineProfileGenerator_generate-baseline-prof.txt
 *
 * The baselineprofile plugin copies the result into
 *   app/src/main/baseline-prof.txt
 *
 * Run:
 *   ./gradlew :app:generateBaselineProfile
 *
 * Expected impact: ~25% faster cold start on real devices once shipped.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 15,
            stableIterations = 3,
        ) {
            // 1. Cold launch
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)

            // 2. Get past Onboarding → FirstLaunchSetup → Login.
            skipFirstLaunch()

            // 3. Seed dummy data. This makes the profile capture the post-login,
            //    data-populated rendering paths (Room queries returning rows,
            //    LazyColumn item composition with real items, chart data binding,
            //    etc.) — that's where the bulk of cold-start CPU goes on a real
            //    user's device, so it's what we want compiled ahead-of-time.
            seedDummyData()

            // 4. Walk bottom-nav tabs to capture each screen's composition path.
            //    Bottom nav contentDescription = screen.label, so use By.desc.
            //    Labels: Dashboard / Loans / Invest / Reports / Expenses.
            listOf("Loans", "Invest", "Reports", "Expenses", "Dashboard").forEach { tab ->
                device.findObject(By.desc(tab))?.click()
                device.waitForIdle()
            }

            // 5. Open the drawer, go to Contact Book, scroll the list.
            device.wait(Until.findObject(By.desc("Menu")), 5_000)?.click()
            device.wait(Until.findObject(By.text("Contact Book")), 5_000)?.click()
            device.wait(Until.findObject(By.res(TARGET_PACKAGE, "people_list")), 5_000)
                ?.let { list ->
                    list.fling(Direction.DOWN)
                    device.waitForIdle()
                    list.fling(Direction.UP)
                    device.waitForIdle()
                }
        }
    }
}
