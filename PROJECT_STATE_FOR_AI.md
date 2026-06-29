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
- `docs/release-handoff/2026-06-20-internal-testing-knowledge-hub.md` - mandatory handoff for the 3.2.105 internal testing release, Play Console setup, reset/sync fix, ledger traceability, and template revamp context

## 0.2 Required reading order

At the start of every session, before producing any code or design suggestion:

1. Read this file (`PROJECT_STATE_FOR_AI.md`) — sections 0, 1, and the latest journal entries
2. Read `AI_AGENT_PROTOCOL.md` in full
3. Read `DESIGN_SYSTEM.md` — §1 (archetypes), §11 (color semantics), §16 (anti-patterns) at minimum
4. Read `UX_AUDIT_2026-05-25.md` — §2 (clusters), §3 (priority), §5 (ship-blocking matrix), §8 (the critical paragraph)
5. For any release, Play Console, Firestore sync, ledger traceability, PDF/export, app icon, screenshot, or template-revamp work, read docs/release-handoff/2026-06-20-internal-testing-knowledge-hub.md
6. Read the specific source files you're about to modify

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

**Current release-gate state (updated 2026-05-28):**

- **All P0/P1/P2/P3 UX audit clusters are closed** in the current development line.
- **C03b is closed** in `3.2.91` after the staged account-id, recurring-id, peopleId, and UUID standardisation work.
- **Next step:** release prep, not more cluster work. Follow `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md` and `RELEASE_PROTOCOL.md` before any Play Console upload.

**Release strategy (changed 2026-05-26):** no Play Console upload of *any* version until **every** cluster in `UX_AUDIT_2026-05-25.md` (C01–C22 + C03b, including the v4+ C22 backlog) is closed. See `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md` for the full reasoning. The C01-closure work below remains valid as a development milestone — code is on `master`, tests are green, release notes drafted — but the `RELEASE_PROTOCOL.md §4` pipeline is **dormant** and will execute *once*, at the final all-clusters launch, with a freshly-drafted Play Store copy.

What this means in practice for AI agents reading this file:
- Do NOT propose tagging `v3.2.2` or running `:app:bundleProdRelease` for an upload.
- DO continue per-cluster work; the structural-fix discipline from `decisions/2026-05-26-c01-fix-strategy.md` (test-first, structural enforcement, ADR for compound decisions) remains in force for every subsequent cluster.
- The 3.2.2 release notes and `CHANGELOG [3.2.2]` are kept as development-milestone records, not shipping artifacts. Future cluster closures append to the journal (§6) and may bump internal `versionName` markers without triggering an upload.

C01-closure milestone is complete (✅ migration test, ✅ release-notes draft, ✅ smoke test with PDF fixes, ✅ startup macrobench within ±5%, ✅ versionName/versionCode bumped). As of `3.2.91`, the cluster backlog is closed; the next work is release prep and final verification.

## 0.5 Priority discipline (summary)

The audit ranks 22 clusters by priority. Do not work out of order:

- **P0** (4 clusters) — ~~C01 · C02 · C03a · C05~~ ✅ **ALL CLOSED 2026-05-26** — ship-blockers cleared
- **P1** (12 clusters) — ✅ closed
- **P2** (6 clusters) — ✅ closed
- **P3** (1 cluster) — ✅ C22 closed for release-gate purposes; deferred items tracked separately

If future work reopens a higher-priority data-integrity issue, handle it before lower-priority polish or post-launch enhancements.

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
**Version**: 3.2.91 on `master` (`versionName = "3.2.91"`, `versionCode = 214`, Room schema version 25). All UX audit clusters are closed for the current development line: P0, P1, P2, C22 P3, and C03b. The latest milestone is **3.2.91 = C03b Stage #4 = Ids helper + UUID standardisation**, which closes C03b structurally after the staged account-id, recurring-id, peopleId, and UUID work. Internal milestone markers only; per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens until the release-prep checklist is completed. Deferred/non-blocking follow-ups remain tracked separately: Stage #1b-3 search/edit/export cosmetics, Stage #3b Person-level UI aggregation, MF SIP taxonomy cleanup, voice search, daily/weekly budget limits, proper logo design, AI categorization, receipt OCR, NAV/stock auto-import, and multi-project scoping polish.
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

### 2026-05-28 - 3.2.91 (C03b Stage #4 - Ids helper + UUID standardisation; closes C03b)

**Type:** C03b structural closure. Latest source of truth is `CHANGELOG.md` `[3.2.91]`.

**Internal milestone:** `3.2.91` / `versionCode = 214`. Room schema remains at v25. No Play Console upload yet; next step is release prep per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`.

**What changed:** `logic/Ids.kt` is now the central new-entity ID generator. Kotlin insert paths route new database entity IDs through UUID v4 generation. Existing legacy IDs remain valid and are not rewritten. Documented exceptions remain bounded: model-level defaults that cannot depend on `logic/`, one-time SQLite migration IDs, display-only bug report IDs, and the `"personal"` Project sentinel.

**State after this milestone:** All UX audit clusters are closed for the current development line: P0, P1, P2, C22 P3, and C03b. Deferred/non-blocking follow-ups remain tracked separately: search/edit/export cosmetic account-name surfaces, Person-level UI aggregation, MF SIP taxonomy cleanup, voice search, daily/weekly budget limits, proper logo design, AI categorization, receipt OCR, NAV/stock auto-import, and multi-project scoping polish.

**Verification recorded in changelog:** `IdsDataIntegrityTest` added; changelog records 296 JVM tests across 26 classes passing for the milestone. Later local verification in this workspace may require normal home-directory access for Robolectric lock/cache files.

### 2026-05-27 — 3.2.47 (C22 Stage 2 — Recurring last-day + preview + Goals deadline)

**Type:** C22 P3 backlog second stage. Audit items #218, #220, #207. End-date #219 deferred to its own stage (needs Room migration v17→v18).

**Internal milestone:** `3.2.47` / `versionCode = 170`. No Play Console upload. No test gate change (UI + 1 worker logic clamp; no schema impact).

**Changes:**
- `RecurringWorker.shouldRunToday` — clamps target dayOfMonth to `today.lengthOfMonth()` so day-31 fires on Feb 28 / Apr 30 / etc. Days 1-27 unchanged.
- `RecurringScreen.AddRecurringDialog`:
  - "Use last day of month" checkbox (sets value to 31; worker handles the clamp).
  - dayOfMonth range widened 1-28 → 1-31 with as-you-type validation.
  - "Next occurrences" caption below the input — shows next 3 firing dates given (frequency, dayOfMonth). Pure UI calc; rebuilds on input change.
- `GoalScreen.AddGoalDialog` — exposes the `Goal.deadline` field that was already in the model but never UI-bound. Optional yyyy-MM-dd input.
- `GoalCard` — renders "Target by <date>" below the progress bar when deadline is set.

**Pattern: schema-free P3 polish.** Two of these (#220 preview, #207 deadline-expose) needed zero data changes. #218 needed only a worker logic tweak (one line). #219 (end-date) is the only schema-touching one; isolated to a separate stage.

### 2026-05-27 — 3.2.45 (C22 Stage 1 — About-screen Resources section + new LICENSES.md)

**Type:** C22 P3 backlog first stage. Audit C22 items #254 Privacy Policy, #256 Open Source Licenses, #257 Changelog land here. #258 proper-logo deferred (no drawable asset to ship).

**Internal milestone:** `3.2.45` / `versionCode = 168`. No Play Console upload per release-cadence ADR. No test gate change (UI-only).

**New file:** `LICENSES.md` at repo root. Static list of major OSS deps with their licenses (Apache 2.0 / EPL 1.0 / MIT) + canonical text URLs. Categorized by area (Kotlin/Coroutines, Android Jetpack, DI, Firebase, Test infrastructure, etc.).

**`AboutScreen` Resources section:**
- Three clickable rows under the Legal Disclaimer card. Each opens a GitHub URL via `ACTION_VIEW`:
  - Privacy Policy → PRIVACY_POLICY.md (file already existed; just unlinked from UI).
  - Open Source Licenses → LICENSES.md (newly created).
  - Changelog → CHANGELOG.md.
- New `AboutLinkRow(icon, label, onClick)` private composable. Mirrors SettingsActionRow shape: leading icon + label + trailing OpenInNew chevron. Whole row tappable; `runCatching` so absent browser no-ops.

**C22 audit items remaining:** import contacts from phonebook, Savings Goals target-date / icon picker, Recurring end-date / last-day-of-month / preview, Projects clone / description / icon, Budgeting auto-suggest / alert thresholds, Search recent / filter chips / fuzzy / voice, Encrypted backups, Receipt OCR, Snowball/avalanche debt payoff, Extra-payment scenarios, Dark mode, AI categorization, Bank statement import, NAV/stock auto-import, Multi-project scoping (needs C03b first), proper logo asset.

**Pattern: open the P3 cluster with a small contained change** before tackling the larger items (OCR, AI, bank parse). Builds momentum + verifies the staging cadence works for the v4 backlog.

### 2026-05-27 — 3.2.44 (C20 closed — drawer cleanup; **closes P2 backlog**)

**Type:** C20 closure. **Sixth and final P2 cluster down. P2 backlog closed.** Audit §C20 fixes #181–#186.

**Internal milestone:** `3.2.44` / `versionCode = 167`. No Play Console upload per release-cadence ADR. No test gate change (UI-only restructure of the drawer).

**Drawer changes:**
- Compact header — 40dp Person-icon avatar + signed-in user name + email in one Row. Replaced the prior 52dp icon + Fynlo headline + "Personal Finance Manager" tagline (~120dp / ~25% of drawer vertical pre-C20). User identity from `AuthManager.userName / userEmail`; falls back to "Signed in" / "Tap Profile to sign in" for anonymous. Photo-avatar via `userPhoto` deferred — needs Coil.
- Flat list ordered by frequency, one divider. Removed "ACCOUNT / FINANCE TOOLS / APP" section labels. Top 5 (primary emerald via new `accent = true` on DrawerItem): Settings, Profile & Security, Budgeting, Savings Goals, Contact Book. Bottom 4 (grey): Recurring Transactions, Manage Projects, EMI Calculator, About & Disclaimer. Logout stays at bottom in red.
- Removed duplicate "Investments" drawer entry — same route as bottom-nav "Invest" tab.

**`DrawerItem` API addition:** `accent: Boolean = false` param. When true (and not selected), tints the icon primary emerald. Param order: `(icon, label, selected, accent, onClick)` so the trailing-lambda still binds to `onClick`.

**Lessons logged:**
- Trailing-lambda binding: new boolean params must go BEFORE the lambda param.
- Package-path shadowing: when a Composable scope has `val app = ...`, can't fully-qualify with `app.fynlo.*` from that scope. Add a direct import.

**P2 backlog closed.** Six clusters: C10, C11, C16, C17, C19, C20. Combined with the 4 P0 + 11 P1 clusters, that's **21 of 22 UX_AUDIT clusters now closed**. Only **C22 (P3 v4+ backlog)** remains for the release-cadence ADR's "all clusters closed before Play Console upload" condition.

### 2026-05-27 — 3.2.43 (C19 closed — empty-state standardization across the 3 remaining outliers)

**Type:** C19 closure. Fifth P2 cluster down. Audit fixes for the 3 remaining outliers (most surfaces already used the shared theme.EmptyState per C07's sweep).

**Internal milestone:** `3.2.43` / `versionCode = 166`. No Play Console upload per release-cadence ADR. No test gate change (UI-only).

**Three surfaces fixed:**
- **PeopleScreen** — bespoke `EmptyPeopleState` migrated to the shared `theme.EmptyState` composable. Removed redundant sub-header text that duplicated the empty-state body copy (audit "Contact Book: redundant double-explanation").
- **MoneyFlowScreen filtered-empty** — promoted "No flows found" plain text to a 48dp SwapHoriz icon + filter-aware body ("No flows yet" vs "No [tab] flows" depending on active filter).
- **InvestmentScreen Valuation History dialog** — got a 40dp History icon + 2-line body instead of plain "No records found" text. Compact form rather than the shared composable since AlertDialog body is space-constrained.

**Verified already-correct:**
- Hand Loans empty (audit #33) — no longer applies since C12 replaced the TabRow with segmented filter; per-segment empty messages are filter-aware in context.
- Recurring + Budgeting + Goals — all already use the shared composable from C07's sweep.

**Pattern: most empty states had been standardized; this commit just cleaned the 3 outliers the audit specifically called out.** The work is more "surgical cleanup" than "broad sweep." Recurring empty surfaces are uniform across the app now.

**P2 remaining: C20 (Drawer cleanup) — last P2 cluster.**

### 2026-05-27 — 3.2.42 (C17 closed — DisabledButtonHint(reason) composable + 9-site sweep)

**Type:** C17 closure. Fourth P2 cluster down. Audit fixes #18, #169, #203, #210, #232, #233.

**Internal milestone:** `3.2.42` / `versionCode = 165`. No Play Console upload per release-cadence ADR. No test gate change (UI-only addition).

**New: `DisabledButtonHint(reason: String?)`** — 11sp `onSurfaceVariant` label below the button when reason is non-null; reserved 14dp spacer when null so the column doesn't shift up as the user completes the form.

**Nine sites swept:**
- TransactionDialog — amount + category + Custom-category-text reasons.
- LendingDialog — pick-borrower + loan-amount reasons.
- DebtDialog — lender-name + amount reasons.
- InvestmentDialog — refactored `canSave` into deterministic `disabledReason: String?` branching on sourceType (account/existing-debt/new-loan).
- PaymentDialog (CollectPayment + PayDebt) — totalAmount > 0 reason.
- BudgetScreen AddBudget — category + positive-limit reasons.
- PeopleScreen, ProjectsScreen, RecurringScreen — name-blank reasons.

**Pattern: AlertDialog vs raw Dialog.** AlertDialog's `confirmButton` slot expects exactly one button composable — can't fit a hint underneath. For those forms the hint goes inside the `text` slot as the last item. For raw Dialog forms (Transaction, Lending, etc.) the hint sits directly under the button. Same composable, slightly different placement per dialog type.

**P2 remaining:** C19 (Empty states), C20 (Drawer cleanup).

### 2026-05-27 — 3.2.41 (C16 closed — color semantics: Outstanding emerald on Lent, project active-indicator radio)

**Type:** C16 closure. Third P2 cluster down. Audit fixes #1 and #5 land; #2, #3, #4 verified already-correct; #6 skipped with rationale.

**Internal milestone:** `3.2.41` / `versionCode = 164`. No Play Console upload per release-cadence ADR. No test gate change (pure colour-token change; no logic touched).

**Changes:**
- `LendingScreen.LendingCard` Outstanding column: `if (isOverdue) SemanticRed else MaterialTheme.colorScheme.onSurface` → `if (isOverdue) SemanticRed else Emerald500`. Normal-state Outstanding now signals receivable-as-asset.
- `CustomerDetailScreen` hero Current Balance: was red on positive outstanding (read as debt). Flipped to green on positive (asset still on books) and neutral on zero (no celebration colour for "loan repaid — just done").
- `ProjectsScreen` active project indicator: `Icons.Default.CheckCircle` (green check, read as income confirmation) → `Icons.Filled.RadioButtonChecked` (same tint, unambiguous selection-state shape).

**Verified already-correct:**
- Negative investment growth: InvestmentScreen renders growth with sign-aware coloring everywhere it's displayed.
- Restore button: CustomerDetailScreen Mark NPA/Restore toggle already uses Emerald on restore + Amber on warn. SettingsScreen "Restore Real Data" confirm uses theme primary.
- Load Test Data: row icon is Amber, confirm dialog is Red because the action is destructive (same pattern as Cleanup Seeder + Wipe ALL).

**Skipped:**
- Wallet/budget mixed icons (audit #6) — current Wallet usages are account-type identifiers, not semantic state. No clear violation found.

**Pattern: targeted token swaps, not a sweeping audit.** Most of the codebase was already correct; the audit's complaint was concentrated in 3 specific surfaces. Made the changes, verified the rest, documented what was skipped and why.

**P2 remaining:** C17 (Disabled button hints), C19 (Empty states), C20 (Drawer cleanup).

### 2026-05-27 — 3.2.40 (C11 closed — DateUtils.format(date, Style) API + dateFormat pref threaded into exports)

**Type:** C11 closure. Second P2 cluster down. Audit §C11 #302, #307, #308, #309, #312, #325, #359, #360.

**Internal milestone:** `3.2.40` / `versionCode = 163`. No Play Console upload per release-cadence ADR. **Data-integrity gate +15 tests** (`DateUtilsDataIntegrityTest`) → 137 tests / 12 classes / 0 failures.

**`DateUtils.format(date, Style)` API added:**
- `Style.Relative` — "today" / "yesterday" / "in N days" / "N weeks ago" / falls back to Compact at > 8 weeks. Uses C10's shared `pluralize` for day/week counts.
- `Style.Compact` — user's chosen pattern (dd-MM-yyyy / MM-dd-yyyy / yyyy-MM-dd). Caller passes `compactPattern` explicitly; default falls back to `dd-MM-yyyy`.
- `Style.Definitive` — "25 May 2026" (locale-agnostic month name).
- `Style.ChartAxis` — "May 25" (for chart x-axis).
- Defensive fallbacks: malformed input returns verbatim, malformed pattern uses default.
- Formatter cache to avoid rebuilding `DateTimeFormatter` per call.

**Back-compat preserved:** `formatToDisplay` + `parseInput` still work. `formatToDisplay` delegates to `format(dateStr, Style.Compact)` with default. Existing in-app call sites compile and behave identically.

**Threading user dateFormat through exports:**
- ExportUtility (3 PDF generators) + ExcelExportUtility.generateFullBackup gained `dateFormat: String` param.
- PDF date cells reformat via `DateUtils.format(date, Compact, dateFormat)`.
- XLSX date cells reformat via new `dt(iso)` helper (named `dt` not `d` to avoid shadowing `debts.forEach { d -> }` lambda parameter).
- FinanceViewModel.exportToPDF / exportToXLSX gained the param. SettingsScreen passes the pref it already collects. Per-screen Export buttons (ReportsHubScreen, ProfitLossScreen, CustomerDetailScreen, MoneyFlowScreen) collect `UserPreferences.dateFormat(...)` and thread.

**XLSX label fix:** Lending sheet `"Loan Date"` → `"Lent On"` — same field, same name everywhere.

**Deferred follow-up: in-app rendering still uses default `dd-MM-yyyy`.** Making in-app Composable sites respect the user pref needs either a `LocalDateFormatPattern` CompositionLocal initialized at the root + `rememberFormatDate(dateStr)` helper, OR a static cache loaded at app init. Either is mechanical but touches every Composable that renders a date. The bulk of audit C11 (exports + formal API + label) closes here.

**Pattern: same shape as C08 + C10.** Define helper once, sweep call sites, add a data-integrity test class to pin the contract. Defaults stay tolerant so existing callers don't break.

**P2 remaining:** C16 (Color semantics), C17 (Disabled button hints), C19 (Empty states), C20 (Drawer cleanup).

### 2026-05-27 — 3.2.39 (C10 closed — shared Pluralize helper + 13-site sweep, first P2 cluster down)

**Type:** C10 closure. First P2 cluster down. Audit §C10 fixes #117, #138, #152 land here.

**Internal milestone:** `3.2.39` / `versionCode = 162`. No Play Console upload per release-cadence ADR. **Data-integrity gate +8 tests** (`PluralizeDataIntegrityTest`) → 122 tests / 11 classes / 0 failures.

**`app.fynlo.logic.Pluralize` added:**
- `object Pluralize` with `pluralize(count, singular, plural?)` + `pluralNoun(count, singular, plural?)`. Default plural is `singular + "s"`; irregular plurals via explicit second arg.
- Top-level shorthand functions for ergonomic call sites.
- Int AND Long overloads. Long is the common case for `ChronoUnit.DAYS.between(...)` day-counts.

**13 call sites swept:**
- DebtPayoffScreen (the literal `(s)` bug), AccountStatementScreen, GlobalSearchScreen, InvestmentScreen (×2), SpendScreen, TransactionHistoryScreen (×2), ExportUtility PDF section header, CollectionCalendarScreen (×2 Long), CustomerDetailScreen (×3 — WhatsApp templates + payment count), DebtDetailScreen, PinScreen, ReminderWorker (×4 background notifications).
- Plus NetWorthHistoryScreen's screen-local pluralCount helper (from C15c 3.2.31) deleted; migrated to the shared helper.

**`PluralizeDataIntegrityTest` — 8 cases:**
- Singular at count == 1; plural at 0, 2+, negative; default plural; explicit irregular; pluralNoun variant; zero-is-plural English convention.

**Localization follow-up deferred.** Audit recommended Android `<plurals>` resources but the app's strings live in Compose `Text(...)` not `string.xml`, so a `pluralStringResource(...)` migration would be the same call-site churn anyway. Revisit when localization becomes a real concern. The shared Kotlin helper is structured so that future migration is mechanical.

**Pattern: one helper, many call sites.** Same shape as C08 (CurrencyFormatter) and C09 (UTF-8 mojibake guard) — define the helper once, sweep every site, add a data-integrity test class to pin the contract.

**P2 remaining:** C11 (Date formatting), C16 (Color semantics), C17 (Disabled button hints), C19 (Empty states), C20 (Drawer cleanup).

### 2026-05-27 — 3.2.38 (C21 Stage 4 of 4 — closes C21 + closes P1 backlog: XLSX overhaul)

**Type:** Stage 4 of 4 for C21. **Closes C21 cluster in full. Closes the entire P1 backlog.** Audit §C21 fixes #12, #13, #14, #15, #16 land here. Eleventh P1 Sprint-2 cluster closed.

**Internal milestone:** `3.2.38` / `versionCode = 161`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — render-layer additions; SUM totals pre-computed at write time so file values are correct without depending on Excel's formula evaluator).

**ExcelExportUtility additions:**
- `Cell.Currency(value)` — currency-formatted amount cell. Uses numFmt `[$<sym>-409]#,##0.00;[Red]-[$<sym>-409]#,##0.00` where `<sym>` is `CurrencyUtils.symbolFor(currencyCode)`. The `;[Red]-` half handles negative-red natively. All amount columns migrated from `Cell.Number` → `Cell.Currency`.
- `Sheet.freezeHeader = true` (default) → `<sheetView><pane ySplit="1" .../></sheetView>` before sheetData. Header stays visible while scrolling.
- `Sheet.autoFilterCols: Int = 0` → `<autoFilter ref="A1:<col><lastRow>"/>` after sheetData. Filter dropdowns on header. Range excludes the totals row.
- `Sheet.totalsCols: List<Int> = emptyList()` → totals row after body. Leftmost cell "Total" label (bold style 4). Each totalsCols column gets a bold+currency SUM formula (style 5) with pre-computed `<v>` value alongside `<f>SUM(...)`.
- New "Summary" sheet as the first sheet — 10 KPI rows matching the PDF cover (Net Worth, Total Assets, Total Liabilities, Cash, Investments, Invest Growth, Monthly Income, Monthly Expense, Net Cash Flow, Total Lent Out). Same cash-basis + financing-categories exclusion as P&L.
- Metadata sheet gained a Currency row.

