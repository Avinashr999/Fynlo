# Changelog

All notable changes to Fynlo are documented here.

## [3.2.6] - 2026-05-27 *(Development milestone — C04 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C04 — smart defaults across picker surfaces.** First P1 Sprint 2 cluster closed. Three surfaces wired to `RecentlyUsedTracker` (Stage 1 data layer from 3.2.5's predecessor session), plus the AddTransactionDialog Stage 2.5 gap. Five-part fix list:
  1. **AddTransactionDialog Stage 2.5.** `LaunchedEffect(isIncome)` is now a three-case `when`: (a) no recency → both fields blank; (b) recent in `Categories.INCOME` / `Categories.EXPENSE` → chip pre-selected, `customCategory` cleared; (c) recent is a Custom-typed string (e.g., "Charity") → `selectedCategory = "Custom"` AND `customCategory = recent` together, so the typed text re-renders below the chip row. Stage 2 (already in 3.2.5's tree) silently dropped case (c); this closes the gap.
  2. **RecurringScreen.AddRecurringDialog → typed chip picker.** Free-text `OutlinedTextField` replaced with the C05-pattern `FlowRow` of `FilterChip`s from `Categories.INCOME` / `Categories.EXPENSE` + trailing "Custom" chip + conditional custom-name input. Same three-case `LaunchedEffect` prefill logic. `viewModel.rememberLastRecurringCategory(isIncome)` reads on open; `viewModel.recordRecurringCategory(r.type == "Income", r.category)` writes on submit. Keyed off `RecentlyUsedTracker.FormIds.ADD_RECURRING` so the Recurring slot is isolated from one-off AddTransaction. Split by type (income/expense) so a Salary recurring doesn't bleed into Expense recurring.
  3. **BudgetScreen.AddBudgetDialog → chained-fallback heuristic prefill.** The audit's BudgetScreen criterion is **highest-spend-uncapped**, not pure recency. `LaunchedEffect(Unit)` resolves: (a) `viewModel.suggestBudgetCategory()` — highest-spend EXPENSE category not yet budget-capped; (b) if null, `viewModel.rememberLastBudgetCategory()` — pure-recency fallback for when every spent-on category is already capped; (c) if still null (fresh install), blank. Category input also converted from `LazyRow` + free-text to a `FlowRow` of `FilterChip`s + "Custom" trailing chip + conditional custom-name input — same C05 pattern as AddTransaction.
  4. **SettingsScreen.kt currency picker → locale-default-first + "Recently used" sub-group.** `ExposedDropdownMenuBox` kept as the widget; menu content extended. Selection initialised via `viewModel.rememberLastCurrencyOrLocale(LocalConfiguration.current.locales[0])` — recency wins if any, otherwise `Currency.getInstance(locale).currencyCode` (e.g., `"INR"` for `en_IN`, `"USD"` for `en_US`, `"EUR"` for `fr_FR`), with `"INR"` as final fallback for locales without a country. Menu renders a conditional "Recently used" sub-header + ≤5 rows + `HorizontalDivider` above the full alphabetical list from `CurrencyUtils.supported`. Hidden entirely when `observeRecentCurrencies()` emits an empty list (fresh install). Each `DropdownMenuItem` writes to BOTH `UserPreferences.setDefaultCurrency` (canonical pref, unchanged) AND `viewModel.recordCurrency(code)` (recency layer).
  5. **NEW `app.fynlo.data.BudgetSuggestion`** — pure `object BudgetSuggestion.suggest(cappedCategories: Set<String>, expenseAnalytics: Map<String, Double>): String?`. The Budget heuristic lives here as a pure function so it can be unit-tested without a Hilt + Room + StateFlow harness. `FinanceViewModel.suggestBudgetCategory()` is a thin delegate over it.

### Added
- **`app.fynlo.data.BudgetSuggestion`** (pure-function `object`) — see Fixed above.
- **9 new methods on `FinanceViewModel`** (all keyed off `RecentlyUsedTracker` slots that already existed):
  - `rememberLastRecurringCategory(isIncome) / recordRecurringCategory(isIncome, category)` — `FormIds.ADD_RECURRING`.
  - `suggestBudgetCategory()` — delegates to `BudgetSuggestion.suggest(...)`.
  - `rememberLastBudgetCategory() / recordBudgetCategory(category)` — `FormIds.ADD_BUDGET`.
  - `rememberLastCurrencyOrLocale(locale = Locale.getDefault()) / recordCurrency(code)` — `FormIds.SETTINGS_CURRENCY`. Locale-fallback path uses `runCatching` to defend against locales without a country (some emulators).
  - `observeRecentCurrencies(n = 5): Flow<List<String>>` — reactive top-N for the dropdown's "Recently used" group.
- **`BudgetSuggestionDataIntegrityTest`** — 12 cases covering empty inputs, fully-capped state, mixed capped/uncapped (load-bearing), tie-break determinism, blank-category filter, zero/negative-spend filters, capped-set semantics.
- **`CurrencyPickerOrderDataIntegrityTest`** — 8 cases covering the `buildCurrencyPickerOrder(recent, full)` helper that produces the dropdown's flat display order: empty-recent passthrough, recents-leading, dedup across both lists, recent-order preservation (most-recent-first, not alphabetical), full-list order preservation, blank stripping, intra-recent dedup, union-size invariant.

### Changed
- **`versionName`** `3.2.5` → `3.2.6`, **`versionCode`** `128` → `129`. C04-closure internal milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Sprint 2 P1 milestone
After this commit, **the first P1 Sprint 2 cluster (C04) is CLOSED.** All four P0 ship-blockers (C01 + C02 + C03a + C05) plus C04 are done. The CI data-integrity gate now runs **71 tests across 8 classes** on every push:

  - `AutoRecalcDataIntegrityTest` (8)
  - `BackupDataIntegrityTest` (10)
  - `BudgetSuggestionDataIntegrityTest` (12) — new in 3.2.6
  - `CategoriesDataIntegrityTest` (9)
  - `CurrencyPickerOrderDataIntegrityTest` (8) — new in 3.2.6
  - `RecalculateBalancesDataIntegrityTest` (3)
  - `RecentlyUsedDataIntegrityTest` (12)
  - `TransactionValidatorDataIntegrityTest` (9)

Up from 39 tests / 5 classes at 3.2.5. Next P1 cluster up is the C06/C07 FAB-ownership pair.

## [3.2.5] - 2026-05-26 *(Development milestone — C05 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C05 — category bleed across Income/Expense.** The Add Transaction sheet's category chips no longer offer income-flavoured categories on an Expense transaction and vice versa. Closes the *fourth* and final P0 ship-blocker from Sprint 1; all four (C01 + C02 + C03a + C05) are now done in a single session against an audit estimate of C01-only. Fix:
  1. NEW `app.fynlo.data.Categories` — typed `INCOME` (8 entries), `EXPENSE` (13 entries), `TRANSFER` (1 entry) lists, plus a `forType(type: String): List<String>` accessor that's case-insensitive on the three canonical type literals and falls back to `EXPENSE` for unknown / blank input (the conservative default — falling back to `INCOME` would re-risk the bug C05 fixes).
  2. `TransactionDialog.kt` chip list rewritten as `remember(isIncome) { (if (isIncome) Categories.INCOME else Categories.EXPENSE) + "Custom" }`. `LaunchedEffect(isIncome) { selectedCategory = "" }` clears the selection every toggle flip. The historical hacky `if (selectedCategory == "Food") selectedCategory = "Salary"` special-case on the Income toggle button is gone.
  3. `EditTransactionDialog.kt` chip list routes through `Categories.forType(transaction.type) + "Custom"`. Edit dialogs never change the transaction type so the list is keyed off the existing `transaction.type` value. **Bonus cleanup:** the hardcoded `"Expense"` and `"Balance Correction"` entries are dropped from the user-pickable list — the former is C03a's forbidden type-literal (now sanitized by `TransactionValidator`), the latter is an internal category set only by `FinanceRepository.quickEditBalance()` and shouldn't be user-selectable.
  4. `BudgetScreen.AddBudgetDialog` refactored to source `Categories.EXPENSE` instead of inlining an expense-category list. No behaviour change (Set Category Limit is expense-only — no Income/Expense toggle present, so no bleed was possible — but consolidating keeps category vocabularies in sync as the lists evolve).

### Added
- **`app.fynlo.data.Categories`** (pure-constant `object`) — see Fixed above.
- **`CategoriesDataIntegrityTest`** — 9 pure-function cases covering the core `INCOME ∩ EXPENSE = ∅` invariant, the audit's two acceptance scenarios ("Toggle Income → Salary/Freelance/Interest" + "Toggle Expense → Food/Fuel/Shopping/Bills"), case-insensitive `forType()` matching, the conservative-fallback contract, and the non-empty-list guard. Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s data-integrity CI gate.

### Changed
- **`versionName`** `3.2.4` → `3.2.5`, **`versionCode`** `127` → `128`. C05-closure internal milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Out-of-scope for C05 (deliberate)
- `RecurringScreen.kt` uses a free-text `OutlinedTextField` for category, not a chip picker. There's no list to swap, so no C05-style bleed is possible. Converting Recurring to a typed chip picker is UX work that belongs under **C04** (smart defaults) or a separate enhancement.

### Sprint 1 P0 milestone
After this commit, **all 4 P0 ship-blockers in the audit's Sprint-1 scope are CLOSED**:

  - C01 (Recalculate destroys payments) at 3.2.2
  - C02 (Stale exports / no auto-recalc) at 3.2.3
  - C03a (Schema integrity — additive) at 3.2.4
  - C05 (Category bleed Income/Expense) at 3.2.5

The CI data-integrity gate now runs **39 tests across 5 classes** on every push: `RecalculateBalancesDataIntegrityTest` (3) + `AutoRecalcDataIntegrityTest` (8) + `BackupDataIntegrityTest` (10) + `TransactionValidatorDataIntegrityTest` (9) + `CategoriesDataIntegrityTest` (9).

## [3.2.4] - 2026-05-26 *(Development milestone — C03a closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C03a — schema integrity (additive fields).** Closes 3 of 4 P0 ship-blockers from the audit's Sprint-1 scope (C01 + C02 + C03a all done; C05 is the lone P0 remaining). Five-part audit fix list:
  1. **Backup root carries provenance.** `BackupData` now has `schemaVersion: Int = 1` (v2 on new exports), `appVersion = BuildConfig.VERSION_NAME`, `exportedAt = Instant.now().toString()` (UTC ISO-8601), `userId` (caller-provided Firebase UID, blank if signed out), `deviceName = android.os.Build.MODEL`. All five fields are populated by `FinanceRepository.getAllDataAsJson()`. *(Stage 1.)*
  2. **`createdAt` on the four missed entities.** `MIGRATION_16_17` adds `createdAt INTEGER NOT NULL DEFAULT 0` to `flow_templates`, `investment_valuations`, `net_worth_snapshots`, `recurring_transactions`. Three are backfilled from `updatedAt`; `net_worth_snapshots` (no `updatedAt`) is backfilled from `strftime('%s', date) * 1000`. The v14→v15 migration covered the 10 main tables; this catches the four auxiliary entities it missed. *(Stage 2.)*
  3. **`projectId` on the one missed scoped entity.** `MIGRATION_16_17` adds `projectId TEXT NOT NULL DEFAULT 'personal'` to `investment_valuations` and backfills from the parent `investments` row's `projectId` so multi-project users keep their valuations correctly scoped. Orphan valuations (parent investment missing) fall back to `'personal'`. *(Stage 2.)*
  4. **SHA-256 content hash on backup root.** `BackupData.contentHash` is a 64-char lowercase hex digest over the canonical JSON form of the entire object with `contentHash` set to `""`. Computed at export, embedded as the last serialise step. Verified at import via `BackupIntegrity.check()` — restore refuses to proceed on hash mismatch (`IllegalStateException` thrown **before** `db.withTransaction` opens, so the DB never sees a tampered/corrupted backup). Forward-compat: `schemaVersion > 2` rejected with `UnsupportedVersion(N)`. Backwards-compat: v1 legacy backups (no metadata, no hash) accepted unconditionally — defaults on the new fields mean old JSONs still decode. *(Stage 1.)*
  5. **Forbidden literal categories rewritten to `"Uncategorized"`.** A historical UX bug let the category dropdown include `"Expense"` / `"Income"` / `"Transfer"` as options — but those are transaction TYPES, not categories. NEW `app.fynlo.data.TransactionValidator.sanitize(Transaction)` rewrites any of those three to `"Uncategorized"` at every write site (`insertTransaction` + the new-side of `editTransaction`). Matching is **case-sensitive and exact-string only** — `"Income Tax"` and `"Expense Reimbursement"` pass through unchanged. A one-shot `UPDATE transactions SET category = 'Uncategorized' WHERE category IN ('Expense','Income','Transfer')` inside `MIGRATION_16_17` fixes existing rows produced by the old dropdown. *(Stage 2.)*

### Added
- **`app.fynlo.data.BackupIntegrity`** (pure-function `object`): `computeHash(BackupData): String` (64-char lowercase hex; strips `contentHash` before computing for symmetry between export/import), `check(BackupData): Check` (sealed-class outcome: `Ok` / `UnsupportedVersion(N)` / `HashMismatch`), `CURRENT_SCHEMA_VERSION = 2` constant.
- **`app.fynlo.data.TransactionValidator`** (pure-function `object`): `FORBIDDEN_CATEGORIES = {"Expense","Income","Transfer"}` set, `FALLBACK_CATEGORY = "Uncategorized"` constant, `sanitizeCategory(String): String` and `sanitize(Transaction): Transaction` helpers (the latter returns the same instance fast-path when input is valid, to save GC pressure in the hot write paths).
- **`MIGRATION_16_17`** in `FynloDatabase.kt` — additive column adds + the legacy-category cleanup UPDATE. Schema export `app/schemas/.../17.json` committed.
- Entity additions: `FlowTemplate.createdAt`, `InvestmentValuation.projectId` + `InvestmentValuation.createdAt`, `NetWorthSnapshot.createdAt`, `RecurringTransaction.createdAt`. All `Long = 0L` or `String = "personal"` defaults so existing in-memory construction call sites stay source-compatible.
- **`BackupDataIntegrityTest`** — 10 pure-function cases (Stage 1). Matches the `*DataIntegrity*` filter.
- **`TransactionValidatorDataIntegrityTest`** — 9 pure-function cases (Stage 2). Same filter.
- **`FynloDatabaseMigrationTest`** — extended with 3 new instrumented `migrate16to17_*` cases (`createdAt` backfill / `projectId` backfill from parent / legacy category rewrite) and the existing `migrate15toCurrent` test updated to chain through `MIGRATION_16_17`. All 8 cases pass on CPH2767 / Android 16.

### Changed
- **`versionName`** `3.2.3` → `3.2.4`, **`versionCode`** `126` → `127`. C03a-closure internal milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.
- **`FinanceRepository.getAllDataAsJson()`** now takes an optional `userId: String = ""` parameter, populates the metadata block, and embeds the SHA-256 hash as the final pre-encode step.
- **`FinanceRepository.restoreDataFromJson()`** runs `BackupIntegrity.check()` **before** opening `db.withTransaction`. Throws `IllegalStateException` with a user-readable message on `UnsupportedVersion` or `HashMismatch` so the DB never sees a bad backup.
- **`FinanceRepository.insertTransaction()` and `editTransaction()`** apply `TransactionValidator.sanitize()` at the function boundary. `editTransaction`'s `new` parameter renamed to `newRaw` so the existing body's references to `new` rebind cleanly to the sanitized version.

### CI data-integrity gate progression
- C01 closure: 0 → 3 tests
- C02 closure: 3 → 11 tests
- C03a Stage 1: 11 → 21 tests
- **C03a Stage 2 (this entry): 21 → 30 tests** (across 4 test classes — `RecalculateBalancesDataIntegrityTest`, `AutoRecalcDataIntegrityTest`, `BackupDataIntegrityTest`, `TransactionValidatorDataIntegrityTest`).

## [3.2.3] - 2026-05-26 *(Development milestone — C02 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C02 — stale exports and missing auto-recalc.** The audit's reproducer ("PDF generated pre-recalc showed ₹268,081; post-recalc showed ₹241,663 — same data, ₹26K difference, no warning") is structurally no longer reachable. Five-part fix:
  1. **Auto-recalc on launch** — `FynloApplication.onCreate`'s post-init coroutine calls `recalcCoordinator.runIfStaleOnLaunch()` which runs the recalc once per calendar day (debounced on `lastRecalcAt < today.startOfDay` in the user's local zone, NOT a 24h sliding window). Failure is non-fatal.
  2. **Auto-recalc before every export** — all four formats (JSON, CSV, PDF, XLSX) route through `recalcCoordinator.runAndStamp()` first. XLSX previously bypassed the ViewModel entirely; now goes through a new `FinanceViewModel.exportToXLSX(os)` wrapper for consistency.
  3. **Dashboard "Last updated X ago" subtitle** — small `labelSmall`-styled line below the hero net-worth number on `HomeScreenModern`. Consumes `UserPreferences.lastRecalcAt(...)` reactively; renders via Android's `DateUtils.getRelativeTimeSpanString`. Renders "Not recalculated yet" when no recalc has ever run.
  4. **Before/after `AlertDialog` on manual Recalculate** — `FinanceViewModel.recalculateAllBalancesCapturingDelta()` captures pre/post `FinancialSummary` and returns a `RecalcDelta` with signed change fields. The Settings dialog shows pre→post for net worth plus signed deltas for receivables / cash / investments, or a "your data was already up to date" message when `isNoOp` (the common post-C01 case where structural enforcement means most recalcs change nothing). The old fire-and-forget Toast is gone.
  5. **Timestamp in exports** — `ExportUtility.generatePDF` adds a "Recalculated: \<date\>" line under the existing "Generated:" header; `ExcelExportUtility.generateFullBackup` prepends a `Metadata` sheet with `Generated` / `Recalculated at` / `Export type` rows. `RecalcCoordinator.runAndStamp()` now returns the stamped time so callers don't need a separate DataStore read.

