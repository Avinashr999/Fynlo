# Fynlo - Complete AI Portability File
**Project Name**: Fynlo
**Version**: 1.8.0
**Platform**: Android (Kotlin, Jetpack Compose, Room)

## 1. Project Overview
A professional financial ledger for personal use. Every rupee is tracked using double-entry principles (origin to destination). Features include PIN security, automated interest (anniversary-based step compounding), data exports (PDF/CSV), and a central contact book with unique IDs.

## 2. Core Features (Implemented)
- **4-Digit PIN Security**: PIN lock (default: 1234) on startup.
- **Master Dashboard**: Shows Net Worth, Total Assets (Cash+Invest+Lent), and Total Liabilities (Principal+Interest).
- **Spending Analytics**: Progress-bar based category breakdown.
- **Passbook System**: Individual bank/cash statements accessible by tapping account names.
- **Anniversary Interest Engine**: Compounding happens only on the year anniversary.
- **Data Restore**: Import/Export JSON functionality for disaster recovery.

## 3. Key Source Code

### Interest Engine (logic/InterestEngine.kt)
Uses A = P * (1 + r/100)^n for compounding and SI = P*R*T/100 for simple interest.
```kotlin
// Compounding logic:
repeat(fullYears.toInt()) { currentTotal += (currentTotal * rAnnual) }
val siForPartialYear = (currentTotal * rAnnual * remainingDays.toDouble()) / 365.0
```

### Navigation & Double Entry (ui/Navigation.kt)
Every Add dialog captures a "Source" or "Destination" to ensure balanced books.
```kotlin
// Example flow:
onConfirm = { borrower, source -> viewModel.addBorrowerWithSource(borrower, source) }
```

## 4. How to Resume
Upload this file and the `FYNLO_ALL_SOURCE.txt` (if created) to any AI like Claude or ChatGPT. The app is 100% production-ready for personal ledger usage.

---

## 5. Performance Benchmarking Workflow (added May 24, 2026)

Fynlo has a working Jetpack Macrobenchmark setup. Use this to measure cold-start
TTID/TTFD and PeopleScreen scroll jank on a real connected device, and to
regenerate the baseline profile when needed.

### 5.1 Files involved

| Path | Purpose |
|---|---|
| `macrobenchmark/` | Separate `com.android.test` module. Tests live here. |
| `macrobenchmark/.../StartupBenchmark.kt` | Cold-start TTID across None/Partial/Full compilation modes |
| `macrobenchmark/.../ScrollBenchmark.kt` | PeopleScreen LazyColumn scroll, FrameTimingMetric |
| `macrobenchmark/.../BaselineProfileGenerator.kt` | Generates `baseline-prof.txt` |
| `macrobenchmark/.../Constants.kt` | `TARGET_PACKAGE`, `skipFirstLaunch()`, `seedDummyData()` |
| `app/src/main/baseline-prof.txt` | The actual shipped profile (~18k entries, 1.6 MB) |
| `bench-baseline-fynlo.json` | Frozen pre-profile startup baseline (regression check) |
| `bench-startup-with-profile.json` | Post-profile startup baseline |
| `bench-scroll-baseline-fynlo.json` | Frozen scroll baseline |

### 5.2 Running benchmarks (assumes one device connected via ADB)

```powershell
# Startup (3 tests x 10 iters, ~25 min)
.\gradlew :macrobenchmark:connectedProdBenchmarkBenchmarkAndroidTest `
  -P android.testInstrumentationRunnerArguments.class=app.fynlo.macrobenchmark.StartupBenchmark

# Scroll (2 tests x 8 iters, ~20 min)
.\gradlew :macrobenchmark:connectedProdBenchmarkBenchmarkAndroidTest `
  -P android.testInstrumentationRunnerArguments.class=app.fynlo.macrobenchmark.ScrollBenchmark

# Results JSON path:
# macrobenchmark/build/outputs/connected_android_test_additional_output/
#   prodBenchmarkBenchmark/connected/<DEVICE>/app.fynlo.macrobenchmark-benchmarkData.json
```

### 5.3 Regenerating the baseline profile (THE TRICKY ONE)

The standard `./gradlew :app:generateProdBaselineProfile` is currently
**BROKEN** in this project. It fails with:

```
KspAATask.kt:772  isParentOf
java.nio.file.InvalidPathException: Illegal char <?> at index 55:
  C:\Users\user\AndroidStudioProjects\Fynlo\app\provider(?)
```