**`generateFullBackup` signature added:**
- `summary: FinancialSummary = FinancialSummary()` — feeds the Summary sheet KPIs.
- `currencyCode: String = "INR"` — drives the currency numFmt symbol + Metadata row.

**`FinanceViewModel.exportToXLSX` threads both new params** from `financialSummary.value` + `currentProject.value?.currency ?: "INR"`.

**Totals rows applied to:**
- Accounts (Balance)
- Lending (Principal + Paid)
- Debts (Principal + Paid)
- Investments (Invested + Current Value + Growth)

Transactions / Loan Repayments / Debt Repayments don't get totals — they're event log sheets, summing dates or per-row notes wouldn't be meaningful. Their amount columns still display as currency.

**Audit fix #13 "Mohan Rao-style overdue red row" deferred** — the native `[Red]` negative-format covers 95% of the conditional-formatting intent ("negative growth (red)" + any negative amount in any column). Full per-row conditional fill on overdue Status would require per-row style overrides + a 4th fill in styles.xml; nice-to-have but more invasive XML. Logged as a deferred follow-up.

**Audit fix #18 (PDF metadata)** accepted as Android `PdfDocument` framework limitation (Title/Author/Subject setters not exposed by the public API). Documented in Stage 1.

**P1 backlog now closed.** Eleven Sprint-2 clusters: C04, C06+C07, C08, C09, C18, C12, C13, C14, C15, C21. Remaining cluster work is P2 (C10, C11, C16, C17, C19, C20), C22 v4+ (P3), C03b breaking schema migration, infrastructure backlog INF01-INF06, plus deferred Task #24/#26/#27/#28. Per the release-cadence ADR no Play Console upload happens until all of that is closed.

### 2026-05-27 — 3.2.37 (C21 Stage 3 of 4: PDF charts + 5 new KPI cards)

**Type:** Stage 3 of 4 for C21. Audit §C21 fixes #10 (3 charts) and #11 (5 new KPIs) land here.

**Internal milestone:** `3.2.37` / `versionCode = 160`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — pure render code + reads from already-validated paths).

**ExportUtility added 5 new KPI cards:**
- Row 1 (5 cards across, balance sheet view): NET WORTH | TOTAL ASSETS | TOTAL LIABILITIES | TOTAL CASH | INVEST GROWTH
- Row 2 (4 cards across, activity view): MONTHLY INCOME | MONTHLY EXPENSE | NET CASH FLOW | TOTAL LENT OUT
- Monthly income/expense use calendar-month + financing-categories exclusion (matches P&L Statement so debt receipts don't inflate income).
- Total Lent Out = lifetime principal (audit #4's fix from C15b applied here too).

**ExportUtility added 3 chart panels:**
- `drawAssetAllocationDonut` — Cash + Investments + Receivables slices. Donut hole at 55% inner radius. Legend with name + amount + %. Hidden when totalAssets is 0.
- `drawMonthlyBarChart` — 12-month income (green) + expense (red) bars. Y-axis labels + reference grid lines. Same financing exclusion as P&L. Tiny legend below.
- `drawNetWorthTrendLine` — connects NetWorthSnapshot points chronologically. Area-fill under the line. Min/mid/max y-axis labels, endpoint date labels. Empty-state hint when < 2 snapshots.

**generatePDF added `snapshots: List<NetWorthSnapshot> = emptyList()` param.** Callers updated:
- FinanceViewModel.exportToPDF: `repository.getNetWorthSnapshots(pid).first()`.
- ReportsHubScreen + ProfitLossScreen: `viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())`.

**Pattern: each chart panel does its own checkBreak()** so any chart that won't fit on the cover starts on a fresh page. The cover may flow to a 2nd page on installs with little data + big charts — that's fine; the data tables underneath still chain via existing checkBreak in drawTableRow.

**Stage 4 pending:** XLSX overhaul — currency-format numeric cells, conditional formatting (overdue red / negative red), frozen first row + auto-filter, totals rows, Summary sheet first. Closes C21 and the last remaining P1 cluster.

### 2026-05-27 — 3.2.36 (C21 Stage 2 of 4: PDF data correctness — Debts section + word-wrap + dynamic Status + Type column + interest-type default)

**Type:** Stage 2 of 4 for C21. Audit §C21 fixes #3, #4, #5, #6, #7, #17 all in this commit.

**Internal milestone:** `3.2.36` / `versionCode = 159`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — new helpers are pure / deterministic).

**ExportUtility.generatePDF added:**
- `debts: List<Debt> = emptyList()` param threaded from FinanceViewModel.exportToPDF, ProfitLossScreen, ReportsHubScreen.
- New "Liabilities & Debts" section between Lending and Investments. Same column shape as Lending for visual parity.

**ExportUtility.drawTableRow rewritten:**
- Replaced character-count truncation with `wrapText(text, paint, maxWidth)` — measures via `Paint.measureText`, word-wraps, per-character fallback for words too long for the column. Row height grows to fit the cell with the most wrap lines.
- Eliminates "Salary Transf…" / "Mohan Ra…" data loss.

**ExportUtility added:**
- `computeBorrowerStatus(b, today)`: WrittenOff → "Written Off"; paid >= amount → "Closed"; due < today → "Overdue"; else "Active".
- `computeDebtStatus(d, today)`: same logic minus WrittenOff (debts don't have that state in current schema).

**ExportUtility recent-transactions title:**
- Pre-Stage 2 always "Recent Transactions (last 50)" even when user had 9 transactions. Now: `transactions.size <= 50` → "All Transactions (N)"; else "Most Recent 50 of N Transactions".

**ExportUtility Type column:**
- Recent Transactions widths shuffled — Type 10%→12%; Description 27%→26%; Category 18%→17%. Total still 100%. "Transfer" no longer wraps.

**ExportUtility loan statement:**
- Pre-Stage 2 rendered `"${rate}% p.a. (${borrower.type})"` producing `"${rate}% p.a. ()"` when type was blank — falsely implies Simple Interest. Now renders "Not specified" for blank type.

**Three caller sites updated:**
- FinanceViewModel.exportToPDF threads `debts.value`.
- ProfitLossScreen added `viewModel.debts.collectAsState()` (wasn't collected before — P&L screen never used debts in-screen) and threads it.
- ReportsHubScreen added `borrowers / debts / investments` collectAsState (was passing `emptyList()` for all three; the Reports landing PDF was missing all of them pre-Stage 2). Reports → Export PDF now produces a comprehensive report.

**Pattern: word-wrap as default, truncation as bug.** Truncation hides data the user needs ("Salary Transf…"). Word-wrap costs vertical space but preserves information — strictly better default for a finance report.

**Stages 3-4 pending:**
- Stage 3 — PDF charts + KPIs: asset allocation donut + monthly income/expense bar + net worth trend line; 5 new KPIs (Total Liabilities / Total Lent Out / Monthly Income / Monthly Expense / Net Cash Flow).
- Stage 4 — XLSX overhaul: currency-format numeric cells, conditional formatting (overdue red, negative red), frozen first row + auto-filter, totals rows, Summary sheet first.

### 2026-05-27 — 3.2.35 (C21 Stage 1 of 4: PDF identity + cover header + standardized filename pattern)

**Type:** Stage 1 of 4 for C21 (last remaining P1 cluster). Audit §C21 fixes #1, #2, #8, #9 land here. #18 accepted as Android PdfDocument framework limitation.

**Internal milestone:** `3.2.35` / `versionCode = 158`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — pure helpers + caller threading; no data path change).

**ExportUtility added:**
- `filename(reportType, subject, ext)` — `Fynlo_<Type>_<yyyy-MM-dd>_<safeSubject>.<ext>`. Subject sanitized via Regex to alphanumeric+underscore.
- `headerInfoLine(project, user, period, currencyCode)` — `Project: X | User: Y | Period: Z | Currency: INR (₹)`. User omitted when blank.

**ExportUtility PDF generators (3 of them) all gained:**
- `projectName: String = "Personal"`, `userEmail: String = ""`, `periodLabel: String = "All time"` params (loan statements skip periodLabel since they always span the full loan history).
- Identity row rendered as bold 10pt on the cover, immediately under the title.

**Five caller sites updated:**
- FinanceViewModel.exportToPDF, ReportsHubScreen, ProfitLossScreen, CustomerDetailScreen, MoneyFlowScreen (PDF + CSV), MonthlySummaryScreen CSV. All use `ExportUtility.filename(...)` for the file path and thread project + email + period (where available).

**Accepted limitation (audit #18):** Android's `PdfDocument` doesn't expose Title/Author/Subject info-dictionary setters. The identity row inside the PDF cover carries the same data onto a page that opens directly when the user views the file. Documented in `ExportUtility.PDF_METADATA_LIMITATION_NOTE`.

**Stages 2-4 pending:**
- Stage 2 — PDF data correctness: Debts section in generatePDF, dynamic Status (Active/Overdue/Closed) computed from due+paid, no column truncation, "Recent Transactions" title fix, wider Type column, no silent Interest Type default.
- Stage 3 — PDF charts + KPIs: asset allocation donut + monthly income/expense bar + net worth trend line; 5 new KPIs (Total Liabilities / Total Lent Out / Monthly Income / Monthly Expense / Net Cash Flow).
- Stage 4 — XLSX overhaul: currency-format numeric cells, conditional formatting for overdue / negative growth, frozen first row + auto-filter, totals rows, Summary as first sheet.

### 2026-05-27 — 3.2.33 (C15 Stage 5 of 5 — closes C15: C15e Money Flow category-grouped visualization)

**Type:** Stage 5 of 5 for C15. **Closes C15 cluster in full.** Audit §C15e #1 (build flow visualization) landed here. Tenth P1 Sprint-2 cluster closed.

**Internal milestone:** `3.2.33` / `versionCode = 156`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — pure UI; reads existing `allFlows` list).

**MoneyFlowScreen added:**
- `MoneyFlowVisualization` composable at the top of the LazyColumn (above Account Flows). Two parallel columns in one surface:
  - **Inflows** (Emerald, left) — top 5 by amount from `INCOME` + `DEBT_RECEIVED` flows grouped by `flow.from`. Rest → `Other` bucket.
  - **Outflows** (SemanticRed, right) — top 5 from `EXPENSE` + `DEBT_REPAY` flows grouped by `flow.to`. Rest → `Other`.
- Each row: category label + amount (color-coded, right-aligned) above a proportional horizontal bar. Independently scaled per column. Bars floor at 4% width so near-zero categories stay visible.
- Empty-state placeholders for either side when no flows of that direction exist yet.
- LENDING / TRANSFER / INVESTMENT excluded — lending principal already in Flow Summary as "Lent Out"; transfers and investments are internal movements, not real wallet inflows/outflows.

**MoneyFlowScreen kept (existing surface preserved):**
- Account Flows (top accounts by total movement).
- Flow Summary (Total Inflow / Outflow / Lent Out / Net Flow).
- Transaction Flows filter-chip list (All / Income / Expense / Transfer / Lending / Debt).
- CSV + PDF export dropdown.

**Pattern: visualization layered on top of existing functionality.** The user's smoke surfaced that the screen wasn't actually empty — it had real list functionality. So instead of replacing or removing, the visualization layered on top satisfies the audit's "build flow visualization" call without losing what was useful.

**C15 closed in full.** All five sub-stages landed:
- C15a (3.2.29) — Reports landing → pure launcher with previewed tiles.
- C15b (3.2.30) — P&L Statement chart-hero + callout cards + Total Lent Out fix.
- C15c (3.2.31) — Net Worth History chart-hero + callouts + transaction-history backfill + open-daily nag removal.
- C15d (3.2.32) — Monthly Summary chart-hero + 12-month bar chart + axis labels + reference lines + callouts + linear-regression projection + CSV export.
- C15e (3.2.33, this commit) — Money Flow category-grouped visualization.

**Tenth P1 cluster closed.** Remaining P1: C21 (PDF/XLSX export quality polish). Plus deferred Task #24/#26/#27/#28. P2 cluster work (C10/C11/C16/C17/C19/C20) and C22 v4+ backlog (P3) still pending per release-cadence ADR — no Play Console upload until all are closed.

### 2026-05-27 — 3.2.32 (C15 Stage 4 of 5: C15d Monthly Summary chart-hero + 12-month bar chart + axes + callouts + projection + CSV)

**Type:** Stage 4 of 5 for C15. Audit §C15d fixes #1–#6 all in this commit. Only Stage 5 (C15e Money Flow build-or-remove) remains for C15 closure.

**Internal milestone:** `3.2.32` / `versionCode = 155`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — pure UI + linear-regression math; CSV writes through same FileProvider as PDF export).

**MonthlySummaryScreen restructured:**
- type_chart_hero block: "Net for May +₹X" hero + 12-month bar chart in one shared surface. Same shape as C15b (P&L) and C15c (Net Worth History).
- Bar chart extended 6 → 12 months. Cash-basis exclusion of financing categories matches P&L Statement.
- Y-axis labels + 4 horizontal reference lines (25/50/75/100% of max) so the user can read magnitudes off the chart.
- 4 callout cards replacing prior 3-chip row: Best Month / Worst Month / Avg/Month / Trend (recent-6 vs prior-6 avg delta).
- Linear-regression projection of income + expense series independently. Next 3 months rendered as alpha=0.4 ghost bars to the right of a dashed vertical divider. Projected month labels prefixed with "·" and dimmed.
- CSV export FilledTonalIconButton (TableChart icon) at top-right of hero. Writes Month,Income,Expense,Net via FileProvider + ACTION_SEND chooser.

**Pattern: independent series projection.** I projected income and expense series independently rather than projecting the (net) series. Reasoning: income and expense have different drivers and trends; the user sees both projected trend lines and can compare. Projecting only the net would lose the magnitude story.

**MonthlySummaryScreen kept:**
- Idle Fund Alert (60% cash threshold).
- Month-by-Month Breakdown list at bottom (12 rows now instead of 6).

**Stage 5 pending:**
- C15e — Money Flow: build a Sankey or category-grouped flow visualization showing Income sources → categories → end balances, OR remove the tile if not building. Decision-heavy more than work-heavy.

### 2026-05-27 — 3.2.31 (C15 Stage 3 of 5: C15c Net Worth History chart-hero + callouts + backfill + nag removal)

**Type:** Stage 3 of 5 for C15. Audit §C15c fixes #1–#6 all in this commit. Stage 4 (C15d Monthly Summary) follows.

**Internal milestone:** `3.2.31` / `versionCode = 154`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — `backfillNetWorthHistory` routes through the same `saveNetWorthSnapshot` path as the daily auto-save).