### Added
- **`app.fynlo.data.RecalcCoordinator`** (`@Singleton`, Hilt-injected). Wraps `FinanceRepository.recalculateAllBalances()` with `lastRecalcAt` stamping; exposes `runAndStamp(): Long`, `runIfStaleOnLaunch()`, and a pure `shouldRecalcOnLaunch(lastRecalcAt, now, zone): Boolean` predicate on the companion (testable without Robolectric).
- **`UserPreferences.lastRecalcAt(ctx): Flow<Long>` + `setLastRecalcAt(ctx, ms)`** — DataStore-backed; follows the existing read-Flow + suspend-write pattern.
- **`FinanceViewModel.exportToXLSX(os)`** — new wrapper so XLSX gets the same recalc-stamp contract as PDF / CSV / JSON.
- **`app.fynlo.RecalcDelta`** data class — before/after snapshot of `FinancialSummary` with derived change fields and an `isNoOp` predicate. Used by the Settings result dialog.
- **`app/src/test/.../AutoRecalcDataIntegrityTest`** — 8 pure-function tests for the debounce predicate (zero / yesterday-late / same-day / week-gap / zone-correctness / arithmetic boundary). Matches `*DataIntegrity*` and `*Recalculate*` filters; the CI data-integrity gate count goes **3 → 11**.

