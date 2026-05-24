package app.fynlo.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold-start of Fynlo across three compilation modes:
 *
 *   - None()    : worst-case — no AOT, no baseline profile (rare in practice)
 *   - Partial() : with baseline profile applied — what real users see
 *   - Full()    : best-case ceiling, everything AOT
 *
 * Run:
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *     -P android.testInstrumentationRunnerArguments.class=app.fynlo.macrobenchmark.StartupBenchmark
 *
 * Reports:
 *   - timeToInitialDisplayMs (TTID)  — first frame drawn
 *   - timeToFullDisplayMs    (TTFD)  — your ReportDrawnWhen { isSyncReady } fired
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test fun startupColdNone()    = startup(CompilationMode.None())
    @Test fun startupColdPartial() = startup(CompilationMode.Partial())
    @Test fun startupColdFull()    = startup(CompilationMode.Full())

    private fun startup(mode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // Wait until the launcher activity is rendered. TTFD comes from
        // ReportDrawnWhen { isSyncReady } in MainActivity, fired automatically
        // once FinanceViewModel.isSyncReady flips to true.
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
    }
}
