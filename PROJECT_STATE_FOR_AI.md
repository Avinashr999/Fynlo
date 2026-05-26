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

**All four Sprint-1 P0 ship-blockers are CLOSED on `master`** (2026-05-26):

- **C01** (Recalculate destroys payments) at `3.2.2` — commits `331c1ae` (Stage 1) + `5a00d4a` (Stage 2). ADR: `decisions/2026-05-26-c01-fix-strategy.md`. `payments` / `debt_payments` are the **single source of truth**; the invariant `paid == SUM(payments)` is structurally enforced — no DAO method lets new code violate it without first re-adding one of the ten queries Stage 2 deleted.
- **C02** (Stale exports / no auto-recalc) at `3.2.3` — auto-recalc on launch (daily-debounced) + auto-recalc before every export + Dashboard "Last updated" subtitle + before/after dialog on manual recalc + timestamps in PDF/XLSX exports.
- **C03a** (Schema integrity — additive) at `3.2.4` — backup format v2 with metadata + SHA-256 hash + `createdAt`/`projectId` on the previously-missed entities + `TransactionValidator` guard against the forbidden type-as-category literal.
- **C05** (Category bleed Income/Expense) at `3.2.5` — typed `Categories.INCOME` / `Categories.EXPENSE` lists wired to the Income/Expense toggle; chip-picker switches and resets selection on toggle flip; `INCOME ∩ EXPENSE = ∅` enforced by 9 unit tests.

**Sprint 1 was scoped to C01 only by the audit's original estimate; we landed all four P0 clusters in a single session.** The CI data-integrity gate now runs **39 tests across 5 classes** on every push.

**Do NOT** (these would re-open C01):

- Re-introduce a direct `paid` / `paidPrincipal` / `paidInterest` writer at the DAO layer. To record a repayment, `insertPayment` (or `insertDebtPayment`) and then call `rebuildBorrowerPaidFromPayments()` (or the debt twin). To reverse one, `deletePayment` + rebuild.
- Add any `@Query("UPDATE borrowers SET paid = ...")` (or debts twin) outside the existing `rebuildBorrowerPaidFromPayments` / `rebuildDebtPaidFromDebtPayments` queries. The entire C01 ADR exists because of one such query.
- Remove or weaken `RecalculateBalancesDataIntegrityTest` or the explicit `--tests '*DataIntegrity*' --tests '*Recalculate*'` gate in `.github/workflows/checks.yml` (those are the CI tripwire that catches a regression).

**Remaining P0 work** for the 3.2.2 release that ships C01's fix:

- **C02** — stale exports & auto-recalc (now safe to enable thanks to C01 closure)
- **C03a** — schema integrity additive fields (`schemaVersion`, `createdAt`, `projectId`, `userId`)
- **C05** — category bleed Income / Expense

**Release strategy (changed 2026-05-26):** no Play Console upload of *any* version until **every** cluster in `UX_AUDIT_2026-05-25.md` (C01–C22 + C03b, including the v4+ C22 backlog) is closed. See `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md` for the full reasoning. The C01-closure work below remains valid as a development milestone — code is on `master`, tests are green, release notes drafted — but the `RELEASE_PROTOCOL.md §4` pipeline is **dormant** and will execute *once*, at the final all-clusters launch, with a freshly-drafted Play Store copy.

What this means in practice for AI agents reading this file:
- Do NOT propose tagging `v3.2.2` or running `:app:bundleProdRelease` for an upload.
- DO continue per-cluster work; the structural-fix discipline from `decisions/2026-05-26-c01-fix-strategy.md` (test-first, structural enforcement, ADR for compound decisions) remains in force for every subsequent cluster.
- The 3.2.2 release notes and `CHANGELOG [3.2.2]` are kept as development-milestone records, not shipping artifacts. Future cluster closures append to the journal (§6) and may bump internal `versionName` markers without triggering an upload.