### Changed
- **`versionName`** `3.2.2` → `3.2.3`, **`versionCode`** `125` → `126`. Internal milestone marker — not uploaded per the release-cadence ADR.
- **`SettingsScreen` PDF / XLSX launchers** rewrapped — PDF in `scope.launch(Dispatchers.IO)` since `exportToPDF` is now suspend; XLSX routes through `viewModel.exportToXLSX(os)` instead of calling `ExcelExportUtility.generateFullBackup` directly.

## [3.2.2] - 2026-05-26 *(Development milestone — C01 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C01 — Recalculate Balances no longer destroys payment history.** Legacy borrowers and debts whose partial repayment was tracked only on the cumulative `paid` field (with `paidPrincipal == paidInterest == 0` and no rows in `payments` / `debt_payments`) had `paid` silently zeroed on every tap of Recalculate. The fix is structural: `payments` and `debt_payments` are the **single source of truth** for repayment history; `paid` is a derived projection re-computed from `SUM(payments)`. (UX_AUDIT §C01; ADR `decisions/2026-05-26-c01-fix-strategy.md`; commits `331c1ae` Stage 1, `5a00d4a` Stage 2.)
- `setup-gradle@v3` × Gradle 9 incompatibility — every `./gradlew` invocation in CI was failing in 6s with `Cannot get the value of write-only property 'removeUnusedEntriesOlderThan'`. Upgraded to `@v4` in both workflows.
- `kotlinx-serialization` × `room-testing` runtime incompatibility — `room-testing:2.8.4` was compiled against `kotlinx-serialization 1.8.x` (where `GeneratedSerializer.typeParametersSerializers()` has a default implementation), but the project pinned `1.7.3` where it's still abstract. `MigrationTestHelper.loadSchema` failed with `AbstractMethodError` on every test. Bumped `kotlinxSerialization` `1.7.3 → 1.8.1` in `gradle/libs.versions.toml`. Bump also covers `androidx.room:room-testing:2.8.4 androidTestImplementation` (previously stuck at `2.6.1`).
- **PDF export: consolidated Lending & Receivables table now includes a `Paid` column.** The widely-shared whole-portfolio PDF (the one users hand to accountants) was rendering only `Principal / Rate / Lent On / Due / Status / Notes`, making a borrower with `paid > 0` indistinguishable from a brand-new loan. Surfaced by the 3.2.2 §3.5 smoke test. The per-borrower-statement PDF (`generateLoanStatementPDF`), XLSX, and JSON all had it; only this one widely-shared table did not. Touches C21 (PDF/XLSX export quality) — doesn't close the cluster, just resolves the most visible artefact.
- **PDF footer no longer hardcodes the version string.** `ExportUtility.kt` had `"Generated by Fynlo v3.1 | ..."` baked in, so a 3.2.2 build was emitting PDFs labelled `v3.1`. Now reads `BuildConfig.VERSION_NAME`. This was a pre-existing `LINT_RULES.md FY022` violation; the protocol-compliance check in `checks.yml` gates on `pull_request` only, so direct-push commits never tripped it.

