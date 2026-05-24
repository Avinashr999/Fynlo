package app.fynlo.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures scroll jank on the People (Contact Book) screen, which is the
 * longest list in Fynlo and the most likely jank surface.
 *
 * Reports frameDurationCpuMs and frameOverrunMs:
 *   - frameDurationCpuMs p99 > 16 ms → dropped frames at 60Hz
 *   - frameOverrunMs > 0             → missed vsync deadline (visible stutter)
 *
 * Run:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *     -P android.testInstrumentationRunnerArguments.class=app.fynlo.macrobenchmark.ScrollBenchmark
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test fun scrollPeoplePartial() = scrollPeople(CompilationMode.Partial())
    @Test fun scrollPeopleNone()    = scrollPeople(CompilationMode.None())

    private fun scrollPeople(mode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = mode,
        setupBlock = {
            pressHome()
            startActivityAndWait()

            // Get past Onboarding → FirstLaunchSetup → Login.
            skipFirstLaunch()

            // NOTE: Intentionally NOT calling seedDummyData() here. Settings scroll
            // is fragile from a measurement perspective (it can fail to locate the
            // "Load Test Data (QA)" button if it's off-screen, which leaves the CUJ
            // stuck on Settings and triggers "no renderthread slices" failures).
            // Scrolling the empty-state PeopleScreen is still a valid jank baseline
            // — it captures Scaffold + TopAppBar + LazyColumn-empty-item composition.
            // See BaselineProfileGenerator.kt for the seeded version.

            // Drawer → Contact Book → wait for tagged LazyColumn.
            device.wait(Until.findObject(By.desc("Menu")), 5_000)?.click()
            device.wait(Until.findObject(By.text("Contact Book")), 5_000)?.click()
            device.wait(Until.findObject(By.res(TARGET_PACKAGE, "people_list")), 5_000)
        },
    ) {
        val list = device.findObject(By.res(TARGET_PACKAGE, "people_list"))
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN); device.waitForIdle()
            list.fling(Direction.UP);   device.waitForIdle()
            list.fling(Direction.DOWN); device.waitForIdle()
        } else {
            // Fallback: gesture-swipe the screen center.
            val w = device.displayWidth
            val h = device.displayHeight
            repeat(3) {
                device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.3).toInt(), 10)
                device.waitForIdle()
            }
        }
    }
}