C01-closure milestone is complete (✅ migration test, ✅ release-notes draft, ✅ smoke test with PDF fixes, ✅ startup macrobench within ±5%, ✅ versionName/versionCode bumped). Next cluster to pick up is C02 (now a 5-line wrapper per the C01 ADR's Consequences section).

## 0.5 Priority discipline (summary)

The audit ranks 22 clusters by priority. Do not work out of order:

- **P0** (4 clusters) — ~~C01 · C02 · C03a · C05~~ ✅ **ALL CLOSED 2026-05-26** — ship-blockers cleared
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
**Version**: 3.2.8 on `master` (`versionName = "3.2.8"`, `versionCode = 131`). C01 closed at 3.2.2 → C02 at 3.2.3 → C03a at 3.2.4 → C05 at 3.2.5 (all four Sprint-1 P0 clusters closed) → C04 at 3.2.6 (first P1 Sprint 2 cluster closed) → 3.2.7 = C04 smoke follow-up + two RecurringScreen surface fixes → **3.2.8 = re-smoke fix for SegmentedButton 'Monthly' clipping**. Internal milestone markers only — per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens until every `UX_AUDIT` cluster (P0 through P3) is closed. Next P1: the C06 / C07 FAB-ownership pair (they share the same Scaffold-vs-screen question so they close as a unit).
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

### 2026-05-27 — 3.2.8 (re-smoke fix: SegmentedButton 'Monthly' clipping)

**Type:** one-line tweak surfaced by re-smoking 3.2.7's frequency-picker fix. The 3.2.7 switch to `SingleChoiceSegmentedButtonRow` improved the visual over the prior cramped chip row, but Material 3's default `SegmentedButton.icon` parameter renders a checkmark on the selected segment (`SegmentedButtonDefaults.Icon(active = selected)`) which consumes ~24dp of each segment's width. With 4 segments inside an AlertDialog, "Monthly" — the longest label — was getting clipped. Fix: pass `icon = {}` to suppress the checkmark. Selection state is still visually communicated by the segment's filled background colour; the checkmark was redundant signalling.

**Internal milestone:** `3.2.8` / `versionCode = 131`. No Play Console upload per release-cadence ADR. No test gate change (pure visual tweak).

**Pattern note for future SegmentedButton use:** in any AlertDialog or narrow-width container, default to `icon = {}` on `SegmentedButton` unless you specifically need the leading icon affordance. Material's default is sized for full-width screen surfaces, not constrained dialog widths.

### 2026-05-27 — 3.2.7 (C04 smoke follow-up + RecurringScreen polish)

**Type:** post-closure point release surfaced by the C04 device smoke. Three discrete fixes — one is a genuine C04 heuristic flaw, two are pre-existing UX bugs that the smoke happened to surface because we were rendering RecurringScreen for the first time in this session.

**Internal milestone:** `3.2.7` / `versionCode = 130`. No Play Console upload per release-cadence ADR.

**Fix 1 — `BudgetSuggestion` excludes non-discretionary categories.**
The 3.2.6 heuristic was structurally correct ("highest-spend uncapped EXPENSE wins") but produced a confusing result for users with lending activity: `FinanceRepository.insertBorrowerWithSource` auto-creates an Expense transaction with `category = "Lending"`, so a user who has lent ₹50k will see "Lending" suggested as the next category to budget. That's the opposite of what a "what should I budget?" answer should say — the money is expected back.

Added `NON_DISCRETIONARY_CATEGORIES: Set<String>` constant in `BudgetSuggestion.kt` containing the five system / auto-generated EXPENSE categories that should never be auto-suggested:
- `"Lending"` — outbound loan
- `"Investment"` — asset purchase (still owned, just transformed)
- `"Interest Expense"` — accrued from interest engine, `journal_only`
- `"Balance Correction"` — `quickEditBalance` internal book-keeping
- `"Bad Debt"` — borrower write-off journal entry

Filter applied in `suggest()` as `cat !in NON_DISCRETIONARY_CATEGORIES`. Users can still pick any of these manually from the AddBudget chip list — the filter is **suggestion-only**, not chip-list exclusion. Tests: +8 cases in `BudgetSuggestionDataIntegrityTest` (12 → 20). One per excluded category to lock down individual exclusion, plus an "all uncapped are non-discretionary → null" case (chained-fallback correctly moves on to recency), plus a "user-budgeted Lending stays excluded as a normal capped category" case, plus a lockdown test on the set's exact membership.

**Fix 2 — `RecurringScreen` header `+` button visibility.**
The header `IconButton` used `tint = Color.White` against `PremiumScreenHeader`'s plain surface background → invisible in light mode. Pre-existing bug; surfaced when the user tried to add a second recurring transaction and couldn't find the button. Fix: `FilledTonalIconButton` (theme-aware secondary container background + properly-tinted icon), no hardcoded `Color.White`. **Same bug pattern exists in `CollectionCalendarScreen:120` for the back arrow** — logged in this entry for follow-up, NOT fixed here (out of scope for the smoke follow-up; will land alongside C06/C07 FAB work).

**Fix 3 — `AddRecurringDialog` frequency picker spacing.**
Four `FilterChip`s with `weight(1f)` + `labelSmall` in a 6dp-spaced `Row` were too cramped at AlertDialog width. Replaced with `SingleChoiceSegmentedButtonRow` — the M3 widget designed for 2-4 mutually-exclusive options. Added a `"Frequency"` section label above for consistency with the existing `"Category"` label.

**Data-integrity gate state:** 71 → **79 tests across 8 classes**, 0 failures (only `BudgetSuggestionDataIntegrityTest` grew).

**Implicitly verified by smoke (user did not report broken):** AddTransactionDialog Stage 2.5 Custom-value re-prefill, RecurringScreen chip picker conversion, RecurringScreen Income/Expense recency split, AddBudget chip-picker conversion, currency picker locale default + "Recently used" sub-group. The fixes in this release address what the smoke explicitly flagged; the rest of C04 is presumed passing.

**Next P1:** C06 (FAB overlap) + C07 (duplicate FAB) — and as part of that, replace `tint = Color.White` in `CollectionCalendarScreen` with the same `FilledTonalIconButton` pattern used here.

### 2026-05-27 — C04 closed in 3.2.6 (Stage 3 + Stage 2.5)

**Cluster:** C04 (P1 Sprint 2, smart defaults). First P1 cluster to close. Stages 1 (data layer, 3.2.5-era) and 2 (`AddTransactionDialog` recency wiring, commit `024cdfb`) shipped previously; this commit closes the cluster with Stage 3 (three remaining picker surfaces) + Stage 2.5 (the Custom-value re-prefill gap in `AddTransactionDialog`).
**Internal milestone:** `3.2.6` / `versionCode = 129`. No Play Console upload per release-cadence ADR.

**Three parallel-agent surfaces closed in Stage 3:**

1. **RecurringScreen.AddRecurringDialog** — free-text category `OutlinedTextField` replaced with the C05-pattern `FlowRow` of `FilterChip`s + trailing "Custom" chip + conditional custom-name input. `LaunchedEffect(isIncome)` uses the same three-case `when` as `AddTransactionDialog` (null → blank both; recent in list → chip; recent not in list → "Custom" + restore text). Submit calls `viewModel.recordRecurringCategory(r.type == "Income", r.category)`. Keyed off `FormIds.ADD_RECURRING` to isolate from one-off AddTransaction. Split by type so Income recurring recency doesn't bleed into Expense. (This also resolves the C05 "Out-of-scope: Recurring still free-text" deferral.)

2. **BudgetScreen.AddBudgetDialog** — chained-fallback prefill in `LaunchedEffect(Unit)`: (a) `viewModel.suggestBudgetCategory()` — the audit's load-bearing **highest-spend-uncapped** heuristic, NOT pure recency; (b) `viewModel.rememberLastBudgetCategory()` fallback when every spent-on category is already budget-capped; (c) blank on fresh install. Category input also converted from `LazyRow` + free-text to the C05 chip pattern.

3. **SettingsScreen currency picker** — `ExposedDropdownMenuBox` extended. Initial selection via `viewModel.rememberLastCurrencyOrLocale(LocalConfiguration.current.locales[0])` — recency wins if any, otherwise `Currency.getInstance(locale).currencyCode`, with "INR" final fallback for locales without a country. Menu renders a conditional "Recently used" sub-header + ≤5 rows + `HorizontalDivider` above the full alphabetical list from `CurrencyUtils.supported`. Hidden when `observeRecentCurrencies()` is empty. Each `DropdownMenuItem` writes to both `UserPreferences.setDefaultCurrency` (canonical pref) AND `viewModel.recordCurrency(code)` (recency).

**Stage 2.5 (folded in):** the C04 Stage 2 `LaunchedEffect(isIncome)` block silently dropped Custom-typed recency values like "Charity". Now a three-case `when`: null → blank both; recent in chip list → chip + clear customCategory; recent NOT in list → `selectedCategory = "Custom"` AND `customCategory = recent`, so the typed text re-renders below the chip row.

**Pure-function extraction:** the budget heuristic lives in `app.fynlo.data.BudgetSuggestion.suggest(cappedCategories, expenseAnalytics)` — no Android, no coroutines, no Hilt. Testing the ViewModel method directly would have required a Room + StateFlow + Hilt harness for a function whose entire contract is "given two collections, pick a string." The pure helper got 12 exhaustive cases instead, in `BudgetSuggestionDataIntegrityTest`. The ViewModel method is a thin delegate.

**Data-integrity gate state:** `*DataIntegrity*` + `*Recalculate*` filter now matches **71 tests across 8 classes** (+12 from `BudgetSuggestionDataIntegrityTest`, +8 from `CurrencyPickerOrderDataIntegrityTest`, the latter covering the flat-dedup display-order helper). Up from 39 / 5 at 3.2.5 → 51 / 6 mid-Stage 2 → 71 / 8 here. All pass.

**No DB migration** in 3.2.6 — `@Database` version unchanged at 17, backup `schemaVersion` unchanged at 2. `RecentlyUsedSnapshot` JSON shape unchanged from Stage 1; only new slots inside it (`add_recurring → category_income/expense`, `add_budget → category_expense`, `settings_currency → currency`). Empty slots default to `[]` so installs that skip directly from a pre-C04 version read the empty snapshot cleanly.

**Execution pattern note (for future cluster closures):** Phase 1 (orchestrator-serial: foundation in ViewModel + Stage 2.5 in TransactionDialog) → Phase 2 (three parallel general-purpose agents on RecurringScreen / BudgetScreen / SettingsScreen, one file each, zero merge risk) → Phase 3 (orchestrator-serial: verification + version + docs + commit). This is the highest-quality way to close a multi-surface cluster — each agent had a single screen to reason about, domain quality on the wiring was higher than one agent doing all three sequentially, and the build-success rate across the three was 3/3 first try.

**Next P1:** C06 (FAB overlap) + C07 (duplicate FAB) — they share the same Scaffold-vs-screen ownership question so they close as a unit.

### 2026-05-26 — C04 Stage 2 landed (AddTransactionDialog category prefill)

**Cluster:** C04 (P1, Sprint 2, smart defaults). Stage 2 of 3: the highest-impact UI site — `AddTransactionDialog` — now reads from and writes to the recency tracker. Stage 3 (Recurring + Budget + currency + recently-used picker group rendering) is the remaining work for cluster closure.
**Internal milestone:** still `3.2.5` (no bump — cluster closure pending Stage 3 + Stage 2.5 custom-value handling).

- `FinanceViewModel` constructor now takes `RecentlyUsedTracker` alongside `FinanceRepository` and `RecalcCoordinator`. Two new methods exposed:
  - `suspend rememberLastTransactionCategory(isIncome: Boolean): String?` — picks the correct `CATEGORY_INCOME` or `CATEGORY_EXPENSE` slot and returns `tracker.last(...)`.
  - `recordTransactionCategory(isIncome: Boolean, category: String)` — fire-and-forget; blank values dropped.
- `AddTransactionDialog` signature gained two optional lambdas with no-op defaults: `rememberLastCategory` (suspend) and `onRecordCategory`. The default no-ops preserve the dialog's preview/test composability — call sites can adopt smart defaults incrementally.
- The C05 `LaunchedEffect(isIncome) { selectedCategory = "" }` now asks the recency layer first: `selectedCategory = recent.takeIf { it in categories } ?: ""`. The submit path records the final category (post-Custom-unwrapping) before invoking `onConfirm`.
- All four `AddTransactionDialog` call sites wired with viewModel-bound lambdas: `Navigation.kt`, `SpendScreen.kt`, `HomeScreenModern.kt`, `HomeScreen.kt`.

**Known gaps for Stage 2.5 (small follow-up):** if the user's most-recently-used category is a Custom-typed value like "Charity" (not in `Categories.INCOME`/`EXPENSE`), it's silently dropped from the prefill. Setting `selectedCategory = "Custom"` AND `customCategory = recent` together would re-prefill the text input below the chip row — minor UX polish, ~10 lines.

**No new tests this commit** — the data layer's contract is already covered by `RecentlyUsedDataIntegrityTest` (12 cases from Stage 1). The Stage 2 wiring is UI plumbing that Compose UI tests would assert; deferring those to when CI runs instrumented tests (INF05). Data-integrity gate count unchanged at **51 across 6 classes**.

**Next:** Stage 3 — Recurring + Budget + currency picker + recently-used-group-at-top-of-picker UX. Or Stage 2.5 first to close the custom-value gap.

### 2026-05-26 — C04 Stage 1 landed (smart-defaults data abstraction)

**Cluster:** C04 (P1, Sprint 2, smart defaults). Stage 1 of 3: the data layer alone, no UI changes. Sprint 2 has formally started — first P1 cluster work after all P0s closed.
**Internal milestone:** still `3.2.5` (version bump deferred to cluster closure per the C03a-Stage-1 pattern).

- NEW `app.fynlo.data.RecentlyUsedTracker` (`@Singleton`, Hilt-injected). API: `record(formId, fieldId, value)`, `last(formId, fieldId)`, `observeTopN(formId, fieldId, n = 5)`. Two-level slot key (`formId/fieldId`) with category split by type (`CATEGORY_INCOME` / `CATEGORY_EXPENSE`) so recency can't leak across the same Income/Expense boundary C05 just enforced.
- NEW `app.fynlo.data.RecentlyUsedLogic` — pure-function helpers (`add` / `last` / `topN` / `keyOf`). Dedup-and-cap semantics: blank values rejected, existing values bumped to top instead of duplicated, slot capped at `MAX_ENTRIES = 5`. Lives in its own object so unit tests cover the contract without Robolectric / DataStore.
- NEW `app.fynlo.data.model.RecentlyUsedSnapshot` / `RecentEntry` — single JSON blob stored under `UserPreferences.RECENTLY_USED`. No new Room table or migration. Fault-tolerant decode: corrupted blob yields empty snapshot, doesn't crash.
- `UserPreferences.recentlyUsed(ctx)` + `editRecentlyUsed(ctx, mutate)` — DataStore reader + atomic editor (Json encode/decode wrapped in `runCatching` for corrupted-blob resilience).
- `RecentlyUsedDataIntegrityTest` adds 12 pure-function cases. JVM data-integrity gate count: **39 → 51** across 6 classes (`RecalculateBalancesDataIntegrityTest` 3 + `AutoRecalcDataIntegrityTest` 8 + `BackupDataIntegrityTest` 10 + `TransactionValidatorDataIntegrityTest` 9 + `CategoriesDataIntegrityTest` 9 + `RecentlyUsedDataIntegrityTest` 12).

**Next:** C04 Stage 2 — wire the tracker into `AddTransactionDialog` (prefill category + account on open, record on submit; visually verify on device). Then Stage 3 applies to Recurring + Budget + currency.

### 2026-05-26 — C05 closed (category bleed eliminated; ALL Sprint-1 P0s now done)

**Cluster:** C05 (P0, category bleed Income/Expense). Final P0 ship-blocker. Sprint 1's audit estimate was C01-only — landing C01 + C02 + C03a + C05 in a single session is a 4× scope expansion against the audit's plan.
**Internal milestone:** `versionName = "3.2.5"`, `versionCode = 128`.

- **NEW `app.fynlo.data.Categories`** — `INCOME` (8 entries: Salary, Freelance, Interest, Dividend, Loan Repayment, Refund, Gift Received, Other Income), `EXPENSE` (13 entries including Food, Fuel, Bills, Rent, Healthcare, Subscriptions, Lending, Investment, Other Expense), `TRANSFER` (1 entry). `forType(type: String): List<String>` is case-insensitive on the three canonical types and falls back to `EXPENSE` for unknown/blank (the conservative default per the kdoc).
- **`TransactionDialog.kt`** — chip list now `remember(isIncome) { (if (isIncome) Categories.INCOME else Categories.EXPENSE) + "Custom" }`. `LaunchedEffect(isIncome) { selectedCategory = "" }` clears the selection every toggle flip. The hacky Food→Salary special-case on the Income button is gone (the LaunchedEffect makes it redundant).
- **`EditTransactionDialog.kt`** — chip list routes through `Categories.forType(transaction.type) + "Custom"`. Edit dialogs never change type, so no toggle reset needed. **Bonus cleanup:** the hardcoded `"Expense"` and `"Balance Correction"` entries are removed from the user-pickable list (the former is C03a's forbidden type-literal, the latter is an internal repository-only category set by `FinanceRepository.quickEditBalance()`).
- **`BudgetScreen.AddBudgetDialog`** — refactored to source `Categories.EXPENSE` instead of an inline literal. No behaviour change (Set Category Limit is expense-only — no bleed possible — but consolidating keeps category vocabularies in sync as the lists evolve).
- **NOT done (out of C05 scope):** `RecurringScreen.kt` uses a free-text `OutlinedTextField` for category, not a chip picker. Converting it is UX work that belongs under C04 (smart defaults) — noted but not in this commit.

**Test:** NEW `CategoriesDataIntegrityTest` (9 cases) covers the core `INCOME ∩ EXPENSE = ∅` invariant + the audit's acceptance scenarios + the conservative fallback for unknown types. JVM data-integrity CI gate count: **30 → 39 across 5 classes**.

**Cluster dashboard post-Sprint-1:**

| Cluster | Priority | Status |
|---|---|---|
| C01 Recalculate destroys payments | P0 | ✅ Closed (3.2.2) |
| C02 Stale exports / no auto-recalc | P0 | ✅ Closed (3.2.3) |
| C03a Schema integrity — additive | P0 | ✅ Closed (3.2.4) |
| C05 Category bleed Income/Expense | P0 | ✅ Closed (3.2.5) |
| C04 Smart defaults | P1 (Sprint 2) | Not started |
| C06–C09 (P1 Sprint 2) | P1 | Not started |
| C12–C15 (P1 Sprint 4) | P1 | Not started |
| C03b Schema breaking migration | P1 (Sprint 6) | Not started |
| C10/C11/C16/C17/C19/C20 polish | P2 | Not started |
| C18 Settings cleanup | P1 (Sprint 3) | Not started |
| C21 PDF/XLSX export quality | P1 (Sprint 5) | Two touches landed (C03a + smoke fixes); cluster proper not started |
| C22 v4+ backlog | P3 | Per release-cadence ADR: required for first public release |

**Sprint 2's natural starting point:** C04 (smart defaults — RecentlyUsedTracker, per-form per-field memory, picker dropdowns re-ordered recently-used first). Independent of any P0 work; mostly UI-state + DataStore plumbing.

### 2026-05-26 — C03a closed (both stages)

**Cluster:** C03a (P0, schema integrity — additive). Fully closed alongside C01 and C02.
**Internal milestone:** `versionName = "3.2.4"`, `versionCode = 127`. P0 progress now **3 of 4 closed** (C01, C02, C03a). C05 is the one remaining P0.

**Stage 2 summary (this section; Stage 1 lives below):**

- `MIGRATION_16_17` lands the four missed `createdAt` columns (`flow_templates`, `investment_valuations`, `net_worth_snapshots`, `recurring_transactions`) and the missing `projectId` on `investment_valuations`. Backfills are zone-aware: from `updatedAt` for the three entities that have it, from `strftime('%s', date) * 1000` for `net_worth_snapshots` (which lacks `updatedAt`). The `InvestmentValuation` `projectId` is backfilled from the parent `Investment`'s `projectId` (orphans fall back to `"personal"`).
- The four entity classes updated to add their new fields. Schema export `app/schemas/.../17.json` committed (37,661 bytes — larger than v16 by the five new columns).
- `AppModule` registers `MIGRATION_16_17` in the migration list.
- NEW `app.fynlo.data.TransactionValidator` (pure `object`) — `sanitizeCategory(String): String` rewrites the three forbidden literal categories (`"Expense"` / `"Income"` / `"Transfer"`) to `"Uncategorized"`. Applied at every Transaction write site: `FinanceRepository.insertTransaction` and the new-side of `editTransaction`. Matching is case-sensitive and exact-string only — substring matches like `"Income Tax"` and `"Expense Reimbursement"` pass through unchanged. A one-shot UPDATE inside `MIGRATION_16_17` fixes any existing rows from the old dropdown bug.
- `TransactionValidatorDataIntegrityTest` adds 9 pure-function cases. JVM data-integrity gate count: **21 → 30** (3 C01 + 8 C02 + 10 C03a-Stage-1 + 9 C03a-Stage-2).
- `FynloDatabaseMigrationTest` extends with 3 new instrumented cases for v16 → v17 (createdAt backfill, projectId backfill from parent, forbidden-literal-category rewrite). Plus the existing `migrate15toCurrent_resultingDatabaseOpensCleanlyWithFullRoom` test updated to chain through `MIGRATION_16_17` too. All 8 instrumented cases pass on CPH2767 / Android 16.

**Status post-C03a:**

| Cluster | Priority | Status |
|---|---|---|
| C01 Recalculate destroys payments | P0 | ✅ Closed |
| C02 Stale exports / no auto-recalc | P0 | ✅ Closed |
| C03a Schema integrity — additive | P0 | ✅ Closed |
| C05 Category bleed Income/Expense | P0 | ⚠️ Open — the last P0 |
| C03b Schema integrity — breaking | P1 (Sprint 3) | Not started |
| C04 / C06–C22 | P1/P2/P3 | Not started |

**Next cluster to pick up:** C05 (category bleed Income/Expense) — the final P0. The audit description is short: P0, low effort. After C05 closes, all four Sprint-1-scope P0 clusters are done in a single session.

### 2026-05-26 — C03a Stage 1 landed (backup format hardening)

**Cluster:** C03a (P0, schema integrity — additive). Stage 1 of 2: items #1 + #4 from the audit's five-step Stage 3a fix list. Items #2 + #3 (`createdAt`/`projectId` audit on 5 entities + `MIGRATION_16_17`) and item #5 ("Expense" category rejection) land in Stage 2.

**Internal milestone:** still `3.2.3` — version bump deferred until C03a fully closes (matches the C02 pattern: bump once at cluster closure, not per stage).

- `BackupData` extended with 6 metadata fields at the front: `schemaVersion: Int = 1`, `appVersion`, `exportedAt`, `userId`, `deviceName`, `contentHash` — all default to safe legacy values so 3.2.0-era JSON backups (which lack these fields) still decode and restore.
- NEW `app.fynlo.data.BackupIntegrity` (pure-function `object`) — `computeHash(BackupData): String` produces a SHA-256 (64-char lower-case hex) over the canonical JSON form of the input with `contentHash = ""`. `check(BackupData): Check` is a sealed-class outcome: `Ok` / `UnsupportedVersion(version)` / `HashMismatch`. Version-pinning happens via `CURRENT_SCHEMA_VERSION = 2`.
- `FinanceRepository.getAllDataAsJson()` now populates the metadata + the hash (using `BuildConfig.VERSION_NAME`, `Instant.now()`, `android.os.Build.MODEL`) and takes a `userId: String = ""` parameter that callers can thread through if they have access to the Firebase auth UID.
- `FinanceRepository.restoreDataFromJson()` calls `BackupIntegrity.check()` *before* opening `db.withTransaction` — so a corrupted/tampered/future-version backup never reaches the DB. Throws `IllegalStateException` with a user-readable message for both rejection paths.
- `BackupDataIntegrityTest` adds 10 pure-function cases (no Robolectric, no Room). Data-integrity CI gate count: **11 → 21** (3 C01 + 8 C02 + 10 C03a).

**Audit data for Stage 2:** 4 entities still lack `createdAt` (`FlowTemplate`, `InvestmentValuation`, `NetWorthSnapshot`, `RecurringTransaction`), 1 entity still lacks `projectId` (`InvestmentValuation`). Plus the "Expense" category rejection. `MIGRATION_16_17` will add 5 columns total, all with safe defaults.

### 2026-05-26 — C02 closed (both stages)

**Cluster:** C02 (P0 ship-blocker, stale exports / no auto-recalc) — now ✅ closed alongside C01.
**Internal milestone:** `versionName = "3.2.3"`, `versionCode = 126` (not uploaded — see `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`).

**Stage 2 (UI/UX layer) summary:**
- **Dashboard "Last updated" subtitle** lives under the hero net-worth number on `HomeScreenModern`. Reads `UserPreferences.lastRecalcAt(...)` reactively; renders via `DateUtils.getRelativeTimeSpanString`.
- **Before/after `AlertDialog`** on manual Recalculate. `FinanceViewModel.recalculateAllBalancesCapturingDelta()` returns a `RecalcDelta` (top-level data class at the bottom of `FinanceViewModel.kt`) with signed change fields and an `isNoOp` predicate; the dialog shows pre→post + deltas, or a "no changes" message when `isNoOp` (the common post-C01 case).
- **Export timestamp** in PDF and XLSX. `ExportUtility.generatePDF` adds a "Recalculated: \<date\>" line under the existing "Generated:" header. `ExcelExportUtility.generateFullBackup` prepends a `Metadata` sheet with `Generated` / `Recalculated at` rows. `RecalcCoordinator.runAndStamp()` now returns the stamped time so callers don't need a separate DataStore read.

All 5 steps of the audit's §C02 fix list are now landed. Status post-C02:

| # | Step | Status |
|---|---|---|
| 1 | Auto-recalc on launch (daily debounce) | ✅ |
| 2 | Auto-recalc before every export | ✅ |
| 3 | "Last updated X ago" subtitle on Dashboard | ✅ |
| 4 | Before/after dialog on manual recalc | ✅ |
| 5 | Timestamp embedded in PDF/XLSX exports | ✅ |

**Next cluster to pick up:** C03a (schema integrity — additive fields: `schemaVersion`, `createdAt`, `projectId`, `userId` at backup root). Independent of C01/C02 work; mechanical migration. Or C05 (category bleed Income/Expense) — also independent.

### 2026-05-26 — C02 Stage 1 landed (auto-recalc data path)

**Cluster:** C02 (P0 ship-blocker, stale exports / no auto-recalc).
**Stage 1 of 2:** items #1 (auto-recalc on launch, daily-debounced) and #2 (auto-recalc before every export) from the audit's 5-step fix list. Stage 2 (Dashboard "Last updated" subtitle, before/after dialog on manual recalc, timestamp embedded in exports) is the UI layer and lands in a separate PR.

**Commits:** *(to be filled in once landed)*

- New `app.fynlo.data.RecalcCoordinator` (`@Singleton`) wraps `FinanceRepository.recalculateAllBalances()` and stamps `UserPreferences.lastRecalcAt` after every successful run. The companion exposes a pure `shouldRecalcOnLaunch(lastRecalcAt, now, zone)` predicate — calendar-day boundary in the user's local zone, NOT a 24h sliding window.
- `FynloApplication.onCreate` post-init coroutine calls `recalcCoordinator.runIfStaleOnLaunch()` — fire-and-forget; failure is non-fatal and logged.
- `FinanceViewModel`'s four export methods (`exportAllData` JSON, `exportToCSV`, `exportToPDF`, new `exportToXLSX`) all route through `recalcCoordinator.runAndStamp()` before generating output. XLSX was previously skipped because the launcher invoked `ExcelExportUtility.generateFullBackup` directly — now routed through the ViewModel for one consistent recalc-then-export contract across all four formats.
- `AutoRecalcDataIntegrityTest` (8 cases) covers the debounce predicate's edge cases without Robolectric (pure-function test). Picked up by the `checks.yml` data-integrity CI gate.

**Net effect:** opening the app once per day silently keeps `paid` / `paidPrincipal` / `paidInterest` in sync with the `payments` table (defensive — post-C01, recalc is idempotent so this is mostly a no-op, but it also stamps `lastRecalcAt` which the UI will consume in Stage 2). Exporting any format from Settings now guarantees the exported numbers match what's about to land on disk, regardless of how stale the in-memory state had drifted.

**Status against the audit's 5-step C02 fix:**

| # | Step | Stage 1 | Stage 2 |
|---|---|---|---|
| 1 | Auto-recalc on launch (daily debounce) | ✅ | — |
| 2 | Auto-recalc before every export | ✅ | — |
| 3 | "Last updated X ago" subtitle on Dashboard | — | ⏳ |
| 4 | Before/after dialog on manual recalc | — | ⏳ |
| 5 | Timestamp embedded in PDF/XLSX exports | — | ⏳ |

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