**NetWorthHistoryScreen added:**
- Chart-hero block — Current Net Worth + `NetWorthLineChart` share one rounded surface so they read as a single `type_chart_hero` unit (same shape established for C15b P&L Statement in 3.2.30).
- 3-callout row (replacing the prior Highest/Lowest/Change% row): `1-Month Change` / `6-Month Change` / `All-Time High`. The %-change cards use the snapshot closest-to-but-not-after the target date; show "Need more data" with neutral color when no snapshot in range.
- "Backfill from history" OutlinedButton — primary CTA in empty state, secondary action when data exists. Status text appears under the button after each run.
- Local `pluralCount(n, "snapshot", "snapshots")` helper so "1 snapshot recorded" doesn't render as "1 snapshots".

**NetWorthHistoryScreen removed:**
- "Open the app daily to track net worth trends" nag (audit #6). The backfill CTA replaces it.

**FinanceViewModel added:**
- `backfillNetWorthHistory(onDone)` — walks transactions month by month from the user's earliest cash-basis date to last completed month, computes `approxNW = currentNW − (cumulative cash flow from monthEnd+1 to today)` for each month-end, inserts as `NetWorthSnapshot`. Financing categories excluded so debt received / loans extended / investments don't double-count. Existing snapshot dates skipped via `getNetWorthSnapshots(pid).first()`. Returns count to UI for the status text.

**Limitation by design:** investment unrealized value changes aren't reconstructable from history (we only have `currentVal`), so the backfilled curve treats investment value as flat. The result is a cash-flow-based trend — accurate for direction reading. Daily auto-save still runs, accumulating real point-in-time data going forward.

**Pattern: backfill over nag.** The audit's load-bearing C15c insight is that asking users to "open daily for trends" is a bad UX. The data is already there in their transaction history — generate it. The cash-flow approximation isn't perfect (no investment-value reconstruction) but it gets the user a useful trend immediately instead of weeks later.

**Stages 4-5 pending:**
- C15d — Monthly Summary: type_chart_hero "Net for May ₹X", bar chart (income green + expense red) last 12 months, y-axis labels + reference lines, callout cards (Best/Worst/Avg/Trend), projection line, CSV export.
- C15e — Money Flow: build Sankey or category-grouped flow visualization, or remove the tile.

### 2026-05-27 — 3.2.30 (C15 Stage 2 of 5: C15b P&L Statement chart hero + callout cards + Total Lent Out fix)

**Type:** Stage 2 of 5 for C15. Audit §C15b fixes #1–#5 all in this commit. Stage 3 (C15c Net Worth History) follows.

**Internal milestone:** `3.2.30` / `versionCode = 153`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — UI restructure + same-source calculations; only formula change is the Total Lent Out split which is a deterministic sum over borrower data).

**ProfitLossScreen added:**
- `MonthlyPLLineChart` Canvas composable — rolling-12 income (green) + expense (red) lines, shared y-axis, point markers, month axis labels (every third + last).
- Chart sits inside the same surface as the Net P&L hero number for the `type_chart_hero` shape per DESIGN_SYSTEM §1.2.
- 4 callout-card row: This Month / Last Month / YTD / vs Last Year. `vs Last Year` is the YTD delta (this year YTD minus prior-year same-window), not absolute.
- Legend dots under the chart for the Income/Expense colour mapping.
- Empty-state hint when every monthly bucket is zero.

**ProfitLossScreen fixed:**
- "Total Lent Out" was `activeBorrowers.sum(amount)` — excluded written-off loans (not lifetime) AND included already-recovered principal (not outstanding). Split into two rows:
  - `Total Lent Out (lifetime)` = every borrower's original principal including write-offs.
  - `Currently Lent Out` = active borrowers' outstanding (amount − paidPrincipal, floored at 0).
- Static "You are profitable ↑" subtitle replaced with cash-basis arithmetic: "Cash basis · income X − expenses Y". User sees WHY rather than just the green ↑.

**Pattern: chart-hero treatment doesn't require a separate component.** I put the chart + hero number in the same surface (rounded background, single padding block) so they read as one element rather than two stacked. That's what makes it `type_chart_hero` instead of `type_hero` + `type_chart`.

**Stages 3-5 pending:**
- C15c — Net Worth History: line chart, type_chart_hero Current Net Worth, callout cards (1M / 6M / All-Time High), backfill from transaction history, remove "open daily" nag.
- C15d — Monthly Summary: type_chart_hero "Net for May ₹X", bar chart (income green + expense red) last 12 months, y-axis labels + reference lines, callout cards (Best/Worst/Avg/Trend), projection line, CSV export.
- C15e — Money Flow: build Sankey or category-grouped flow visualization, or remove the tile.

### 2026-05-27 — 3.2.29 (C15 Stage 1 of 5: C15a Reports landing converted to pure launcher with previewed tiles)

**Type:** Stage 1 of 5 for C15. Cleanup pass on Reports landing per audit §C15a — converted from "inline rollup + tile grid" hybrid to a clean Home-archetype launcher where every tile carries a one-line preview value computed against the selected date range. Closes audit C15a fixes #1, #2, #3, #4. Stages 2-5 (C15b/c/d/e) follow.

**Internal milestone:** `3.2.29` / `versionCode = 152`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — UI restructure with no logic change).

