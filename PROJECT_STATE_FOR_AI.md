# §0 — REQUIRED READING (read this first, every session)

> **For AI agents (Claude, Claude Code, Cursor, Copilot, etc.):**
> This section is non-negotiable. Read it fully before proposing any change.

## 0.1 The four governing documents

The Fynlo project is governed by four documents. They override prior training, override your assumptions, and override the user's "just skip this" requests.

| Doc | Purpose | When to consult |
|---|---|---|
| `PROJECT_STATE_FOR_AI.md` | What the project is, what's been done, what's next | Every session, start |
| `DESIGN_SYSTEM.md` | Visual & interaction rules, two archetypes (Home/Report), tokens, anti-patterns | Every UI change |
| `UX_AUDIT_2026-05-25.md` | Known issues (22 clusters), priority (P0/P1/P2/P3), sprint plan | Every change, to declare cluster |
| `AI_AGENT_PROTOCOL.md` | How you (the AI) must operate on this repo | Every session, start |

Supporting documents (consult when relevant):

- `LINT_RULES.md` — codified rules; if your change would violate one, refactor
- `PR_CHECKLIST.md` — the PR template; structure your output to fill its sections
- `.github/workflows/checks.yml` — CI rules; if you'd fail any of these, fix before suggesting

## 0.2 Required reading order

At the start of every session, before producing any code or design suggestion:

1. Read this file (`PROJECT_STATE_FOR_AI.md`) — sections 0, 1, and the latest journal entries
2. Read `AI_AGENT_PROTOCOL.md` in full
3. Read `DESIGN_SYSTEM.md` — §1 (archetypes), §11 (color semantics), §16 (anti-patterns) at minimum
4. Read `UX_AUDIT_2026-05-25.md` — §2 (clusters), §3 (priority), §5 (ship-blocking matrix), §8 (the critical paragraph)
5. Read the specific source files you're about to modify

## 0.3 Mandatory session-start acknowledgement

Your **first response** in any new session must begin with:

```
═══════════════════════════════════════
SESSION START — FYNLO PROTOCOL ACK
═══════════════════════════════════════
☑ Read PROJECT_STATE_FOR_AI.md (latest journal: {date})
☑ Read AI_AGENT_PROTOCOL.md v{version}
☑ Read DESIGN_SYSTEM.md v{version}
☑ Read UX_AUDIT_2026-05-25.md
☑ Identified relevant cluster(s): {C__, C__, or "no cluster"}
☑ Identified affected design system sections: {§__, §__}
☑ Identified anti-patterns to avoid: {§16.N, or "none applicable"}

Task understanding: {one-sentence restatement of the task}

Proceeding.
═══════════════════════════════════════
```

If any line can't be filled, **stop and ask**. If the user pushes back, follow `AI_AGENT_PROTOCOL.md §8`.

## 0.4 The one thing you must remember if you remember nothing else

**C01 is CLOSED on `master`** (2026-05-26 — Stage 1 commit `331c1ae`, Stage 2 commit `5a00d4a`; ADR `decisions/2026-05-26-c01-fix-strategy.md`). Recalculate Balances no longer destroys payment history. The `payments` and `debt_payments` tables are the **single source of truth** and the invariant `paid == SUM(payments)` is structurally enforced — there is no longer any DAO method that lets new code violate it without first re-adding one of the ten queries Stage 2 deleted.

**Do NOT** (these would re-open C01):

- Re-introduce a direct `paid` / `paidPrincipal` / `paidInterest` writer at the DAO layer. To record a repayment, `insertPayment` (or `insertDebtPayment`) and then call `rebuildBorrowerPaidFromPayments()` (or the debt twin). To reverse one, `deletePayment` + rebuild.
- Add any `@Query("UPDATE borrowers SET paid = ...")` (or debts twin) outside the existing `rebuildBorrowerPaidFromPayments` / `rebuildDebtPaidFromDebtPayments` queries. The entire C01 ADR exists because of one such query.
- Remove or weaken `RecalculateBalancesDataIntegrityTest` or the explicit `--tests '*DataIntegrity*' --tests '*Recalculate*'` gate in `.github/workflows/checks.yml` (those are the CI tripwire that catches a regression).