This is a real KSP2 + AGP 9 bug (same family as google/ksp#2844). The
`androidx.baselineprofile` plugin creates a `prodNonMinifiedRelease` variant
whose path is a Gradle `Provider` not resolved when KSP reads it. Cannot fall
back to KSP1 (removed in 2.3.x). `--no-configuration-cache` does not help.

**Workaround - invoke the test directly:**

```powershell
# 1. Temporarily flip isMinifyEnabled = false in app/build.gradle.kts inside
#    the benchmark { ... } build type. Without this, BaselineProfileRule
#    captures R8-obfuscated method names ('a.b.c.d') instead of real classes
#    and the resulting profile is useless.

# 2. Run the BaselineProfileGenerator test directly. Two flags are essential:
.\gradlew :macrobenchmark:connectedProdBenchmarkBenchmarkAndroidTest `
  -P android.testInstrumentationRunnerArguments.class=app.fynlo.macrobenchmark.BaselineProfileGenerator `
  -P android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

The `enabledRules=BaselineProfile` flag is non-obvious but **required** -
without it, `BaselineProfileRule:54` does `assumeTrue(...)` and the test
silently SKIPS. (Normally the gradle task injects this for you; we bypass
the task because of the KSP2 bug.)

**Result location:**

```
macrobenchmark/build/outputs/connected_android_test_additional_output/
  prodBenchmarkBenchmark/connected/<DEVICE>/
  BaselineProfileGenerator_generate-baseline-prof.txt
```

**Copy it to the canonical location and flip minify back on:**

```powershell
cp 'macrobenchmark/build/outputs/.../BaselineProfileGenerator_generate-baseline-prof.txt' `
   'app/src/main/baseline-prof.txt'

# Then restore isMinifyEnabled = true and isShrinkResources = true on the
# benchmark build type. Re-run StartupBenchmark to verify the win.
```

### 5.4 CUJ navigation helpers (Constants.kt)

Two extension functions on `MacrobenchmarkScope`:

- **`skipFirstLaunch()`** - dismisses Onboarding (Skip) -> FirstLaunchSetup (Skip)
  -> LoginScreen (Continue without signing in). Uses null-safe `?.click()` so
  iterations after the first (where DataStore has persisted the completion
  flags) just no-op past these screens.

- **`seedDummyData()`** - drawer -> Settings -> swipes to find "Load Test Data
  (QA)" -> confirms "Load" dialog -> `pressBack()`. Uses raw `device.swipe()`
  loops, NOT `scrollUntil(By.scrollable(true))` - that approach is unreliable
  with Compose LazyColumns whose accessibility nodes don't report `scrollable`.

**Gotcha:** Don't add `seedDummyData()` to ScrollBenchmark's setupBlock. If the
seed button is not found within the swipe budget, the CUJ leaves the app stuck
on Settings and FrameTimingMetric throws "Observed no renderthread slices".
BaselineProfileGenerator can tolerate this (the profile still captures hot
paths from the partial walk); ScrollBenchmark cannot.

### 5.5 Known failure modes and what they mean

| Symptom | Cause | Fix |
|---|---|---|
| `Tests 0/N completed (1 skipped)` from BaselineProfileGenerator | Missing `enabledRules=BaselineProfile` instrumentation arg | Add the flag (see 5.3) |
| `InvalidPathException: Illegal char <?>` | KSP2 + baselineprofile plugin bug | Use direct test workaround (5.3) |
| `Observed no renderthread slices in trace` | CUJ stuck on a screen that is not redrawing | Simplify the setupBlock; remove `seedDummyData()` |
| `frameCount = 1` per iteration but BUILD SUCCESSFUL | CUJ did not reach target screen; fell back to gesture-swipe on whatever was visible | Check device mid-test with `adb shell dumpsys activity activities` and grep for `mFocusedApp` |
| Profile only contains R8 obfuscated names (`a.b.c.d`) | `isMinifyEnabled = true` on benchmark build type during generation | Flip to false, regenerate, flip back |

### 5.6 Measured impact (CPH2767, Android 16, May 24 2026)

| Compilation Mode | TTID before profile | TTID after profile | Win |
|---|---|---|---|
| None | 307.3 ms | 276.7 ms | -10.0% |
| Partial | 258.4 ms | 247.2 ms | -4.3% (the real ship-mode win) |
| Full | 317.8 ms | 313.1 ms | -1.5% |

Partial is now 21% faster than Full - classic outcome of a well-targeted
baseline profile. Ship Partial-mode AOT compilation (which is what
`profileinstaller` triggers automatically when `baseline-prof.txt` is bundled
in the APK).

### 5.7 Things NOT to forget when modifying the perf setup

- The `<profileable android:shell="true" />` tag in `app/src/main/AndroidManifest.xml`
  is REQUIRED for macrobench to read traces. It is intentionally in `main/`
  (not `debug/`) because Google Play's perf gathering needs it in prod too.
  This is standard for production Android apps in 2026; don't move it.

- `MainActivity.kt` wraps `MainNavigation` in `ReportDrawnWhen { ready }`.
  This is the TTFD signal - if you remove or break this, all `timeToFullDisplayMs`
  metrics report 30-second timeouts.

- `PeopleScreen.kt` has `.testTag("people_list")` on its LazyColumn, with
  `testTagsAsResourceId = true` semantics. ScrollBenchmark depends on this.