**ReportsHubScreen stripped (audit C15a #1 + #4):**
- Income + Expense two-column block (duplicates P&L Statement).
- Net Cash Flow + Savings Rate row (duplicates P&L Statement).
- Net Worth Trend mini-chart with Canvas line + first/last date labels (duplicates Net Worth History).
- "Where Money Went" section with category bars top-6 (duplicates P&L + Spend tab).
- "Where Money Came From" section with category rows top-5 (duplicates P&L).

**ReportsHubScreen added (audit C15a #2 + #3):**
- `ReportTileCard` replacing `ReportLinkCard` — same shape across every tile (`heightIn(min = 116.dp)`, 36-dp icon circle, label, preview-value row). Standardized sizing.
- One-line preview value per tile computed from a memoised `RangeAggregate`:
  - P&L: signed net (+/− amount, green/red).
  - Net Worth: current value (green if positive, red if negative).
  - Money Flow: gross movement total or "No activity".
  - Interest Income: sum of income txns where category = "Interest" in range.
  - Monthly Summary: always this calendar month's net regardless of selectedRange (that screen has its own month picker).
  - Debt Payoff: total outstanding debt or "Debt free" when zero.
  - EMI Calculator: "Calculator" label (no data preview makes sense for a tool).

**Pattern: drill-down preview tiles.** The audit's load-bearing C15a fix is "tiles preview the report data they link to" — that's what makes a Home-archetype launcher useful instead of just a menu of names. The user can see "P&L this month: +₹35K" without tapping in; if it's interesting they tap to drill.

**Stages 2-5 pending:**
- C15b — P&L Statement: line chart of monthly income vs expense over rolling 12 months, type_chart_hero Net P&L above, callout cards (This Month / Last Month / YTD / vs Last Year), fix "Total Lent Out" definition.
- C15c — Net Worth History: line chart, type_chart_hero Current Net Worth, callout cards (1M / 6M / All-Time High), backfill from transaction history, remove "open daily" nag.
- C15d — Monthly Summary: type_chart_hero "Net for May ₹X", bar chart (income green + expense red) last 12 months, y-axis labels + reference lines, callout cards (Best Month / Worst Month / Avg / Trend), projection line, CSV export.
- C15e — Money Flow: build a Sankey or category-grouped flow visualization, or remove the tile if not building.

### 2026-05-27 — 3.2.28 (C12 Stage 3 of 3 — closes C12: row simplification + Send Reminder picker + new DebtDetailScreen)

**Type:** Stage 3 of 3 for C12 — **closes C12 in full**. Row simplification (audit #5, #6), action lift to detail screens (audit #7), WhatsApp+SMS consolidation into single Send Reminder picker (audit #8). One audit point (§C12 #4 column-header sort affordance) deferred since the row layout has no column headers to tap.

**Internal milestone:** `3.2.28` / `versionCode = 151`. No Play Console upload per release-cadence ADR. No test gate change (114 tests / 10 classes / 0 failures — same as 3.2.27 — UI-only restructure with no logic or state-shape change).

**LendingCard (radical strip — was ~380 lines, now 93):**
- Down to icon + name + OVERDUE chip + sub-line + outstanding + chevron per audit #6.
- Stripped 5 inline action callbacks (`onDelete`, `onEdit`, `onCollect`, `onDefault`, `onWriteOff`) — kept only `onClick`.
- Stripped the 70-line in-row WhatsApp smart-message builder, the SMS builder, the "Share loan summary" button, the "Add Phone" hint, the MoreVert dropdown menu, the inline Collect Payment button, the inline Mark NPA / Restore toggle, the duplicate Total Outstanding section, the Days Elapsed / Per Day Interest / Paid So Far strip, the Both interest type SI+CI portion split, the principal + interest two-column body.

**DebtCard (parallel strip — was ~137 lines, now 88):**
- Same shape as LendingCard so Lent + Owed look identical per audit #5.
- Stripped 3 inline action callbacks (`onEdit`, `onDelete`, `onPay`) — kept only `onClick`.
- Stripped MoreVert dropdown, status Badge (redundant with the segmented filter Stage 2 added), inline Pay Instalment button, Days Elapsed strip, Both portions block, Borrowed Amount + Interest two-column body.

**LendingScreen state cleanup:**
- Removed `editingBorrower`, `collectingForBorrower`, `defaultingBorrower`, `writeOffBorrower` state vars and their dialog blocks (CollectPaymentDialog, Mark-as-Defaulted AlertDialog, Write-Off AlertDialog).
- Removed `val accounts`, `val people`, `val currencySymbol` references (no longer needed without the inline message builder).
- AddLendingDialog simplified — Add flow only; Edit moved to detail.

**DebtScreen state cleanup:**
- Removed `editingDebt`, `payingDebt` state vars and their AddDebtDialog-as-editor + PayDebtDialog blocks.
- Removed `val accounts` reference.
- AddDebtDialog simplified — Add flow only; Edit + Pay moved to detail.

**CustomerDetailScreen expansion:**
- **Send Reminder OutlinedButton** under Collect Payment — opens AlertDialog channel-picker with WhatsApp + SMS buttons (audit #8 consolidation). Smart-message builder lifted from the old LendingCard (overdue-aware text, per-day interest accrual, full loan summary for WhatsApp; trimmed essentials for SMS). When phone is blank, dialog hides channel buttons and shows "Tap Edit to add a phone" hint.
- **Mark NPA / Restore Active OutlinedButton** (toggles label based on borrower.status) with confirm dialog. Goes through the same `viewModel.markBorrowerDefaulted` / `restoreBorrowerToActive` methods the old LendingScreen dialog used.
- **Write Off OutlinedButton** (only when totalOutstanding > 0) with confirm dialog routing through `viewModel.writeOffBorrower`. On confirm, navigates back since the borrower's gone from receivables.
- Pair the Mark NPA + Write Off buttons in a single Row so they sit at the bottom of the action stack as secondary actions; primary actions (Collect, Send Reminder) stack above.

**NEW: DebtDetailScreen** (`app/src/main/java/app/fynlo/ui/screens/DebtDetailScreen.kt`, ~285 lines):
- Mirrors CustomerDetailScreen visual structure so Lent + Owed feel like one design (audit #5 carried through to detail too).
- TopBar: Edit + Delete actions.
- Body: hero outstanding number in red (liability), Make Payment primary button, Payment History list from `viewModel.debtPayments`, Notes card.
- Doesn't have Send Reminder (you don't message yourself for debts) or NPA/Write-Off (receivable-side concepts only). Asymmetric on purpose.
- Wired into nav graph at `debt/{debtId}` route. Added `onNavigateToDebtDetail` prop on LoansHubScreen + the top-level Debts route in Navigation.kt; threaded down to DebtScreen's new `onNavigateToDetail` param.

**Pattern: drastic detail-screen vs list-row split.** The cluster's biggest UX win is breaking out of the "every card has all 6 actions inline" pattern that made the list noisy. The cost is one extra tap to reach actions, but the win is scannability — users can flip through the list looking for a name without parsing 6 buttons per row.

**Lost in this commit:**
- Per-row Pay button on debt cards. Now: tap row → detail → Make Payment.
- Direct per-row WhatsApp icon. Now: tap row → detail → Send Reminder → pick WhatsApp.
- "Add Phone" shortcut from no-phone borrowers in the list. Now: tap row → detail → Edit (TopBar).

Each of those is one extra tap. Acceptable given the scannability gain. If user demand for direct-from-list shortcuts surfaces, can add a long-press menu to rows as a follow-up.

### 2026-05-27 — 3.2.27 (C12 Stage 2 of 3: filter consolidation across Lending + Debt screens)

**Type:** Stage 2 of 3 for C12. Filter UI consolidation across both child screens of LoansHubScreen. Closes audit fixes #3 (single Active/Overdue/Closed filter) + #4 (drop sort dropdown). Stage 3 (per-row action removal + Send-reminder picker) follows.

**Internal milestone:** `3.2.27` / `versionCode = 150`. No Play Console upload per release-cadence ADR. No test gate change.

**LendingScreen changes (the bigger surface):**
- **Removed Interest/Hand TabRow** — the internal selectedTab that switched between interest-loan and hand-loan rows. Audit's point: the Hand vs Interest distinction is a row attribute, not a top-level dimension worth its own tab. % rate inline on each card conveys it without a separate UI dimension.
- **Removed sort dropdown** (audit #4) — `Overdue / Amount / Name / Date` `DropdownMenu`. Fixed sort now: overdue-first then amount-desc. Loses user-toggleable Name and Date sorts; if user demand surfaces, audit's "column-header sort affordance" (also #4) is the proper follow-up.
- **Removed stats line** "X interest · Y hand · Z settled" — replaced by per-segment counts on the filter.
- **Removed collapsible "Settled" section** at the bottom of the list — settled loans now live under the `Closed` filter, exposed in one tap.
- **Removed in-screen Total Outstanding hero** — LoansHubScreen's C12 Stage 1 hero (3.2.25) owns this at the parent level.
- **Removed BackHandler** for the TabRow — gone with the TabRow itself.
- **Added Active/Overdue/Closed segmented filter** with per-segment counts.
- **Filter-specific empty states**: "No overdue loans — you're up to date 🎉" / "No closed loans yet" / "No active loans" / (full empty) → existing EmptyLendingState illustration.

**DebtScreen changes (simpler, parity):**
- **Removed in-screen Total Outstanding summary card** — same parent-level redundancy.
- **Added Active/Overdue/Closed segmented filter** with parity logic: `Active = paid < amount`, `Overdue = active AND due date past today`, `Closed = paid >= amount`.
- **Filter-specific empty states** for each segment.

**Pattern: when two parallel screens have different filter UIs, unify both in one commit.** I almost did just LendingScreen and let DebtScreen lag, but per the LendingDialog→DebtDialog widget-unification lesson from 3.2.26 ("when migrating widget types, do it across all matching surfaces in one pass"), doing both kept consistency.

**Lost in this commit (potential regression):**
- User-toggleable sort modes (Name / Date / Amount). The fixed overdue-first then amount-desc sort matches the prior dropdown's default. Audit's #4 wants "column-header sort affordance instead" — meaningful but more work. Deferred to follow-up if user demand surfaces.

**C12 progress:** Stage 1 ✓ (hero + SI+CI rename), Stage 2 ✓ (filter consolidation), Stage 3 ⏳ (row simplification + Send-reminder picker — audit #5, #6, #7, #8).

### 2026-05-27 — 3.2.26 (LendingDialog interest picker unified to dropdown — smoke surface)

**Type:** consistency fix surfaced by C12 Stage 1 smoke. User observed: "why the interest type dropdown not done in lent section?" — fair question, because DebtDialog had been migrated to a dropdown widget while LendingDialog still used a chip picker with a Pro-gated "Advanced options" TextButton. Both pickers serve the same purpose; one widget for both.

**Internal milestone:** `3.2.26` / `versionCode = 149`. No Play Console upload per release-cadence ADR. No test gate change.

**What landed:** LendingDialog switched to `ExposedDropdownMenuBox` matching DebtDialog's widget. Free vs Pro gating preserved by varying the dropdown's options at construction time — free users see only `["Simple Interest"]`, Pro users see all 4 (with `"Both"` rendered as `"SI + CI"` via `InterestEngine.label`). The "Advanced options" TextButton is gone — Pro users see all options immediately (they already paid for them). Free-tier gating still enforced through the option list itself.

**Edge case:** if a free user has a borrower previously saved with an advanced type (Pro downgrade or admin override), the field displays the current type via `InterestEngine.label`, but the dropdown only offers Simple Interest — they can switch back, but not to another advanced type without upgrading.

**Pattern reinforced:** when migrating widget types (chip → dropdown), do it across all matching surfaces in one pass. C12 Stage 1's `Both`→`SI+CI` rename touched 6 sites; the widget swap for LendingDialog should've gone with it. Smoke-surfaced inconsistencies are normal and expected — that's why we smoke between stages.

### 2026-05-27 — 3.2.25 (C12 Stage 1 of 3: LoansHub hero + Both→SI+CI rename)

**Type:** first of 3 stages closing C12 (audit's "biggest UX disaster," 10 fix points, XL effort estimate). Per the C12 staging plan: Stage 1 (Home-archetype hero + naming polish) tonight; Stage 2 (filter consolidation) and Stage 3 (row simplification + Send-reminder picker) follow.

**Internal milestone:** `3.2.25` / `versionCode = 148`. No Play Console upload per release-cadence ADR. No test gate change.

**What landed (3 audit fix points):**

1. **#1 + #2 Total Outstanding hero** on LoansHubScreen above the Lent/Owed segmented row. "Total Outstanding" label → big colour-coded amount (Emerald for Lent, Red for Owed) → "Across N loans / debts" subtitle with proper pluralisation. Numbers sourced from already-computed `financialSummary.totalReceivables` and `totalDebtPrincipal + totalDebtInterest` — no extra fetch. Active count predicates mirror `LendingScreen.isActive`. Hero hidden when zero entries.

2. **#9 "Both" → "SI + CI"** at every user-facing site. Stored value stays `"Both"` (DB rows + `InterestEngine` branch logic depend on it; migrating would need a schema migration and breaks the engine). NEW `InterestEngine.label(storedType)` helper translates display-only. Routed through at 6 sites: PaymentDialog interest subtitle, CustomerDetailScreen rate row, DebtScreen card type line, LendingScreen share-copy line, DebtDialog dropdown (field + menu items), LendingDialog chip picker.

3. **#10 FAB padding** — already done in C06.

**Deferred to C12 Stages 2-3:**

- **Stage 2 (audit #3, #4):** replace 3 filter UIs (Lent/Owed tab + Interest/Hand internal tab + sort dropdown) with single `Active / Overdue / Closed` segmented control. Drop sort dropdown. Structural change to filter logic.
- **Stage 3 (audit #5, #6, #7, #8):** standardise Lent vs Owed row visual; remove per-row action icons (move to CustomerDetailScreen); consolidate WhatsApp + SMS into "Send reminder" picker. Touches both screens' row layouts + CustomerDetailScreen action surface.

**Pattern continued:** C12 is too big for one commit per the audit's XL estimate. Splitting into 3 stages where Stage 1 (this commit) is the Home-archetype skeleton — most user-visible immediate change — lets the user smoke-test the redesign idea before the bigger structural Stage 2 + 3 work commits to a path. Same staging philosophy as C08 (which took 4 stages over 5 commits).

**Remaining P1 work:**
- C12 Stages 2-3
- C15 Reports redesign (4 sub-screens, XL)
- C21 PDF/XLSX export quality polish

### 2026-05-27 — 3.2.24 (C14 Invest tab Home-archetype migration)

**Type:** second of the C12-C15 P1 screen-redesign series. Same pattern as C13: investigation showed InvestmentScreen had no portfolio-level hero or allocation visual (audit's "Home archetype" wasn't met at all), but the per-card content was already strong. Three structural changes close the cluster's spirit; 5 feature-adds defer.

**Internal milestone:** `3.2.24` / `versionCode = 147`. No Play Console upload per release-cadence ADR. No test gate change.

**What landed (3 fixes):**
1. **Portfolio Value hero** above the holdings list. Big number + coloured-arrow growth line (`↑ +₹50,000 (+15.2%)` Emerald, `↓ −₹X (−Y%)` red) + subtitle `₹X invested · N holdings`. Computed once via `investments.sumOf { ... }` so no extra fetch / no schema impact.
2. **Allocation horizontal stacked bar** by investment type, with legend rows below showing colour + type + amount + percentage. Uses shared `ChartColors` palette. Hidden when only one holding type exists (single-block bar conveys no information).
3. **InvestmentCard button hierarchy fixed** — was INVERTED: `Update Value` was OutlinedButton (secondary), `Withdraw` was filled Emerald Button (primary). Update Value is the more-frequent action (markets update daily; withdrawals are rare), so swapped: Update is now primary filled, Withdraw is outlined secondary.

**Bonus:** `Holdings` section header between portfolio-level info and per-card list — marks the transition cleanly.

**Already done:**
- Audit #3 (list rows: icon + name + current value + growth %) — existing rich card meets this. Per audit acceptance, card content was already strong; only the portfolio-level surrounding context was missing.
- Audit #10 (negative growth in red) — already used SemanticRed throughout.

**Deferred (Task #28):**
- #5 CAGR / XIRR (Newton's-method finance math, own commit)
- #6 Mutual Fund SIP taxonomy fix (AddInvestmentDialog dropdown refactor)
- #7 Celebration toast on growth increase
- #8 FD type rendering verification (folds into #6 taxonomy investigation)
- #9 Valuation History chart (chart work, own commit per `DESIGN_SYSTEM §9.14`)

**Pattern reconfirmed:** Home-archetype migrations for screens that already have strong per-row content are tightly scoped — just add the portfolio-level hero + visual summary on top of the list. C13 (Expenses) and C14 (Invest) both fit this pattern. C12 (Loans) will likely need MORE per-row rework because the audit explicitly calls out the row design as the problem ("3 filter UIs stacked", "asymmetric Lent vs Owed layouts", "4 action icons per row") — different scope shape.

**8 of 9 P1 Sprint 2 clusters closed.** Remaining: C12 (Loans, XL — biggest), C15 (Reports, XL across 4 sub-screens), C21 (PDF/XLSX polish).

### 2026-05-27 — 3.2.23 (C13 Expenses tab Home-archetype migration)

**Type:** first of the C12-C15 P1 screen-redesign series. Investigation showed SpendScreen already had most of the Home-archetype skeleton from earlier work (hero number from baseline, category-split bars, recent list from C08-era state) — only 4 of 10 audit fix points needed real work, and 2 were already done elsewhere (audit #2 done as bars, audit #10 done in C03a/C05).

**Internal milestone:** `3.2.23` / `versionCode = 146`. No Play Console upload per release-cadence ADR. No test gate change.

**What landed (4 fixes):**
1. **MoM delta on hero** — coloured arrow line (`↑ red`, `↓ Emerald`, `→ neutral`) below the total amount. "₹500 more / less than last month." Hidden when there's no previous month's data.
2. **Top-category callout** — "Mostly on $cat — ₹X (NN%)" line below the hero, only shown when one category has ≥30% share. Below that the "mostly" framing is misleading, so suppress.
3. **Sectioned recent list** — bucketed Today / Yesterday / This Week / Earlier, only non-empty buckets render their header. Cap raised 15 → 20 rows.
4. **In-page Add button removed** — Scaffold FAB owned by Navigation is the single Add affordance now. AddTransactionDialog wiring kept for the empty-state CTA + FAB-launched flow.

**Already done before this commit:**
- Audit #2 (small bar showing category split) — Category Breakdown already renders horizontal bars per category sorted by amount.
- Audit #10 (literal "Expense" category bug) — C03a / C05 closed this; TransactionValidator sanitises "Expense" → "Uncategorized", and the chip picker is per-type.

**Deferred (Task #27):**
- #5 Recurring toggle (needs RecurringTransaction integration + dialog affordance)
- #6 Receipt photo attach (camera + gallery + Storage backend)
- #7 Tags field (free-text + persistence + filtering)
- #8 Split transactions (Transaction model schema implication)
- #9 Edit Transaction Type + Account changes (EditTransactionDialog rewrite)

Each is a meaningful feature with its own scope; land independently when called for. C13's "Home archetype" spirit is closed; these are feature-adds.

**Pattern reinforced from C18:** the audit estimates effort assuming all fix points need building from scratch. Investigation often shows 2-4 are already done or covered elsewhere — meaningful triage before estimating. C18 was "M (2-3 days)" → done in 1 commit + 1 follow-up because 5 were already done. C13 was "L (1 week)" → done in 1 commit because 6 were already done.

**Pattern: feature-vs-redesign distinction.** When an audit cluster mixes "redesign the layout" with "add new features," doing the redesign first (clean closure) and deferring features (own tasks) keeps each commit cohesive. Audit acceptance for cluster closure is the redesign; features that "happen to live on the same tab" are independent work.

**7 of 9 P1 Sprint 2 clusters closed.** Remaining: C12 (Loans, XL — biggest user-pain), C14 (Invest, L), C15 (Reports, XL across 4 sub-screens), C21 (PDF/XLSX export quality polish).

### 2026-05-27 — 3.2.22 (light-mode toggle visibility fix — smoke surface)

**Type:** one-line-class UX fix surfaced by smoke of 3.2.21. User reset all data, went through the new theme-aware setup wizard (confirmed background looks good), then opened Settings to verify the redesigned Personalization Switches and reported "not clearly visible" in light mode. Same root cause hit the SelectionCards in the setup wizard's NotificationStep.

**Internal milestone:** `3.2.22` / `versionCode = 145`. No Play Console upload per release-cadence ADR. No test gate change.

**Root cause:** M3 default unchecked Switch colors (`outline` thumb on `surfaceContainerHighest` track) are intentionally subtle, and they faded into the SettingsCard's `surfaceVariant` background in light mode. Similarly, 3.2.21's SelectionCard unselected background `surfaceVariant.copy(alpha = 0.4f)` was too washed-out against the theme-aware page gradient.

**Two fixes:**
1. All 4 new Switches (Settings Notifications + Personalization, added in 3.2.20 and 3.2.21) got explicit `uncheckedThumbColor = onSurfaceVariant` and `uncheckedBorderColor = onSurfaceVariant`. OFF state now definitely visible in both themes.
2. SelectionCard unselected: bumped from `surfaceVariant.copy(alpha=0.4f)` to full-opacity `surfaceVariant`, plus a visible `outline.copy(alpha=0.4f)` border so the card is distinct from the page.

**Pattern logged:** when migrating from a hardcoded-color theme to a Material-theme-aware design, MUST verify in both light AND dark mode. Defaults like `surfaceVariant.copy(alpha = 0.4f)` look fine on dark mode (because dark base color is dark grey, alpha is barely visible) but disappear into a light page background. Pure `surfaceVariant` reads as a tonal step in both modes.

### 2026-05-27 — 3.2.21 (theme picker UX redesign + setup-screen theme removal + theme-aware setup bg)

**Type:** user-driven out-of-band UX improvement, three related changes in one cohesive commit. Not from an audit cluster. The user observed the inconsistency between the Notifications section's Switch-row pattern (post-C18) and the Personalization theme picker's SegmentedButtonRow pattern, asked for a redesign, and also called out two related issues with the first-launch setup screen (theme step is friction, dark gradient ignores phone theme).

**Internal milestone:** `3.2.21` / `versionCode = 144`. No Play Console upload per release-cadence ADR. No test gate change.

**Three changes:**

1. **Settings → Personalization theme picker** redesigned to the two-tier Switch pattern Android's stock display-settings use. "Follow system theme" toggle on top; when OFF, a "Dark mode" sub-toggle appears. State mapping preserves `ThemeController.darkModeOverride` (null=system, false=light, true=dark). When the user toggles "Follow system" OFF, the override is seeded with the current visual state via `isSystemInDarkTheme()` so the screen doesn't flip — stays at whatever the user is currently seeing, just frozen under their control. Visually consistent with the Notifications card now.

2. **First-launch setup wizard: theme step removed.** Was 3 steps (theme / notifications / profile). Forcing the user to pick light/dark before they've used the app is friction; the app already defaults to "Follow system" which is the right choice for 95% of users. Now 2 steps. The old `ThemeStep` composable is kept as dead code (not called from anywhere) for now — delete in a follow-up if it stays unreferenced.

3. **First-launch setup pages background now respects system theme.** Was hardcoded `Brush.verticalGradient(Emerald900, Emerald700)` (dark splash regardless of system); now the same theme-aware gradient as OnboardingScreen (`background → background → Emerald700 alpha 4%`). Cascading migrations: every `Color.White` / `Color.White.copy(alpha=…)` in `NotificationStep` / `ProfileStep` / `StepLayout` / `SelectionCard` replaced with `MaterialTheme.colorScheme.onSurface` / `onBackground` / `onSurfaceVariant` / `surfaceVariant` / `outlineVariant` per role. Next button switched from white-on-Emerald900 (only readable on the old dark gradient) to Emerald500 + white text — brand accent that works against both light and dark backgrounds. Large-icon container in `StepLayout` switched to brand-tinted Emerald (12%-alpha) instead of 12%-alpha white.

**Pattern documented:** "user observes inconsistency between two UX patterns in adjacent sections, asks for the older one to migrate to the new one" — this is a common follow-on to bigger refactors like C18. Worth budgeting time for these mini-fixes within a few commits of the big change.

### 2026-05-27 — 3.2.20 (C18 Settings cleanup: 6 of 11 audit fix points)

**Type:** P1 Settings polish cluster. Investigation showed 4 of 11 audit points were already-done (verified during scope analysis), 1 is N/A (Wipe ALL Data placement), 1 deferred (#4 Report-a-Bug — needs its own new-screen commit). The remaining 6 landed in one cohesive commit.

**Internal milestone:** `3.2.20` / `versionCode = 143`. No Play Console upload per release-cadence ADR. No test gate change (pure UI + prefs refactor).

**What landed (6 fixes):**
- **Section headers redesigned** — removed emerald `•` bullet + emerald colour from `SettingsSectionLabel`; now plain bold default-variant text. Danger Zone keeps its red bullet (distinct role). Quieter, more consistent with rest of app.
- **Date Format dropdown with examples** — switched from `SegmentedButtonRow` (3.2.11) to `ExposedDropdownMenuBox`. Field shows `dd-MM-yyyy   →   27-05-2026`; menu items show `dd-MM-yyyy   (27-05-2026)`. Today's date used as example so it self-updates.
- **Notifications split into 2 toggles** — `LOAN_REMINDERS_ENABLED` + `BUDGET_ALERTS_ENABLED` prefs added. Both default to master `notifications_enabled` (preserves existing setup-screen behavior). Master derived as `loan OR budget` so `ReminderScheduler` keeps running on either; when both sub-toggles OFF, master flips OFF too. Worker-layer differentiation (which alarm class reads which sub-key when firing) deferred.
- **Currency picker rows show `INR   ₹   Indian Rupee`** — both field display and dropdown menu items use shared `currencyLabel(code)` helper that reads `CurrencyUtils.supported`. Unknown codes fall back to the bare code.
- **Rate-on-Play-Store usage-gated** — row hidden until `transactions.size >= 5`. Fresh-install users don't see the prompt. No automatic-prompt logic existed; the audit's "rate immediately" concern was about always-visible row affordance, now usage-gated.
- **Cleanup Seeder dialog confirm button gets `Red`** — was default; now matches Load Test Data + Wipe ALL destructive treatment.

**Already done (verified before scoping this commit):**
- #3 (currency locale default) — C04 Stage 3.
- #6 (Developer hidden in release) — `BuildConfig.DEBUG` gate already in place.
- #11 (Recalc description) — C02.

**Skipped as N/A:**
- #7 (Wipe ALL Data position) — Wipe ALL is debug-only; Reset All Data in Danger Zone covers user-facing.

**Deferred to own commit (Task #26):**
- #4 (Report a Bug in-app form) — new screen with form fields + crash-log attach + email-compose + back-nav. Not blocking C18; current mailto: behavior still works.

**Process lesson:** when an audit cluster has many fix points (11 here), the investigation phase is half the work. 4 were already-done, 1 N/A, 1 too-big-to-bundle — only 6 needed real implementation. Worth always running this triage before estimating effort. Audit's "M (2-3 days)" estimate was for all 11 from scratch; we got to "C18 closed for audit purposes" in one session because 5 didn't need doing.

**6 of 9 P1 Sprint 2 clusters closed.** Remaining P1: C12-C15 (screen redesigns — biggest remaining work), C21 (PDF/XLSX export quality polish — page breaks, group totals, embedded glyph fonts; partial follow-on from C08).

### 2026-05-27 — 3.2.19 (C09 CLOSURE: UTF-8 mojibake fixes + regression guard)

**Type:** quick P1 cluster — small in scope (3 files, ~18 mojibake fixes) but high visibility for the user (the warning emojis in the 3 destructive-action dialogs now render correctly). Audit asked for this as a "small, quick cluster, good warm-up." Done in one session with full regression coverage.

**Internal milestone:** `3.2.19` / `versionCode = 142`. No Play Console upload per release-cadence ADR.

**Root cause:** the audit hypothesized "Resources.getString() returning ISO-8859-1" — **wrong**. The strings aren't in `strings.xml`; they're Compose `Text("...")` literals in `.kt` files. The real cause: three source files (`SettingsScreen.kt`, `FinanceViewModel.kt`, `Navigation.kt`) had been saved through a Windows-1252-thinking editor at some prior commit, scrambling every multi-byte UTF-8 codepoint into 3-char Latin-1 mojibake sequences. Inspection via `Get-Item ... -ReadAllBytes` confirmed no BOM and the bytes match the typical CP1252-misencoding pattern.

**What landed:**
- **4 user-facing string fixes:** `SettingsScreen.kt:539` (`âš ï¸` → `⚠️` in Load Test Data dialog), `:555` (`â‚¹` × 2 → `₹` × 2 in Restore Real Data dialog), `:562` (`âš ï¸` → `⚠️` in Wipe ALL Data dialog), `FinanceViewModel.kt:1102` (`â‚¹` → `₹` in dummy-data note).
- **14 comment / section-divider mojibake instances** cleaned up (em-dashes `â€"` → `—`, box-drawing `â"€` → `─`). Cosmetic — these are inside `//` comments and `// ── Section ─────` dividers, no runtime impact, but the file is now valid UTF-8 throughout.
- **NEW `Utf8MojibakeDataIntegrityTest`** with 2 cases: (a) walks `src/main/`, parses every `.kt` and `.xml`, fails on any of 6 known mojibake byte sequences (`â‚¹`, `âš `, `ï¸`, `â€"`, `â€"`, `â"€`); (b) lockdown test that the detection patterns themselves are 3-char sequences (prevents future contributors from adding ambiguous patterns that'd false-positive on legitimate Latin-1 prose).

**Implementation note (Edit-tool limitation):** the standard Edit tool couldn't grab the `âš ï¸` byte sequence as `old_string` — the mojibake includes characters in the U+00A0–U+00FF range (Latin-1 supplementary) that the Edit tool's byte-level matching rejected. Switched to PowerShell with regex anchored on surrounding ASCII context (`{ Text("[^A-Za-z]+?This will DELETE`) for the warning-emoji replacements, and `[char]0x00E2 + [char]0x20AC + [char]0x201D` for the explicit-codepoint em-dash replacement. Worth remembering for any future mojibake / non-ASCII source fixes.

**Audit's PDF-date `20260525` claim not reproduced** — `ExportUtility` uses `LocalDate.now()` which toString()s as `2026-05-27` (ISO 8601 with dashes). Likely fixed in a prior commit; current behaviour matches the audit's acceptance criterion.

**Data-integrity gate state:** 112 → **114 tests across 10 classes** (+2 from `Utf8MojibakeDataIntegrityTest`), 0 failures. The gate-class count finally crosses double digits.

**5 of 9 P1 Sprint 2 clusters closed.** Remaining: C12-C15 (screen redesigns — sizable), C18 (Settings cleanup — medium), C21 (PDF/XLSX export quality polish: page breaks, group totals, embedded glyph fonts for ₹ and अ — depends partially on the C08 numeric-cell foundation).

### 2026-05-27 — 3.2.18 (C08 Stage 4 + C08 CLOSURE: PDF + XLSX export migration)

**Type:** Stage 4 of 4 for C08. Final commit closing the cluster. Two changes — one is plumbing, one is genuinely load-bearing for Excel spreadsheet workflows.

**Internal milestone:** `3.2.18` / `versionCode = 141`. No Play Console upload per release-cadence ADR. Data-integrity gate unchanged at 112 tests / 9 classes.

**PDF migration** (`ExportUtility.kt`): private `fmt(v)` helper that hardcoded `₹` and `.2f` precision → delegates to `CurrencyFormatter.detail(v, currencyCode, locale)`. Threaded `currencyCode` through all 3 PDF generators (`generatePDF` / `generateMoneyFlowPDF` / `generateLoanStatementPDF`) + the `generateMoneyFlowCSV` text export. Caller updates: `FinanceViewModel.exportToPDF` reads `currentProject.value?.currency`; `CustomerDetailScreen.kt:148` and `MoneyFlowScreen.kt:189/205` pass existing `currencyCode` derivation. Visible: PDF cards + tables now render Indian lakh-crore grouping for INR projects, Western grouping for others.

**XLSX load-bearing numeric-cell fix** (`ExcelExportUtility.kt`): pre-3.2.18 every cell emitted as `<c t="s"><v>idx</v></c>` (shared-string lookup) — even numeric amount fields. Excel/Sheets saw amounts as text → SUM returned 0, sorting was alphabetic, charts couldn't plot the column. The fix:
- NEW `Cell` sealed class with `Cell.Text(String)` and `Cell.Number(Double)`.
- Row model changed from `List<String>` to `List<Cell>`. Every sheet builder updated: amounts go through `n(value)` shorthand → `Cell.Number`; labels through `t(value)` → `Cell.Text`.
- `buildSheet` emits text via `<c t="s" s="N"><v>idx</v></c>` (shared-string lookup, header style for row 0); numbers via `<c t="n" s="2"><v>15000.00</v></c>` (raw double, US-locale decimal, references number-format style).
- `STYLES_XML` gained `numFmt numFmtId="164" formatCode="#,##0.00"` + corresponding cell-XF style (`s="2" = STYLE_NUMBER`).
- All 8 sheets updated: Accounts.Balance, Transactions.Amount, Lending.Principal/Rate/Paid, Debts.Principal/Rate/Paid, Investments.Invested/CurrentValue/Growth/Growth%, Loan Repayments.Amount, Debt Repayments.Amount. Metadata sheet stays all-Text (its values are timestamps/labels, not numbers).

**Design choice on XLSX currency display:** the cell format is locale-neutral `#,##0.00` (no currency symbol prefix). Users can apply lakh-crore grouping or currency symbol via Excel/Sheets cell formatting. The load-bearing change is that the underlying value is a number; format is the user's preference. Could later add currency-specific `numFmt` entries (one per supported currency code) — not blocking, logged as a follow-up if needed.

**Cumulative C08 totals:** 4 stages across 4 sub-versions (3.2.13 / .14 / .15 / .18). ~216 sites migrated app-wide. Plus the `formatAmount` helper deletion + `fmtK` helper deletion + 4 critical truncation fixes + 1 latent `AccountGrowthIndicator` bug fix. 33 new tests in `CurrencyFormatterDataIntegrityTest` pinning the contract. **C08 CLOSED at 3.2.18.** 4th P1 Sprint 2 cluster done.

**Process retrospective for C08:** the 4-stage approach worked well. Stage 1 alone (foundation + tests) shipped fast; Stages 2-3 leveraged parallel agents (4 + 6 respectively); Stage 4 was small enough to do serially. The user's mid-stream smoke iteration (3.2.16 / 3.2.17 EMI Calculator interruptions) didn't derail C08 progress — kept the multi-stage plan intact while servicing the immediate user need.

**Next:** C09 (UTF-8 in dialogs) is the next P1, but small. Then the bigger remaining work: C12-C15 (screen redesigns — sizable, like C04 was), C18 (Settings cleanup), C21 (PDF/XLSX polish — page breaks, group totals, glyph embedding). After all P1s: P2 clusters (C10/C11/C16/C17/C19/C20). Then C22 P3 backlog before first public release per cadence ADR.

### 2026-05-27 — 3.2.17 (EMI Calculator navigation entries — latent orphan fix)

**Type:** one-line nav-graph fix after the user's 3.2.16 smoke produced "where can I find" — investigation revealed the EMI Calculator screen has been a **navigation-graph orphan since 3.0+**: registered as a `composable(Screen.LoanCalc.route) { LoanCalculatorScreen() }` route but with **zero entry points anywhere in the UI**. Not in the drawer, not on the Reports hub, not as a deep-link from any other screen. 3.2.16's visual polish + rename made an unreachable screen prettier; this commit makes it reachable.

**Internal milestone:** `3.2.17` / `versionCode = 140`. No Play Console upload per release-cadence ADR. No test gate change.

**What landed:**
- `Navigation.kt` drawer "Finance Tools" section gained an `EMI Calculator` entry (Calculate icon) after the Investments entry.
- `ReportsHubScreen` gained an `onNavigateToLoanCalc` callback param (default `{}` for preview composability), wired in `Navigation.kt` to `navGated(Screen.LoanCalc.route)`.
- `ReportsHubScreen` body now renders an "EMI Calculator" `ReportLinkCard` tile on a new third row of the report grid.

**Audit confirms this was the only orphaned screen:** every other route (MoneyFlow / DebtPayoff / NetWorthH / InterestIncome / MonthlySummary / ProfitLoss reachable via Reports tab; Calendar via Lending screen; GlobalSearch via top-bar search; FlowWizard via Flow CTAs) has at least one entry point. **LoanCalc was the lone unreachable.**

**Process lesson:** when adding a new screen and registering it as a `composable` route, verify at-least-one navigation entry point exists before considering the feature shipped. Worth a follow-up Detekt rule or simple grep check in CI: every `Screen.X.route` referenced in a `composable(Screen.X.route)` registration should also appear in at least one `navController.navigate(...)` call or callback wire-up. Logged as INF backlog candidate.

### 2026-05-27 — 3.2.16 (EMI Calculator visual polish — partial of C12-C15 P1 backlog)

**Type:** out-of-band visual polish for `LoanCalculatorScreen.kt` (renamed to "EMI Calculator") after user flagged it as "not good to see and missing features." Sits in the C12-C15 P1 redesign backlog; not a full cluster closure (features still deferred per user "just visuals" scope choice). Interrupted C08 work — Stage 4 (PDF + XLSX exports) deferred to 3.2.17.

**Internal milestone:** `3.2.16` / `versionCode = 139`. No Play Console upload per release-cadence ADR. No test gate change.

**What landed (visual polish only):**
- **Rename** `LoanCalculatorScreen` → "EMI Calculator" header + side-drawer label. Route name `loan_calc` kept for backwards-compat.
- **Tenure row fixed** — number input takes remaining width, unit segmented row hugs natural width. Previously both `weight(1f)` cramped the segment labels.
- **Result cards** bumped `bodySmall` → `titleMedium`. Three side-by-side cards (Principal / Total Interest / Total Payment) were unreadable at 14sp; 16sp + card-padding tweak makes them headline-level.
- **`DatePickerField`** for loan + due dates (matches every other date input in the app). No more raw `DD-MM-YYYY` text-hint pattern.
- **Outstanding-as-of-Today section hidden behind `Switch` toggle** — `Already took this loan? Show accrued interest from loan date onward`. Default OFF; primary EMI-calc use case is planning a *future* loan, accrued-interest is the exception.
- **Amortization schedule `Yearly / Monthly` toggle**, defaulting to Yearly. 30-year loan goes from a 360-row wall of text to a 30-row scannable summary (year #, summed principal, summed interest, end-of-year balance).
- **Reset button** (`FilledTonalIconButton` in header action slot) clears all inputs.
- **Empty state** when no inputs: calculator icon + "Enter principal, rate, and tenure" hint.
- **Bottom padding** swapped to shared `FabBottomPadding` constant (C06 design system).

**Deferred (per user "skip features" scope choice — tracked as a future cluster):**
- Save as Debt (push computed loan into Debts tracker — primary CTA).
- Prepayment simulation (modal: prepay ₹X in month Y → recomputed interest savings + shortened tenure).
- Affordability % (EMI vs declared salary; needs a salary preference field).
- Compare two scenarios side-by-side.
- Share / export schedule (CSV / PDF).
- EMI breakdown pie chart (Principal vs Total Interest visual).

These remain in the C12-C15 P1 backlog and will land in a dedicated EMI-Calculator-features commit when the user calls for it.

**Pattern reinforced:** when a screen has "polish vs features" tension and the user picks polish-only, ship the polish fast (single commit, no agent fan-out needed for a single-screen redesign) and explicitly defer the features in the journal + CHANGELOG so the deferred work is auditable.

### 2026-05-27 — 3.2.15 (C08 Stage 3: Detail sweep across 18+ files via 6 parallel agents)

**Type:** Stage 3 of 4 for C08. Largest single sweep of the C08 plan: ~147 Detail-style call-site migrations across 18+ files via 6 parallel general-purpose agents grouped by domain. No file overlap between agents; each got a 3-5 file scope with explicit "don't re-touch Stage 2 sites" guidance.

**Internal milestone:** `3.2.15` / `versionCode = 138`. No Play Console upload per release-cadence ADR.

**Agent breakdown (all 6 BUILD SUCCESSFUL + 112 tests passing):**

| Agent | Domain | Files | Sites |
|---|---|---|---|
| A | Payment/Lending/Debt dialogs | PaymentDialog, LendingDialog, DebtDialog | 27 |
| B | Lending/Debt/Customer screen bodies | LendingScreen, DebtScreen, CustomerDetailScreen | 32 |
| C | Investment cluster + widget | InvestmentScreen, InvestmentDialog, PortfolioAnalyticsSheet, NetWorthWidget | 18 |
| D | Daily-use screens | BudgetScreen, GoalScreen, SpendScreen, MoneyFlowScreen, TransactionHistoryScreen | 36 |
| E | Calculators + repository | LoanCalculator, DebtPayoff, MonthlySummary, FinanceRepository | 24 |
| F | Misc cleanup | GlobalSearch, CollectionCalendar, ProfitLoss, Settings, SmartFlowWizard, DashboardComponents, DesignSystem | 10 + 1 deletion + 1 latent bug fix |

**Total: ~147 Detail migrations + ~10 ripple updates (Navigation/HomeScreen/HomeScreenModern/AccountStatement for signature changes) + 1 `formatAmount` helper deletion + 1 latent bug fix (`AccountGrowthIndicator` negative-growth display now uses en-dash instead of `₹-1,200`).**

**Notable patterns the agents established:**

1. **`currencyCode: String = "INR"` default param** on every shared Composable that previously took `currencySymbol`. Defaults preserve preview/test composability and existing named-arg call sites continue compiling without changes. Adopted by `PaymentDialog`, `LendingDialog`, `DebtDialog`, `LendingCard`, `DebtCard`, `PaymentItem`, `BudgetCard`, `InvestmentCard`, `PortfolioAnalyticsSheet`, `PortfolioBreakdownSheet`, `UpdateInvestmentValueDialog`, `ValuationHistoryDialog`, `AddInvestmentDialog`, `StepVerification`, `AccountGrowthIndicator`, `PLSection`, `DueEntryCard`, `CalendarGrid`, `TransactionItem`.

2. **Signature-change ripple** (Agent C): `PortfolioBreakdownSheet` had its `currencySymbol` param dropped entirely (truly unused after migration). Forced ripple updates in 3 call-site files (`HomeScreen.kt`, `HomeScreenModern.kt`, `Navigation.kt`) — Agent C handled the ripple as authorized cross-file plumbing within scope. Navigation gained `navProject` + `navCurrencyCode` derivations to pass through to dialogs.

3. **Smart Negative-vs-Detail branching** for sign-significant displays — `BudgetScreen.Remaining`, `MoneyFlowScreen.Net Flow`, `TransactionItem` amounts. Pattern: `if (value < 0) CurrencyFormatter.negative(...) else CurrencyFormatter.detail(...)`. Establishes app-wide consistency on en-dash use for negatives.

4. **Repository self-resolves currency** (Agent E): `FinanceRepository` note-generation sites use `dao.getProjectById(<entity>.projectId)?.currency ?: "INR"` to read currency at persistence time rather than threading currencyCode through UI → ViewModel → Repository. Clean layering, no caller-side plumbing.

5. **IME-safe input prefix swap**: text-field `prefix = { Text("₹") }` patterns swapped to `prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) }`. Currency-aware prefix while keeping raw text-field state intact. Hero typing-input symbols (e.g., LendingDialog's 32sp `Text("₹")`) treated the same way.

6. **Latent bug fix (Agent F)**: `AccountGrowthIndicator` rendered negative growth as `₹-1,200` (hyphen between symbol and digits from `String.format` itself). Now correctly `−₹1,200` (en-dash before symbol per audit). No tests exercised this path so the bug wasn't surfaced before; the migration uncovered + fixed it incidentally.

7. **`formatAmount` deletion**: the pre-existing `DesignSystem.formatAmount(amount, symbol)` helper had **zero callers** post-Stage 2 (Stage 2 migrations made it dead code). Deleted entirely along with the unused `java.util.Locale` import. Clean.

**Per-file `currencySymbol` deletion outcome:**
- **Deleted (fully migrated):** DebtScreen, CustomerDetailScreen, PortfolioAnalyticsSheet, SpendScreen, MoneyFlowScreen, TransactionHistoryScreen, DebtPayoffScreen, MonthlySummaryScreen, GlobalSearchScreen, CollectionCalendarScreen, ProfitLossScreen, SettingsScreen.
- **Kept (still used by Input field labels):** BudgetScreen, GoalScreen, LendingScreen, InvestmentScreen, InvestmentDialog, LoanCalculatorScreen. These use `currencySymbol` only in `OutlinedTextField` label text like `"Target Amount ($currencySymbol)"` — the field label, not the value, but it's input-affordance text where users want the bare symbol of their currency. Per the IME-safety carve-out.

**Visible UX changes worth flagging on smoke:**
1. **Indian lakh-crore grouping everywhere now** — not just dashboards. Every card body, dialog field, transaction row, calculator output, lending/debt detail.
2. **Decimals dropped to no-decimals on Detail surfaces** — LoanCalculator EMI, account-balance share-copy lines, per-day interest accrual all dropped from `.2f` → `.0f` per audit. If any specific surface needs decimals back, flag in smoke.
3. **All transaction rows show `−` en-dash for expenses, `+` for income** consistently across History, MoneyFlow, Spend, CustomerDetail.
4. **DashboardComponents growth indicator** now `−₹1,200` instead of `₹-1,200`.

**Implementation gotcha (logged for Stage 4):** Parallel agents touching shared composables (e.g., `TransactionItem`, `PortfolioBreakdownSheet`) can leave the Kotlin incremental compile cache in a stale state — agents 1-2 changed a signature, agent 3 sees the old cached signature and fails to compile its file. `./gradlew --stop && ./gradlew ... --rerun-tasks` resolves. Build was clean after one such stop+rerun.

**NetWorthWidget TODO (Agent C):** widget hardcodes `"INR"` with a `// TODO: widget should read default-currency pref from DataStore` comment — runs outside Composable scope, can't easily read user pref. Tracked as Task #22 for later.

**Data-integrity gate state:** unchanged at **112 tests across 9 classes**, 0 failures.

**Cumulative C08 progress:** Stage 1 (3.2.13: foundation + 33 tests) → Stage 2 (3.2.14: 52 highest-impact + 3 truncation fixes) → Stage 3 (3.2.15: 147 Detail + latent bug fix + helper deletion). **~202/257 sites migrated**, only Stage 4 (PDF + XLSX exports) remains for C08 closure.

**Next:** Stage 4 — `ExportUtility.kt` (6 PDF sites) + `ExcelExportUtility.kt` (8 XLSX sites). The XLSX migration is load-bearing because cells are currently stored as `t="s"` (strings) preventing Excel from summing/sorting numerically — has to switch to `t="n"` with a proper number-format style. This unlocks proper spreadsheet workflows.

### 2026-05-27 — 3.2.14 (C08 Stage 2: Hero/ListRow/Negative + truncation fixes — 4 parallel agents)

**Type:** Stage 2 of 4 for C08. Highest-impact call-site migrations to the `CurrencyFormatter` foundation shipped in 3.2.13. Four parallel agents grouped by domain to keep file ownership clean.

**Internal milestone:** `3.2.14` / `versionCode = 137`. No Play Console upload per release-cadence ADR.

**Agent grouping (no file overlap, executed in parallel, all 4 BUILD SUCCESSFUL first try):**

| Agent | Files | Migrations |
|---|---|---|
| A — Dashboard | HomeScreen, HomeScreenModern, NetWorthHistoryScreen, ReportsHubScreen | 14 direct Hero + ~12 transitive (via local `fmt(v)` helpers) + 2 Negative |
| B — Detail cards + truncation | AccountStatementScreen, CustomerDetailScreen, DebtScreen | 3 Hero + 3 CRITICAL truncation fixes (`CustomerDetailScreen.kt:199-201` was dropping decimal precision via `.toInt()`) |
| C — Interest + Analytics | InterestIncomeScreen, AnalyticsComponents | 3 ListRow + duplicate `fmtK()` helper deleted + 1 CRITICAL truncation fix |
| D — Negative + InvestmentDialog | BudgetScreen, InvestmentScreen, InvestmentDialog | 2 Negative + 1 CRITICAL truncation fix |

**Total sites:** ~34 direct migrations + ~12 transitive via helper functions + 6 truncation fixes = **~52 sites**.

**Visible UX changes worth flagging on smoke:**

1. **Indian lakh-crore grouping everywhere on Dashboard.** `₹241,663` → `₹2,41,663`. Applies to net worth, total assets/liabilities, account balances, customer outstanding, debt outstanding — anywhere the user has INR (or NPR/LKR/BDT) project currency.
2. **`.2f` precision dropped to `.0f`** on `NetWorthHistoryScreen` Current Net Worth + `AccountStatementScreen` Current Balance. Per audit design-system spec (`Hero = no decimals`). If user wants cents back, that's a design-system signature tweak — but audit was explicit.
3. **`−` en-dash on negatives** everywhere instead of mixed `-` / `(parens)` / inconsistent prefixes. Renders more legibly.
4. **No more silent decimal truncation** on 3 critical sites:
   - `CustomerDetailScreen.kt:199-201` — your `₹1499.50` outstanding now rounds to `₹1,500` for display (Detail style) instead of truncating to `₹1499`.
   - `AnalyticsComponents.kt:50` — spending breakdown amounts.
   - `InvestmentDialog.kt:62` — edit-amount text-field default.

**Key architectural patterns established:**

- **`currencyCode` lives alongside `currencySymbol`** in each migrated screen. Symbol is kept because Detail-style sites (Stage 3 scope) still use it. Stage 3 will prune `currencySymbol` once those last call sites migrate.
- **Compose components got `currencyCode: String = "INR"` default params** (`AnalyticsComponents.SpendingAnalyticsCard`, `InvestmentCard`, `BudgetCard`). Default preserves preview/test composability; existing call sites unaffected.
- **`fmt(v)` local helpers migrated once** in `HomeScreenModern` + `ReportsHubScreen` — single-point edit transitively migrates ~12 call sites per file. Pattern worth replicating in Stage 3 for screens with many similar sites.

**Implementation gotcha (logged for Stage 3):** First incremental build after Stage 2 edits hit a transient Hilt/KSP cache glitch (`NoSuchFileException` on `app/build/generated/ksp/prodDebug/java/dagger`). A `--rerun-tasks` clean rebuild resolved. Stage 3 worker may see this on first run; just rebuild.

**Data-integrity gate state:** unchanged at **112 tests across 9 classes**, 0 failures (pure call-site refactor — no logic change; `CurrencyFormatter` itself was already fully tested in Stage 1).

**Next:** Stage 3 — the Detail sweep (~189 sites across the dialog files, list rows, card body text). Plan to split across more parallel agents (Lending/Debt dialogs 60+ sites alone). Then Stage 4: PDF + XLSX export migration (XLSX numeric-cell fix is the load-bearing one — currently stored as strings, breaking Excel formulas).

### 2026-05-27 — 3.2.13 (C08 Stage 1: CurrencyFormatter foundation)

**Type:** Stage 1 of 4 for C08 (number formatting consistency across the app). This file lands the unified formatter that Stages 2-4 will sweep call sites into. No call sites migrated yet — by design, the foundation ships first so the migration has a stable target.

**Internal milestone:** `3.2.13` / `versionCode = 136`. No Play Console upload per release-cadence ADR.

**Survey context:** Explore agent counted **257 currency / number formatting call sites across 119 files** before this commit. Breakdown:
- 189 Detail (most common — list items, cards, dialogs)
- 24 Hero (dashboard cards)
- 21 ListRow (K/L abbreviation)
- 8 Input (text-field defaults)
- 6 PDF / 8 XLSX (with the XLSX strings-instead-of-numeric-cells bug breaking Excel summation)
- 3 Negative + 7 unclassified + 3 silent truncation violations
- 178 sites hardcode `₹` (multi-currency broken for those)

**What landed (Stage 1):**

NEW `app.fynlo.logic.CurrencyFormatter` with 6 styles:
- `hero(amount, currencyCode, locale)` — full amount, no decimals, locale-correct grouping. INR/NPR/LKR/BDT → lakh-crore (`₹2,41,663`); others → Western (`$241,663`).
- `detail(...)` — alias for Hero with intent-documenting name.
- `chartHero(...)` — Hero unless rendered string is ≥10 chars, then abbreviates via listRow.
- `listRow(...)` — always abbreviates. K/L/Cr for INR-family, K/M/B for others.
- `input(amount): String` — raw integer, no symbol, no commas. For text-field state.
- `negative(amount, ...)` — Hero with `−` (U+2212 en-dash) prefix; NOT ASCII hyphen.

NEW `CurrencyFormatterDataIntegrityTest` — 33 cases pinning the contract.

**Implementation note (load-bearing detail):** First two implementations of Indian grouping (`NumberFormat.getInstance(Locale("en","IN"))` then `DecimalFormat("#,##,##0", ...)`) both failed unit tests on the test JVM — `NumberFormat` because CLDR data for `en_IN` is unreliable across JDK / ICU versions; `DecimalFormat` because Java's pattern parser handles secondary-grouping inconsistently. **Final implementation hand-rolls the lakh-crore grouping** via string manipulation in `formatLakhCrore(absAmount, decimals)`. Pure Kotlin, no JDK pattern parser involved, identical output on every Android device and every test JVM. This is a documented gotcha for anyone tempted to "simplify" it back to `DecimalFormat`.

**Data-integrity gate state:** 79 → **112 tests across 9 classes** (+33 from `CurrencyFormatterDataIntegrityTest`), 0 failures.

**Next:** Stage 2 — parallel-agent migration of the 51 highest-impact call sites (Hero / ListRow / Negative + truncation fixes). Then Stage 3 (Detail sweep, 189 sites — multiple parallel agents grouped by domain). Then Stage 4 (PDF + XLSX export fixes).

### 2026-05-27 — 3.2.12 (C06 + C07 dual-cluster closure — FAB ownership + empty-state CTAs)

**Type:** dual-cluster closure. C06 (FAB overlap) and C07 (duplicate FAB on empty states) were both P1 Sprint 2 layout bugs that shared the same Scaffold-vs-screen FAB ownership question, so they closed as a unit per the audit's grouping note.

**Internal milestone:** `3.2.12` / `versionCode = 135`. No Play Console upload per release-cadence ADR. Data-integrity gate unchanged at 79 tests / 8 classes / 0 failures.

**C06 fix — system-wide FAB clear zone.** Explore agent surveyed all 10 scrolling screens; every one used `bottom = 100.dp` which the survey + user reports confirmed was provably not enough. Standard M3 FAB is 56dp + 16dp container margin ≈ 72dp minimum; 100dp left almost no breathing room and got eaten by gesture-nav insets on some devices.

NEW `app.fynlo.ui.theme.FabBottomPadding: Dp = 120.dp` constant in `DesignSystem.kt` with explanatory doc comment. Applied via:
- `LazyColumn(contentPadding = PaddingValues(bottom = FabBottomPadding))` — 8 screens (Budget, Goal, Invest, Lending, People, Debt, History, Recurring)
- `Spacer(Modifier.height(FabBottomPadding))` — 2 verticalScroll screens (Home, Spend); `HomeScreenModern` already was at 120dp

Future scrolling screens reuse the constant. The audit also suggested a Detekt lint rule that fails when a Scaffold-with-FAB screen lacks the safe padding — punted to INF backlog (not blocking).

**C07 fix — single unambiguous CTA on empty state.** Pre-fix, GoalScreen / BudgetScreen / RecurringScreen each rendered **three** Add affordances simultaneously on empty state:
- Scaffold's QuickAdd FAB (from `Navigation.kt`'s `floatingActionButton =` slot)
- Screen-level FAB (Goal, Budget) or header `+` IconButton (Recurring)
- Inline "Add First X" subtitle / button

Two-part fix:

(1) **`Navigation.kt:172` — `showFab` hidden list extended** by 3 routes (Budgets, Goals, Recurring). The Scaffold FAB opens a QuickActionMenu for transactions, which is the wrong intent on a Budget / Goal / Recurring page. With this change those screens own their own contextual Add affordance, no longer competing with the Scaffold's.

(2) **NEW shared `EmptyState(icon, title, subtitle, actionLabel, onAction)` composable** in `DesignSystem.kt`. Replaces the three bespoke inline empty states. Each screen wraps its screen-level FAB / header `+` in `if (list.isNotEmpty())` so on empty state the EmptyState's tonal-pill is the single unambiguous CTA. Once data exists, the FAB / `+` re-appears.

**Bonus fix — `CollectionCalendarScreen` back arrow.** Same `tint = Color.White`-on-light-surface invisibility bug as the RecurringScreen `+` fixed in 3.2.7. Replaced with `FilledTonalIconButton` (theme-aware). Logged in the 3.2.7 commit as a follow-up; folded here because it's the same icon-on-surface-without-affordance pattern that C07's design rule addresses.

**Three P1 Sprint 2 clusters now closed:** C04 (3.2.6), C06 + C07 (3.2.12). Six more P1 clusters remain (C08 / C09 / C12-C15 / C18 / C21). C08 (number formatting consistency across all screens) is the largest remaining UX win — likely warrants its own multi-stage cluster like C04.

**Pattern reinforced for future cluster work:** when a cluster has a clear "shared widget owns the consistency" angle (FabBottomPadding constant, EmptyState composable), extracting it to `DesignSystem.kt` first means future screens can adopt the rule without each one re-implementing it. Saves the lint-rule work and gives compile-time visibility of any screen that didn't migrate.

### 2026-05-27 — 3.2.11 (chip→better-widget moderate sweep applying the 3.2.10 design rule)

**Type:** app-wide pattern application driven by the user's request "can we apply [the dropdown pattern] where ever multiple chips in whichever screens is there to make app look better." An Explore agent surveyed all 13 chip use sites across the app and produced a categorized triage. The user picked the "Moderate" sweep size from a 3-option AskUserQuestion — 8 conversions, leaving the 5 large chip groups (category / account / person / horizontal filter rows) as-is because they're correctly chips per the established design rule.

**8 conversions landed:**

| Site | Before | After | Why |
|---|---|---|---|
| `RecurringScreen.kt` AddRecurring Income/Expense | `Row<FilterChip>` × 2 | `SegmentedButtonRow` | Matches AddTransactionDialog's same toggle |
| `SettingsScreen.kt` Theme | `Row<FilterChip>` × 3 | `SegmentedButtonRow` | 3-option mutex |
| `SettingsScreen.kt` Date Format | `Row<FilterChip>` × 3 | `SegmentedButtonRow` | 3-option mutex |
| `LendingScreen.kt` EMI Method | `Row<FilterChip>` × 3 | `SegmentedButtonRow` | 3-option mutex; `useReducing`/`useSimple` Boolean state encoding preserved |
| `LoanCalculatorScreen.kt` Tenure Unit | `Column<FilterChip>` × 2 (awkward stack) | `SegmentedButtonRow` (horizontal) | Replaces vertical stacking with natural horizontal toggle |
| `InterestIncomeScreen.kt` Range | `Row<FilterChip>` × 3 (6M/12M/24M) | `SegmentedButtonRow` | 3-option mutex |
| `TransactionHistoryScreen.kt` Type filter | `Row<FilterChip>` × 3 | `SegmentedButtonRow` | 3-option mutex |
| `TransactionHistoryScreen.kt` Dates toggle (in same row) | toggle `FilterChip` with leading icon | `FilledTonalButton` with leading icon | Semantic mismatch — toggle ≠ "pick one of N". M3 affordance for "tap to open panel" is a tonal button |
| `TransactionHistoryScreen.kt` Quick date presets | `FlowRow<FilterChip>` × 7 (wrapping to 2-3 rows) | `ExposedDropdownMenuBox` | 7 options in constrained panel — dropdown saves vertical space, always fits |

**Pattern consistency:** every `SegmentedButton` uses `icon = {}` per the 3.2.8 lesson — the default checkmark eats ~24dp of label width per segment for redundant signalling (selection is already carried by the filled background colour). Skipping this gives the labels their full natural width.

**Kept as chips** (left alone — they're correctly chips):
- `AddTransactionDialog` category + account pickers — large list + browse-and-pick
- `AddRecurringDialog` / `AddBudgetDialog` category pickers — same reason
- `LendingDialog` borrower / source / interest type pickers — dynamic person lists
- `MoneyFlowScreen` / `ReportsHubScreen` horizontal filter rows — `LazyRow<FilterChip>` is correct for screen-level filter bars

**Internal milestone:** `3.2.11` / `versionCode = 134`. No Play Console upload per release-cadence ADR. No test gate change (pure widget swap — 79 tests across 8 classes still pass).

**Smoke-test recommendation:** the user should re-open each of the 6 screens and verify the new widgets behave correctly. The mechanical part (selection state binding) is preserved in every conversion; only the visual presentation changes. If any conversion looks wrong, the per-site Edit is small enough to revert in isolation.

**Lesson for future cluster work:** when the user asks "apply this pattern wherever X" — survey first, triage with rationale, ask for sweep scope. Don't assume "apply everywhere" literally; the user usually wants principled consistency, not a blanket rewrite.

### 2026-05-27 — 3.2.10 (frequency picker → dropdown; design rule established)

**Type:** widget swap after three failed iterations. The Iteration history:
- **3.2.7**: `Row<FilterChip>` with `weight(1f)` + `labelSmall` — labels cramped.
- **3.2.8**: `SingleChoiceSegmentedButtonRow` with `icon = {}` — "Monthly" still clipped (~45dp per label inside AlertDialog width).
- **3.2.9**: `FlowRow<FilterChip>` — "Yearly" overflowed off the second line on the user's device width.
- **3.2.10**: `ExposedDropdownMenuBox` — always fits, no clipping math.

**Design rule established (codified in code comment and below):** for "pick one of N" pickers inside constrained-width containers (AlertDialog, narrow Card, side-by-side layout), prefer **dropdown** (`ExposedDropdownMenuBox`) over chips/segmented buttons. Reserve **chips** (`FlowRow<FilterChip>`) for "browse and pick" cases where seeing every option at a glance is the value — Category (12+ entries), Account type (5 entries with semantic icons). Reserve **segmented buttons** (`SingleChoiceSegmentedButtonRow`) for 2-3-option toggles on full-width screen scaffolds where space is plentiful (e.g., the Income/Expense toggle at the top of AddTransactionDialog — only 2 options, plenty of room).

Matches the C04 Stage 3 currency picker pattern in SettingsScreen for visual consistency across the app.

**Internal milestone:** `3.2.10` / `versionCode = 133`. No Play Console upload per release-cadence ADR. No test gate change (pure widget swap).

**Audit follow-up logged:** the user asked "can we apply where ever multiple chips in whichever screens is there" — i.e., extend the dropdown pattern app-wide. Surveying chip use across all screens is the next step; not every chip group needs converting (Category picker stays as chips per the design rule above) but several narrow-dialog pickers likely do. Tracked separately so the audit retains the "what is this commit" / "what is the broader sweep" distinction.

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
---

## Journal Addendum - 2026-06-20 Internal Testing Knowledge Hub

Created `docs/release-handoff/2026-06-20-internal-testing-knowledge-hub.md` as the durable handoff for the long pre-launch session covering internal testing 3.2.105, investment funding safety, reset/cloud sync cleanup, ledger traceability, PDF/export fixes, template-lock UI revamp, Play Console setup, screenshots, store assets, and manual smoke status.

Future AI sessions must read that handoff before touching release prep, Play Console support, Firestore sync, accounting traceability, PDF/XLSX exports, app icon/assets, screenshots, or the template revamp. The file records what was fixed, what must not regress, and what remains as non-blocking follow-up.
---

## Journal Addendum - 2026-06-23 Google Sign-In Internal Testing

Internal testers reported Google Sign-In failing with the login-screen developer-error message. Diagnosis points to Play App Signing SHA fingerprints missing from Firebase/Google OAuth for package `app.fynlo`. Hardened `GoogleSignInHelper` to use the generated `default_web_client_id` resource with fallback, clarified the login error text, and recorded the Play Console > App integrity > Firebase SHA-1/SHA-256 routine in the internal testing knowledge hub.

## Journal Addendum - 2026-06-23 Play App Signing Fingerprints

Play Console did not show App integrity/App signing in the visible sidebar. Downloaded the Play-generated Signed, universal APK (`229.apk`) from App bundle explorer and extracted the Play App Signing certificate using Android SDK `apksigner verify --print-certs`. Firebase must add the V3.0 Signer SHA fingerprints: SHA-1 `D8:31:51:86:FD:1E:11:6A:AF:46:7C:9F:88:5C:82:B4:FD:B0:89:95` and SHA-256 `A1:C6:ED:60:B9:13:CC:5D:F8:B9:41:43:BE:3C:66:88:59:88:8E:37:3A:D9:C1:B5:BF:97:93:84:A2:2A:7F:2A`. Ignore the Source Stamp Signer certificate for Firebase OAuth.

---

## Journal Addendum - 2026-06-23 Security Hardening Roadmap

Safe-now hardening completed: FileProvider sharing is restricted to generated export files under `cache/exports/`. Report, CSV, P&L, Money Flow, Monthly Summary, and Loan Statement shares now create files through `ExportUtility.exportCacheFile(...)`, so the app no longer exposes the broader cache, files, or external-cache roots through content URIs.

Security review status: focused review found no launch-blocking issue. The official Codex Security deep scan workspace was opened, but its deep preflight could not prove worker-slot capacity, so the complete deep-scan artifact was not finalized.

Phase 2 hardening after Play listing/internal-testing stabilization:
- Evaluate full local ledger DB encryption, such as SQLCipher or equivalent Room-compatible storage, if stronger protection than Android app sandbox plus PIN is required.
- Tighten Firestore security rules with schema and field validation once sync coverage and account deletion/reset behavior are stable.
- Consider explicit privacy controls for Crashlytics/Analytics collection if Play/Data Safety positioning changes.
- Upload native debug symbols for Play Console crash/ANR diagnostics when practical.

---

## Journal Addendum - 2026-06-24 Visible Brand Rename

User requested changing the visible app/store/legal/export brand from `Fynlo` to `Fynlo Ledger` because another Play Store app already uses the Fynlo name. Completed the visible rename across app labels, onboarding/login, About, settings/help copy, report/export filenames and headers, backup/error copy, localized app labels, and public legal docs.

Important boundary: package name, namespace, database names, shared-preference keys, class names, and other stable technical identifiers remain unchanged. The app still ships as package `app.fynlo`; do not rename this unless a future Play migration explicitly requires a brand-new app listing.

Next Play upload for this rename line uses `versionCode = 230` and `versionName = "3.2.106"`. Future Play listing text, screenshots, feature graphic, and tester-facing release notes should use `Fynlo Ledger`.
## 2026-06-24 - Android test plugin analysis

- Ran the `test-android-apps` code-side QA pass after the phone was disconnected from adb.
- Unit tests and prod-debug Kotlin compilation passed.
- Lint passed after fixing the debug-only network security config so user-installed certificates live under `debug-overrides`, while release remains HTTPS-only and system-certificates-only.
- Device/emulator UI and performance capture still require a connected adb phone or an Android emulator in PATH.

## 2026-06-24 - Phone QA date picker polish

- Continued the phone-connected Android QA pass on device `3C15CA0055F00000` with the production package `app.fynlo`.
- Installed the latest prod-debug build and verified the visible brand as `Fynlo Ledger` across dashboard, loans, invest, reports, expenses, search, drawer, settings, and core add/edit sheets.
- Found one polish issue in the investment add sheet: the shared date field could pull up the keyboard instead of behaving like a picker-only control.
- Updated `DatePickerField` so date inputs are read-only picker controls. Tapping the field opens the Material date picker and clears focus, preventing accidental keyboard popups across investment, transaction, loan, budget, recurring, and other date-based forms.

## 2026-06-24 - Play asset refresh and review prompt gate

- Installed the developer build on connected phone `3C15CA0055F00000`: package `app.fynlo.dev`, versionCode `230`, versionName `3.2.106-dev`.
- Found a store-polish issue while capturing screenshots: the Play review prompt could appear on the login screen before the user entered the app.
- Fixed `Navigation.kt` so the review prompt can only show after login and PIN unlock. This prevents first-launch/sign-in and Play screenshot capture from being interrupted.
- Regenerated `play_store_assets/feature_graphic_1024x500.png` with visible `Fynlo Ledger` branding and kept the existing symbol-only 512x512 app icon.
- Refreshed the Play phone upload screenshots in `play_store_assets/screenshots_upload/` as a clean six-file set: sign-in, dashboard, loans, invest, reports, and expenses. Removed stale/raw upload screenshots so the folder does not mix old `Fynlo` assets with `Fynlo Ledger`.
- Tablet screenshots were not generated in this pass because only phone AVDs (`Pixel_6`, `Pixel_7`, `Pixel_9`) exist locally and the `avdmanager` tool is not installed. Create a tablet AVD or connect a tablet before claiming real 7-inch/10-inch tablet screenshots.

## 2026-06-25 - Debt Edit Destination Account Delta Fix

- Manual internal-testing smoke found a debt edit bug: a debt created into an account, such as Rs. 1,00,000 into Business Investment, could be edited to a larger principal while the Debt page changed but the receiving account stayed at the old credited amount.
- Fixed `FinanceRepository.updateDebt(...)` so the linked `Debt Received` transaction is the source of truth for the destination account. Editing a debt now updates that linked transaction and applies only the principal delta to the original destination account, using `toAcctId` when available.
- New debt creation now stores `toAcctId` on the linked `Debt Received` transaction and credits the destination account through the same id-aware account delta path used by other money actions.
- Added regression coverage in `MoneyActionIdempotencyDataIntegrityTest`: editing a debt from 100 to 675 credits only +575 to the original Business Investment account and preserves the linked transaction account id.
- Verification passed: focused money-action integrity test, `:app:compileProdDebugKotlin`, and full `:app:testProdDebugUnitTest`.

## 2026-06-26 - Editable Money Source/Destination Traceability

- User raised a trust-critical edit scenario: a money action saved to the wrong account must be correctable later without manual balance surgery.
- Lending edit now exposes `Lent from` and routes through `updateBorrowerWithSource(...)`. The repository reverses the original linked `Lending` transaction source account, applies the corrected source account debit, and updates the linked transaction `fromAcct/fromAcctId`.
- Debt edit now exposes `Received into Account` and routes through `updateDebtWithDestination(...)`. The repository reverses the original `Debt Received` destination credit, applies the corrected destination credit, and updates the linked transaction `toAcct/toAcctId`.
- Account-funded investment edit now exposes funding source correction. The repository reverses the original investment funding account, debits the corrected account, preserves current value, and updates the linked funding transaction.
- Dashboard due-soon nudge now opens the collection calendar instead of the generic loans hub. The calendar now includes both lending dues and debt dues, with overdue red, today green, near-due amber, and later upcoming blue; tapping rows opens borrower or debt detail appropriately.
- Zero-interest hand loans are already included in the active lending filter when unpaid and not settled/written off. If a specific hand loan is missing on-device, inspect that row's saved `status`, `paid`, and `amount` values rather than assuming the hand-loan list excludes it.
- Regression coverage added in `MoneyActionIdempotencyDataIntegrityTest` for correcting borrower source, debt destination, and investment funding source. Verification passed with focused money-action tests, `:app:compileProdDebugKotlin`, and full `:app:testProdDebugUnitTest`.

## 2026-06-26 - Date Picker and Form Selection Polish

- Phone feedback found the Material date picker could become slow when jumping across years in loan, debt, contact, investment, budget, and related sheets.
- `DatePickerField` now bounds the selectable year range to current year minus 80 through current year plus 50. This keeps practical finance dates available while avoiding the huge year list that made some devices janky.
- Dynamic selection chips in the lending form were replaced with dropdowns for borrower and source account. This keeps long contact/account lists from wrapping into crowded rows.
- Investment funding source selection was changed from three stacked pill controls to a single dropdown, matching the calmer form language used by debt/account pickers.
- Launcher label check: production resources already resolve `app_name` to `Fynlo Ledger`; dev resources resolve to `Fynlo Ledger Dev`. If the launcher still shows only `Fynlo`, treat it as launcher truncation/cache or an older installed build, not a source label regression.

## 2026-06-26 - Debt-Funded Investment Journal Repair

- Internal testing found a real balance-drift explanation: an old debt-funded BBS investment trace was saved as a `Transfer` from Business Investment to Family Cash. That moved Rs. 2,00,000 even though the row was only supposed to explain the investment funding source.
- Debt-funded investment trace rows are now journal-only: future existing-debt/new-loan investment funding creates `Info` transactions with `journal_only`, no source/destination account columns, and a direct `ref` back to the investment.
- Startup now runs `repairDebtFundedInvestmentTransferTraces()`. Legacy debt-funded investment `Transfer` rows are converted to journal-only traces and any accidental real-account movement is reversed once. This is expected to correct the observed Family Cash 29,90,000 -> 27,90,000 and Business Investment 17,25,000 -> 19,25,000 case after the app launches.
- Transaction History now displays per-row account balance impact, for example `Family Cash: Rs. before -> Rs. after`, so users can trace previous balance and next balance directly from the history list. Privacy mode hides these values.
- Ledger Health now raises a critical `Debt receipt amount mismatch` when a debt principal and its linked `Debt Received` transaction amount diverge. This exposes legacy rows like a debt edited to Rs. 6,75,000 while its original receipt row is still Rs. 1,00,000. It is deliberately reported instead of silently rewriting old cash history.
- Verification passed: `:app:compileProdDebugKotlin` and `:app:testProdDebugUnitTest`.

## 2026-06-26 - Debt Receipt and Stored Balance Drift Auto-Repair

- Internal testing confirmed the remaining live-data discrepancy: Kalyani Ammamma's debt row was Rs. 6,75,000, but the linked `Debt Received` transaction was still Rs. 1,00,000. The missing Rs. 5,75,000 was therefore not visible in Business Investment because the account ledger was replaying the stale receipt amount.
- Startup now runs `repairDebtReceiptAmountMismatches()`. It updates the linked receipt transaction to match the debt principal and applies only the missing delta to the original destination account, using `toAcctId` when available.
- Startup then runs `repairAccountBalanceDriftFromLedger()`. It uses each account's CREATE audit as the opening balance and replays current transaction rows, ignoring `Info` / `journal_only` traces, to correct stored account-balance drift.
- Verified on connected production package `app.fynlo`: Business Investment moved to Rs. 25,00,000, Family Cash moved from Rs. 29,90,000 to Rs. 27,90,000, HDFC corrected from Rs. 39,898 to Rs. 39,906, and Kalyani's linked receipt now shows Rs. 6,75,000.
- Audit trail records each repair with before/after values and reasons: the Kalyani receipt amount repair, Business Investment +Rs. 5,75,000, Family Cash -Rs. 2,00,000, and HDFC +Rs. 8.
- Regression coverage added for both failure shapes in `MoneyActionIdempotencyDataIntegrityTest`: debt receipt mismatch repair and stored account drift repair. Verification passed with `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, and installs to both prod/dev debug apps.

## 2026-06-26 - Investment Journal Trace Relink and Source Correction

- Follow-up phone inspection showed Business Investment itself was balanced: the seven `Debt Received` rows sum exactly to Rs. 25,00,000. The remaining trust issue was traceability: one BZA Rs. 75,000 debt-funded investment journal row was attached to the Rs. 2,50,000 BZA investment, leaving the Rs. 75,000 investment without its own source trace.
- Added startup repair `repairDebtFundedInvestmentJournalTraceRefs()`. It only relinks or creates `Info` + `journal_only` investment source traces; it does not change account balances. It is wired after debt-funded transfer neutralization and before debt receipt/account-drift repairs.
- Ledger Health now flags investment source trace gaps: missing investment traces, amount mismatches, duplicate investment traces, and any debt-funded investment trace that still moves real account balances.
- Investment edit now supports correcting the funding source between account-funded and existing-debt-funded. Account-funded corrections reverse the old account movement and debit the corrected account; existing-debt corrections reverse any old account deduction and store a journal-only debt-funded source trace.
- Valuation History dialog contrast was tightened so titles, values, date labels, and chart labels use readable surface/primary colors instead of faint outline tones.
- Verified on connected production package `app.fynlo`: Business Investment remains Rs. 25,00,000, its seven receipt rows sum to Rs. 25,00,000, all listed debt receipts match their debt principal, and BZA now has separate Rs. 2,50,000 and Rs. 75,000 journal traces linked to the correct investment ids.
- Regression coverage added for debt-funded investment trace relinking and account-funded-to-debt-funded investment source correction. Verification passed with `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, prod debug install, and dev debug install.

## 2026-06-26 - Internal Testing AAB 3.2.107

- Bumped Play upload version to `versionCode = 231` and `versionName = "3.2.107"` after the investment journal trace repair was committed and pushed.
- Purpose of this release: deliver the latest ledger trace hardening to Google Play internal testers. Previous direct phone installs do not update testers unless a new AAB is uploaded.

## 2026-06-29 - Account Transfer Neutrality and Loans Overview Split

- Account-to-account transfers are now treated as balance-sheet-only movements with a dedicated `Account Transfer` category. They debit the source account, credit the destination account, and keep audit `amountDelta` at zero so net worth and P&L do not move.
- Transfer creation paths now write `Account Transfer` instead of the generic type literal `Transfer`; the transaction validator preserves legacy transfer rows by normalizing `type=Transfer/category=Transfer` into `Account Transfer` instead of `Uncategorized`.
- Ledger Health now raises a critical issue if a transfer routes to the same account on both sides, because that should never be allowed as a real movement.
- Loans overview cards were changed to show only the requested overview split: `Total Borrowers` / `Total Debtors`, `Principal`, and `Interest`. Individual borrower/debtor detail pages were intentionally left unchanged.
- Added regression coverage for account transfers: source balance decreases, destination balance increases by the same amount, combined cash remains unchanged, and the saved row is categorized as `Account Transfer`.

## 2026-06-29 - Account Statement Ordering and Legacy Trace Repair

- Production-phone inspection found the current Family Cash balance was mathematically explained by the local ledger: opening audit balance Rs. 1,16,15,000 minus Rs. 1,14,50,500 of outgoing Family Cash rows equals Rs. 1,64,500.
- The trust issue was visibility: account statement rows were sorted only by date, so same-day activity could appear confusing, and several legacy restored transactions carried account names without immutable account ids.
- Transaction ordering is now centralized in `TransactionOrdering`: newest-first means business date descending, then creation/update timestamp descending, then id descending.
- Account detail statements now use that ordering, show the transaction date/time, and reuse transaction-history balance-impact rows so before/after account movement is visible in the account ledger itself.
- Startup now runs `repairTransactionAccountIds()`. It backfills missing `fromAcctId` / `toAcctId` on legacy name-only transaction rows when the account name still exists. This repair does not move money or change balances.
- Regression coverage added for stable transaction ordering and for the account-id repair being balance-neutral.

## 2026-06-29 - Dashboard Transfer Entry Point

- User could not find account transfer because Dashboard quick actions still showed only Expense, Income, Lend, and History.
- Added a visible `Transfer` quick action to Dashboard.
- The existing transfer dialog now supports two modes: account-specific transfer with the source preselected, and dashboard-started transfer where the user chooses both source and destination accounts.
- Transfer still routes through `transferBetweenAccounts(...)`, so it remains balance-sheet-only: one account decreases, another increases, and net worth does not change.

## 2026-06-29 - Expenses Add Flow Locked to Expense

- Phone feedback showed the Expenses screen `+` dialog still exposed both Expense and Income because it reused the generic Add Transaction dialog.
- `AddTransactionDialog` now supports `allowTypeSwitch = false`. When disabled, it locks the dialog to the caller's `initialIsIncome` value and hides the Expense/Income segmented selector.
- Expenses now opens the dialog as `Add Expense`, locked to expense-only entry, and always shows `Expense added` feedback.
- Product rule for loan grace periods: do not solve waived extra-days interest by recording fake interest payments. A future safe implementation should add a distinct non-cash interest-waiver adjustment, so cash, collected interest income, outstanding interest, and audit history remain separate and trustworthy.

## 2026-06-29 - Interest Waiver / Grace Period Ledger Adjustment

- Loan and debt grace-period interest is now modeled as `interestWaived`, a non-cash adjustment stored separately from real repayments.
- Waiving interest reduces only unpaid interest outstanding. It does not change account balances, does not create payment rows, does not increase paid totals, and does not count as interest income or interest expense.
- Borrower and debt detail screens expose a `Waive Interest` action only when unpaid interest exists. Payment dialogs show already-waived interest in the outstanding summary so users can see why the remaining due is lower.
- Room schema migrated to version 28, Firestore sync reads/writes the new field, and audit trail records `WAIVE_INTEREST` with zero `amountDelta`.
- Ledger Health now warns if imported/cloud data ever contains a negative waiver or a waiver larger than unpaid interest. This is a guardrail only; it does not silently rewrite balances.
- Regression coverage confirms borrower and debt waivers leave cash and payment rows untouched while reducing receivable/liability calculations.

## 2026-06-29 - Production User Language and Dev-Only Tools

- Production-facing wording was simplified so regular users see plain terms instead of implementation terms: `Ledger health` -> `Book check`, `Audit trail` -> `Money action history`, `Google Firestore` -> `Google cloud backup`, `P&L` -> `Profit & Loss`, and Crashlytics error text -> `error report`.
- The Settings developer/QA panel and related diagnostic-only backup tools are now gated to the dev flavor only: `BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev"`. This prevents `prodDebug` phone testing from showing developer tools that Play Store users will never see.
- Keep this rule for future changes: production UX copy should explain what the user can do and what will happen to their money records; avoid Firebase, Firestore, Crashlytics, schema, migration, journal-only, sync-pull, and similar internal terms in visible user text.
- Verification passed with `:app:compileProdDebugKotlin` and `:app:testProdDebugUnitTest`.

## 2026-06-29 - Dashboard Freshness and Book Confidence Pass

- Dashboard `Total net worth` freshness now uses the latest money activity timestamp across accounts, transactions, borrowers, debts, and investments, with recalculation time only as a fallback. This fixes stale labels like `Last updated 7 hours ago` after newer money activity.
- Dashboard now shows a compact book-confidence card with Book Check score, cloud backup state, and last activity. Tapping it routes users to Settings where Book Check lives.
- Dashboard nudges now include Book Check warnings when missing links or duplicates exist, so data-quality issues are not hidden away in Settings.
- Add Transaction now warns when the same type/category/amount/account/date already exists. It does not block saving; it gives a human warning before accidental duplicate entry.
- Book Check issue rows now include `Try this` guidance so users know whether to open History, the loan/debt record, the investment, or the original account-linked record.
- These changes are visibility and guardrail-only. They do not change balance math, Firestore data shape, or ledger repair behavior.

## 2026-06-29 - Combined Phase 2 Roadmap Memory

The user approved the combined future roadmap below. Do not treat these as completed unless a later journal entry says they were implemented and verified.

### Technical / accountability roadmap

1. Monthly close / lock period for finished months.
2. Undo window for add/edit/delete/payment actions.
3. In-app What's New / How to Use section.
4. Advanced category rules and auto-categorisation.
5. Ledger timeline showing before/action/after money movement.
6. Monthly review screen explaining what changed.
7. Smart mismatch fixer from Book Check.
8. Backup health center for cloud/local state and conflicts.
9. Export preview before PDF/Excel save.
10. Recurring reminders for salary, rent, EMI, subscriptions, dues, and debt payments.
11. Contact ledger per person.
12. Attachment support for receipts/proofs.
13. Balance reconciliation wizard.
14. Role/privacy/reviewer mode.
15. Bank/data import assistant with review-before-save.
16. Personal finance insights.
17. Goal-based planning.
18. Loan grace/waiver history improvements.
19. Offline sync conflict resolver.
20. Dev-only release checklist screen.

### UI/UX roadmap

1. Unify all money dialogs into one premium bottom-sheet shell.
2. Replace crowded chips with searchable dropdowns where lists can grow.
3. Separate Expense and Income dialog experiences.
4. Give account transfer its own clear dialog.
5. Improve payment dialogs with principal/interest/waiver/remaining clarity.
6. Standardise confirmation dialogs.
7. Standardise card density across dashboard, lists, detail pages, and dialogs.
8. Improve empty states with clear next actions.
9. Make Book Check more guided and actionable.
10. Standardise icons.
11. Improve validation text under disabled buttons.
12. Improve global search UX and keyboard spacing.
13. Polish reports export UX.
14. Regroup Settings into clearer sections.
15. Add micro-feedback everywhere.

### Dashboard Book Check direction

- Keep one compact persistent Book Check / book-confidence card on Dashboard.
- Avoid showing both a confidence card and an intrusive top dialog for the same non-critical warnings; that duplicates attention and can make Dashboard feel alarming.
- If there are serious issues, show a small high-priority nudge/card that opens Book Check. Use a modal dialog only when the user taps it or when a truly destructive/unsafe action is about to happen.

## 2026-06-29 - Phase 2 Safe UI Foundation Pass

- Added a shared `FynloChoiceDropdown` component for premium form pickers.
- Replaced crowded category/source chips in Add Transaction with dropdown fields. This is UI-only: transaction creation, account balance movement, Firestore sync, and duplicate warning behavior remain unchanged.
- Replaced category chips in Budget and Recurring dialogs with the same dropdown pattern.
- Added Settings -> App Info -> `What's new & how to use`, covering money-action impact, Book Check, account transfers, backups, and history/search.
- Upgraded the Settings `Export Data` dialog from an older centered alert to the shared bottom-sheet form style and added a preview block before file save.
- Added a dev-flavor-only Settings -> Developer -> `Release Checklist` dialog covering compile, tests, install, smoke, Book Check, reports, AAB, and Play Console handoff.
- Verified after each batch with `:app:compileProdDebugKotlin`.
- Still deferred from Phase 2: monthly close, undo window, smart mismatch fixer, attachments, reconciliation wizard, reviewer mode, offline conflict resolver, and any feature that needs schema/sync semantics plus phone smoke.

## 2026-06-30 - Balance-Safe Edit Preservation and Phone Install

- Fixed a high-risk edit-path bug in loan, debt, and investment dialogs: editing a visible field now preserves hidden accounting fields instead of rebuilding a partial record. This protects paid principal, paid interest, waived interest, default/frozen-interest state, realized/withdrawn investment values, timestamps, and trace fields.
- Loan/debt/investment source corrections still route through repository methods that reverse the old funding movement and apply the new one. UI dialogs now pass complete records into those methods.
- Verified `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, and dev debug compile.
- Installed both phone variants on connected device `3C15CA0055F00000`: production `app.fynlo` and developer `app.fynlo.dev`, both versionCode `231`; version names `3.2.107` and `3.2.107-dev`.
- Phone smoke confirmed production dashboard quick actions include `Transfer`, Dashboard freshness shows `Updated 0 minutes ago`, Expenses plus opens an `Add Expense`-only dialog with no Income/Transfer switch, and developer intro opens as `Fynlo Ledger`.
- Recent device log scan showed no fresh fatal crash lines for the launched app.

## 2026-06-30 - Optional Safe Hardening: Book Repair Action

- Added a user-visible `Run safe repair` action inside Settings -> Book check.
- The repair runs only already-proven cleanup paths: deleted-row residue cleanup, debt-funded investment trace neutralization/relinking, debt receipt amount repair, transaction account-id backfill, account balance drift reconciliation, paid-total rebuild, and final balance recalculation.
- The result message reports whether linked rows or account balances changed. It does not invent new money actions or make guess-based corrections.
- This completes the first safe slice of the `Smart mismatch fixer` roadmap. A fully automatic mismatch fixer, monthly close, undo window, attachments, and offline conflict resolver remain later-stage work because they need deeper schema/sync semantics and stronger phone smoke.
- Verified `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, and install of both production/developer debug variants to connected device `3C15CA0055F00000`.

## 2026-06-30 - Safety Hardening: Monthly Close, Undo, Proofs, Sync Conflicts

- Added Room schema version 29 with `monthly_closes`, `undo_actions`, `proof_attachments`, and `sync_conflicts`.
- Monthly close now blocks money-changing writes dated inside a closed month, including transactions, loans, debts, investments, withdrawals, and interest waivers.
- Settings -> Backup & Export now exposes `Monthly close`, `Undo last money action`, `Sync conflict review`, and `Proof records`.
- Undo is intentionally narrow and safe: it reverses recent transaction add/edit/delete actions inside a 10-minute window. It does not yet undo compound loan/debt/investment cascades.
- Proof support is now persisted, backed up/restored, and synced as metadata. Per-record attachment pickers are still a future UI layer.
- Sync conflict capture records local-vs-cloud divergence for account and transaction rows instead of silently overwriting without trace. The first UI lets the user review and mark conflicts as reviewed.
- Backup/restore and reset paths now include monthly closes, proof links, undo records, and conflict records so the new safety tables do not leave stale state behind.
- Verified `:app:compileProdDebugKotlin` and `:app:testProdDebugUnitTest`.