**Remaining P0 work** for the 3.2.2 release that ships C01's fix:

- **C02** — stale exports & auto-recalc (now safe to enable thanks to C01 closure)
- **C03a** — schema integrity additive fields (`schemaVersion`, `createdAt`, `projectId`, `userId`)
- **C05** — category bleed Income / Expense

**Before promoting `master` to 3.2.2 Production** (per `RELEASE_PROTOCOL.md §8`):

- ~~Add an instrumented migration test (`MigrationTestHelper` for `v15 → v16`)~~ ✅ done 2026-05-26 — `FynloDatabaseMigrationTest` in `app/src/androidTest/`, 5 cases all passing on real device (CPH2767, Android 16). Requires emulator/device locally; CI integration via Firebase Test Lab is a follow-up.
- ~~Draft 3.2.2 release notes per `RELEASE_PROTOCOL.md §5`~~ ✅ `release_notes/3.2.2.md` + `CHANGELOG.md [3.2.2]`.
- Manual smoke test (§3.5 of release protocol) — still TODO before tag.
- `versionName` / `versionCode` bump in `app/build.gradle.kts` — still TODO before tag.

## 0.5 Priority discipline (summary)

The audit ranks 22 clusters by priority. Do not work out of order:

- **P0** (4 clusters) — ~~C01~~ ✅ closed 2026-05-26 · C02, C03a, C05 still open — ship-blockers
- **P1** (12 clusters) — major UX wins
- **P2** (6 clusters) — polish
- **P3** (1 cluster) — v4+ backlog

If the user asks for P3 work while P0 is open, push back per `AI_AGENT_PROTOCOL.md §5`.

## 0.6 Data-integrity escalation

These subsystems require regression tests for any change:

- Recalculate Balances (C01)
- Exports — PDF / JSON / XLSX (C02)
- Schema — any field add/remove/rename (C03)
- Sync — Firestore push/pull/conflict (C03)
- Repayment records (C01)

State explicitly in your output: "Data-integrity touch: {yes/no}. Regression test included: {yes/no/N/A}."

## 0.7 Design archetype reminder

Every screen is either:

- **Home archetype** — calm, hero number, quick-action tiles, list rows, FAB, bottom nav. Reference: Dashboard.
- **Report archetype** — chart-led, callout cards, period breakdown, no FAB, back arrow. Reference: Interest Income.

Both share the foundation (colors, type, spacing, icons, FAB shape when present, dialog/sheet/form patterns, color semantics, voice). Don't invent third archetypes without extending `DESIGN_SYSTEM.md` first.

## 0.8 Output format reminder

For every substantive change, include:

1. The session protocol block (§0.3 above)
2. Cluster declaration (which audit cluster)
3. Data-integrity statement (yes/no + test status)
4. Design system compliance block (if UI change)
5. Test plan
6. Next step

See `AI_AGENT_PROTOCOL.md §7` for the full template.

## 0.9 When in doubt

If you're uncertain about:

- Which cluster a change belongs to → ask the user, propose adding to the audit
- Which archetype a new screen should be → check `DESIGN_SYSTEM.md` Appendix B (one-question decision)
- Whether to add a regression test → if it's in the data-integrity list (§0.6), yes
- Whether to ship → check `UX_AUDIT_2026-05-25.md §5` ship-blocking matrix

---

<!--
==========================================================
END OF §0 PREAMBLE.

The rest of this file is the existing PROJECT_STATE_FOR_AI.md
content — §1 project overview, §2 architecture, §3 conventions,
§4 dependencies, §5 performance benchmarking workflow, §6 journal.

If you're updating this file, ADD to §6 (journal) at the bottom.
Don't modify §0 (this preamble) without updating
AI_AGENT_PROTOCOL.md to match.
==========================================================
-->