### Added
- **Schema migration `v15 → v16`** — one-time backfill on first launch: every borrower / debt with `paid > 0` and no rows in `payments` / `debt_payments` gets one synthetic `Payment` (or `DebtPayment`) row with `amount = paid`, `principal = paid`, `interest = 0`, `date = loanDate`, `type = "Legacy backfill"`, notes recording that the actual repayment date is genuinely unknown. Total `paid` is preserved exactly. Schema export `app/schemas/.../16.json` committed.
- **`RecalculateBalancesDataIntegrityTest`** — Robolectric-backed JVM test exercising real Room SQL against an in-memory `FynloDatabase` in three cases: legacy borrower with backfilled Payment, current-schema borrower with split Payment row, brand-new borrower with zero payments. All three green in CI.
- **`FynloDatabaseMigrationTest`** — instrumented test (`app/src/androidTest/`) using Room's classic `MigrationTestHelper`. Five cases: backfills a legacy borrower, leaves an already-populated borrower untouched, mirrors for the debts side, leaves `paid = 0` borrowers untouched, and re-opens the database with full Room afterwards to catch schema-validation drift. All five passed on CPH2767 / Android 16 against the real `v15 → v16` migration code path. Schemas are exposed to the test APK via `sourceSets["androidTest"].assets.srcDirs("$projectDir/schemas")`. Requires a connected device/emulator (`./gradlew :app:connectedProdDebugAndroidTest`); CI integration via Firebase Test Lab is a follow-up.
- **Data-integrity CI gate** — `.github/workflows/checks.yml` runs an explicit `--tests '*DataIntegrity*' --tests '*Recalculate*'` Gradle filter on every push / PR, so a regression cannot pass silently in the test log. (Closes INF04 in `UX_AUDIT §9`.)
- **ADR directory** `decisions/` with the C01 fix-strategy ADR (Michael Nygard format: Status / Context / Decision / Consequences / Alternatives considered).
- **Release notes directory** `release_notes/` with `3.2.2.md` (Play Store copy + migration disclosure + in-app banner draft + pre-release checklist).
- **`§6 Journal`** section added to `PROJECT_STATE_FOR_AI.md` (referenced by the preamble's reading-order block but never created).

### Changed
- **`FinanceRepository.recalculateAllBalances()`** rewritten: no longer calls the destructive `recalculateBorrowerPaid` / `recalculateDebtPaid` DAO queries (`UPDATE ... SET paid = paidPrincipal + paidInterest`). Now derives `paid` exclusively via `rebuildBorrowerPaidFromPayments` / `rebuildDebtPaidFromDebtPayments`, with the `WHERE EXISTS (payments)` gate removed and `SUM(...)` wrapped in `COALESCE(..., 0)` so brand-new borrowers land at `paid = 0` (not NULL).
- **`FinanceRepository.editTransaction`** Loan / Debt Repayment branches: editing a repayment transaction now does delete-old-Payment + insert-new-Payment + rebuild, instead of mutating `paid` directly. Sync now also pushes the Payment / DebtPayment delete (was a latent sync-correctness gap).
- **`FinanceRepository.deleteTransaction`** Loan / Debt Repayment branches: the "no matching Payment" fallback that reversed `paid` directly is removed. When no Payment matches the transaction's amount/date, `paid` is left as-is — consistent with the invariant `paid == SUM(payments)`.
- **`FinanceRepository.insertPaymentWithDest` / `insertDebtPaymentWithSource`**: the conditional `updateBorrower/DebtPaid{Amount,Principal,Interest}` writers are replaced by a single `rebuild...FromPayments` call after the Payment row is inserted.
- `PROJECT_STATE_FOR_AI.md` `Version` header bumped from stale `1.8.0` to `3.2.1` (matches `app/build.gradle.kts` `versionName`); §0.4 / §0.5 reflect C01 closure with structural guardrails for future agents.

### Removed
- **10 DAO methods** that allowed direct mutation of derived columns:
  - `updateBorrowerPaidAmount` / `updateBorrowerPaidPrincipal` / `updateBorrowerPaidInterest`
  - `updateDebtPaidAmount` / `updateDebtPaidPrincipal` / `updateDebtPaidInterest`
  - `recalculateBorrowerPaid` / `recalculateDebtPaid` *(the destructive queries that motivated the entire C01 ADR)*
  - `seedPaidPrincipalFromPaid` / `seedDebtPaidPrincipalFromPaid` *(dead code — their logic lives inline in migrations 11→12 / 12→13 / 15→16)*
- Matching repository wrappers on `LendingRepository` and `DebtRepository`.

### Security / data integrity
- **`paid == SUM(payments)` is now structurally enforced.** No DAO method exists that lets new code violate the invariant without first re-adding one of the ten deleted queries — and that re-introduction would be visible in code review and would fail the data-integrity CI gate.

### Performance
- Cold start ~4 % faster in Partial AOT mode (the production ship mode), up to ~10 % on devices with fully cold caches. Driven by the baseline profile bundled in the APK via `baseline-prof.txt` + `profileinstaller`. Measured on CPH2767 / Android 16, 2026-05-24; see `PROJECT_STATE_FOR_AI.md §5.6`.

### Infrastructure
- `gradle/wrapper/gradle-wrapper.jar` is now **committed** to the repository (was gitignored with a misleading "optional — keeps repo lighter" comment; broke `./gradlew` on every fresh clone, including all CI runs). `.gitignore` corrected.
- `gradlew` committed with executable bit (`100755`) instead of `100644`.
- `gradle/actions/setup-gradle@v3` → `@v4` in `android.yml` and `checks.yml` (Gradle 9 compatibility).
- Android Lint baseline (`app/lint-baseline.xml`) adopted: existing 41 errors + 174 warnings are filtered; new lint errors still fail CI. (Notable real issue inside the baseline: `windowLayoutInDisplayCutoutMode` in `values-night/themes.xml` requires API 27 but `minSdk` is 26 — flagged for a real fix in a follow-up.)
- `paths-ignore: ['**/*.md', 'docs/**']` on Android workflows — docs-only commits no longer trigger ~15 minutes of CI per commit. Workflow files themselves are deliberately not ignored.
- Governance documents added at repo root: `LEGAL_PROTOCOL.md`, `PRIVACY_POLICY.md`, `TERMS_AND_CONDITIONS.md`, `RELEASE_PROTOCOL.md`, `DATA_RECOVERY_PROTOCOL.md`, `BACKUP_PROTOCOL.md`, `INCIDENT_PROTOCOL.md`, `PRIVACY_PROTOCOL.md`, `EXTERNAL_DEPENDENCY_PROTOCOL.md`, `ANALYTICS_PROTOCOL.md`, `ONBOARDING_PROTOCOL.md`, `ACCESSIBILITY_AUDIT_PROTOCOL.md`, `SUNSET_PROTOCOL.md`, `GOVERNANCE_README.md`, `AI_AGENT_PROTOCOL.md`, `LINT_RULES.md`, `DESIGN_SYSTEM.md`, `UX_AUDIT_2026-05-25.md`, plus `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/checks.yml`, and `docs/privacy.md` / `docs/terms.md` for the GitHub Pages legal mirror (`docs/_config.yml` site title set to `Fynlo Legal`).

### Skipped
- **3.2.1** was an internal milestone (the governance / CI hygiene commits) and is intentionally never promoted to Play Store production — `RELEASE_PROTOCOL.md §8`'s hard rule: *"Do NOT promote 3.2.1 to production. Skip directly to 3.2.2."*

### Still TODO before 3.2.2 ships
*(Not blockers for the changelog entry itself, but listed here so the release procedure has a single source of truth.)*

- ~~`app/build.gradle.kts` `versionName` / `versionCode` bump~~ ✅ done (`versionName = "3.2.2"`, `versionCode = 125`).
- ~~Instrumented migration test~~ ✅ done — `FynloDatabaseMigrationTest` (5 cases, instrumented; passed on CPH2767 / Android 16, 2026-05-26). CI integration via Firebase Test Lab is a separate follow-up.
- `UX_AUDIT_2026-05-25.md §C01` status flip to "Closed in 3.2.2" once the tag lands.
- Macrobenchmark re-run + manual smoke test (`RELEASE_PROTOCOL.md §3.4`, §3.5) — need a device.

## [3.2.0] - 2026-05-16

### Added
- Hilt dependency injection (upgraded to 2.56 for Gradle 9 compatibility)
- First Launch Setup Wizard (language, theme, notifications, profile)
- Jetpack Preferences DataStore (replacing SharedPreferences)
- Default currency preference (9 currencies)
- Date format preference (3 formats)
- Firebase Analytics integration (screen views, feature usage, onboarding)
- Crashlytics non-fatal exception recording + ANR tracking
- Firebase Performance Monitoring with app startup trace
- Network security config (cleartext disabled)
- Staging build variant for QA testing
- 5 focused repositories (Lending, Debt, Investment, Expense, Account)
- 30 unit tests (InterestEngine + FinancialSummary calculations)

### Changed
- Hilt Gradle plugin now works with KSP on Gradle 9.x (version 2.56)
- ThemeController migrated from SharedPreferences to DataStore
- Navigation flow: Onboarding -> Setup Wizard -> Login
- ProGuard rules: added Hilt/Dagger keep rules

### Fixed
- Removed duplicate POST_NOTIFICATIONS request from MainActivity
- Fixed smart quote encoding issues in Navigation.kt

### Security
- Added network_security_config.xml (disables cleartext HTTP)
- ProGuard strips debug/verbose/info logs in release builds
- Firestore rules enforce user isolation and field validation

## [3.1.0] - 2026-05-15

### Added
- App launcher icon changed to emerald
- LoginScreen redesigned to match emerald design language
- Dark mode audit for onboarding illustrations

## [3.0.0] - Previous

### Features
- Multi-project workspace support
- Lending with SI/CI/RB/Both interest types
- Debt tracking with principal/interest split
- Investment portfolio with valuations
- Recurring transactions with auto-logging
- Net worth history and snapshots
- Money flow visualization
- Excel/JSON/CSV/PDF export
- PIN lock + biometric unlock
- Firebase cloud sync (offline-first)
- Smart Flow Wizard for quick logging