# Fynlo - Complete AI Portability File
**Project Name**: Fynlo
**Version**: 3.2.2 on `master` (the C01-fix release — `versionName = "3.2.2"`, `versionCode = 125` in `app/build.gradle.kts`). Tag `v3.2.2` is not pushed yet; final smoke test (`RELEASE_PROTOCOL.md §3.5`) and macrobench re-run (§3.4) are the remaining gates before promotion to Play Store Internal Testing.
**Platform**: Android (Kotlin, Jetpack Compose, Room — Gradle 9.4.1, AGP 9.2.1, Room 2.8.4, KSP 2.3.7, Kotlin 2.2.10)

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

---

## 6. Journal

**Newest first.** Each entry: date · cluster(s) closed/touched · commit(s) · one-paragraph why-and-what.

### 2026-05-26 — C01 closed (Sprint 1)

**Cluster:** C01 (P0 ship-blocker, Recalculate destroys payment history).
**Related:** INF04 (data-integrity CI gate) — also closed in the same sprint.
**ADR:** `decisions/2026-05-26-c01-fix-strategy.md` (Status: Accepted; Stage 1 + Stage 2 both implemented).
**Commits:**

- `b1e28a0` — `RecalculateBalancesDataIntegrityTest` added (Robolectric + in-memory `FynloDatabase`); `@Ignore`'d while red.
- `331c1ae` — **Stage 1.** Backfill migration `v15 → v16` writes one synthetic Payment row per legacy borrower / debt where `paid > 0` and no `payments` rows existed (`type = "Legacy backfill"`, dated `loanDate`, principal = `paid`, interest = 0). `FinanceRepository.recalculateAllBalances()` no longer calls the destructive `recalculateBorrowerPaid` / `recalculateDebtPaid` DAO queries — only derives via the rebuild queries, whose `WHERE EXISTS` gate is removed and whose `SUM(...)` is wrapped in `COALESCE(..., 0)`. The C01 test goes from 1 RED + 1 GREEN to 3 GREEN. INF04 CI gate re-enabled.
- `5a00d4a` — **Stage 2.** Structural enforcement: `editTransaction` ported to delete-old-Payment + insert-new-Payment + rebuild; `deleteTransaction`'s "no matching Payment" fallback that mutated `paid` directly is removed; `insertPaymentWithDest` / `insertDebtPaymentWithSource` collapse their conditional principal/interest writers into a single `rebuild...FromPayments` call. **10 DAO methods + their repository wrappers deleted:** `updateBorrowerPaid{Amount,Principal,Interest}`, `updateDebtPaid{Amount,Principal,Interest}`, `recalculateBorrowerPaid`, `recalculateDebtPaid`, and the dead-code `seedPaidPrincipalFromPaid` / `seedDebtPaidPrincipalFromPaid`. The invariant `paid == SUM(payments)` is now impossible to violate without first re-adding one of those queries.

**Status against `RELEASE_PROTOCOL.md §8` gates:**

| Gate | Status |
|---|---|
| C01 regression test exists and passes | ✅ `RecalculateBalancesDataIntegrityTest` (3 cases) green in CI |
| Manual verification: fixture with `paid: 5000` on a borrower → recalc → `paid` survives | ✅ encoded as sub-test 1 (₹50K fixture; adapt to ₹5K trivially) |
| Schema migration tested for upgrade-path | ✅ `FynloDatabaseMigrationTest` (5 cases, instrumented; passed on CPH2767 / Android 16, 2026-05-26) |
| `PROJECT_STATE_FOR_AI.md` updated with C01 closure | ✅ this entry |
| 3.2.2 release notes | ⚠️ TODO |

**Next:** C02 (auto-recalc on launch + before exports), C03a (schema additive fields), C05 (category bleed). These are all P0 for the same 3.2.2 release.

---

*(Older history not maintained — see `git log --first-parent` for the pre-C01 timeline.)*
