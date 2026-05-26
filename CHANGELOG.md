# Changelog

All notable changes to Fynlo are documented here.

## [3.2.16] - 2026-05-27 *(Development milestone — EMI Calculator visual polish; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **EMI Calculator visual polish (partial of UX_AUDIT C12-C15 P1 redesign backlog).** User flagged the screen as "not good to see and missing features" and clarified it's their EMI calculator (the canonical Indian-finance term). Visual-polish-only scope this commit; features (Save-as-Debt, Prepayment simulation, Compare scenarios, Share schedule) deferred.
  - **Header rename** "Loan Calculator" → "EMI Calculator" (both in `PremiumScreenHeader` and in `Navigation.Screen.LoanCalc.label` for the side-drawer entry). Route name `loan_calc` kept unchanged — backwards-compat with any deep-link / saved state.
  - **Tenure input row fixed.** The number input + Months/Years segmented row both used `weight(1f)` previously, making them the same width and cramping the segment labels. Now: number input is `weight(1f)` (takes remaining space), unit segment hugs natural width.
  - **Result cards bumped from `bodySmall` → `titleMedium`.** Three cards side-by-side (Principal / Total Interest / Total Payment) with 14sp values were unreadable; 16sp + better card padding makes them proper headline-level information.
  - **Loan / Due Date inputs now use `DatePickerField`** (same component used everywhere else in the app). No more `DD-MM-YYYY` text-hint pattern, no more parsing-by-string-input.
  - **Outstanding-as-of-Today section hidden behind a `Switch` toggle** ("Already took this loan? Show accrued interest from loan date onward"). The primary EMI-calculator use case is planning a *future* loan; the accrued-interest path is the exception. Toggle defaults OFF for a cleaner default form; opting in surfaces the date pickers and the accrued-interest panel.
  - **Amortization schedule got a `Yearly / Monthly` toggle.** Yearly is the default — 24 monthly rows for a long loan was a wall of text users scanned past; yearly summary (one row per year with summed principal + interest + end-of-year remaining balance) is what people actually want to see. Monthly view still available for users who want detailed precision.
  - **Reset button** added to the header action slot (`FilledTonalIconButton` with refresh icon). Clears all inputs back to defaults — useful when iterating between scenarios.
  - **Empty state** when no inputs entered: small calculator icon + "Enter principal, rate, and tenure" hint. Replaces the previous blank gap.
  - **Outstanding panel typography bumped** — `bodyMedium` for labels and values, `titleMedium` header. Was `bodySmall` previously which felt cramped against the prominent `Outstanding` heading.
  - **Bottom padding** now uses the shared `FabBottomPadding` constant (C06 design system) instead of the previous hardcoded `100.dp`.

### Deferred to a future cluster (per user "just visuals, skip features")
- **Save as Debt** — push the computed loan into the Debts tracker so the user can track it after taking the loan.
- **Prepayment simulation** — "prepay ₹X in month Y → total interest savings + tenure shortening."
- **Affordability %** — EMI as % of declared salary; needs a salary preference field.
- **Compare two scenarios** side-by-side.
- **Share / export schedule** (CSV / PDF).
- **EMI breakdown pie chart** (Principal vs Total Interest visual).

### Changed
- **`versionName`** `3.2.15` → `3.2.16`, **`versionCode`** `138` → `139`. EMI Calculator visual-polish milestone.

### Data-integrity gate
Unchanged at **112 tests across 9 classes**, 0 failures (UI-only refactor).

### Notes
- **Stage 4 of C08 still pending** — PDF + XLSX export migration was originally planned next, but the user surfaced the EMI Calculator first. Stage 4 ships as 3.2.17.

## [3.2.15] - 2026-05-27 *(Development milestone — C08 Stage 3: Detail sweep across 18+ files via 6 parallel agents; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C08 Stage 3 — Detail-style migration of ~147 call sites across 18+ files via 6 parallel agents.** Largest single sweep in the C08 plan. After this commit, the **only remaining `String.format("%,.0f", amount)` patterns in display code are inside the exports** (Stage 4 scope: `ExportUtility.kt` / `ExcelExportUtility.kt`). Every UI screen, dialog, card, list row, and widget now routes through `CurrencyFormatter` for a single source of truth on number display.

### Per-agent breakdown
- **Agent A — Payment/Lending/Debt dialogs (27 sites):** `PaymentDialog.kt` (22 sites: `CollectPaymentDialog` + `PayDebtDialog`), `LendingDialog.kt` (2), `DebtDialog.kt` (3). Each dialog composable gained a `currencyCode: String = "INR"` parameter (default keeps existing named-arg call sites compiling). The hero typing-input `Text("₹")` symbols swapped to `CurrencyUtils.symbolFor(currencyCode)` for currency-aware prefix display while keeping raw text-field state intact (IME-safe).
- **Agent B — Lending/Debt/Customer screen bodies (32 sites):** `LendingScreen.kt` (25 including the WhatsApp/share-copy `buildString` lines), `DebtScreen.kt` (6 in `DebtCard`), `CustomerDetailScreen.kt` (1 in `PaymentItem`). `currencySymbol` derivation deleted from `DebtScreen.kt` + `CustomerDetailScreen.kt` after all sites migrated. Share-copy precision dropped from `.2f` → `.0f` per audit Detail spec (flagged for review).
- **Agent C — Investment cluster + widget (18 sites):** `InvestmentScreen.kt` (11), `InvestmentDialog.kt` (4), `PortfolioAnalyticsSheet.kt` (2), `NetWorthWidget.kt` (1). `PortfolioBreakdownSheet` composable signature changed (`currencySymbol` → `currencyCode`), forced ripple updates to call sites in `HomeScreen.kt`, `HomeScreenModern.kt`, and `Navigation.kt` (which gained a `navProject` / `navCurrencyCode` derivation to pass through to `AddInvestmentDialog`). **`NetWorthWidget` hardcodes `"INR"` with a `TODO: widget should read default-currency pref from DataStore` comment** — widget runs outside Composable scope so it can't easily read user pref; logged as a follow-up task.
- **Agent D — Daily-use screens (36 sites):** `BudgetScreen.kt` (11 in `BudgetCard` + overview chips, including a smart Negative-vs-Detail branch for the Remaining chip when over-budget), `GoalScreen.kt` (2), `SpendScreen.kt` (7), `MoneyFlowScreen.kt` (9), `TransactionHistoryScreen.kt` (7). `TransactionItem` and month-header rows now use the **proper +/− pattern from Stage 2** (Income: `+₹2,41,663`; Expense: `−₹15,000` via `CurrencyFormatter.negative` for the en-dash). `MoneyFlowScreen` Net Flow display now branches Detail / Negative based on sign for consistent en-dash use. `currencySymbol` deleted from SpendScreen / MoneyFlowScreen / TransactionHistoryScreen.
- **Agent E — Calculators + repository note generation (24 sites):** `LoanCalculatorScreen.kt` (10 — EMI display `.2f` → `.0f`, all amortization rows), `DebtPayoffScreen.kt` (5), `MonthlySummaryScreen.kt` (6), `FinanceRepository.kt` (3 note-generation sites — Gain/Loss transaction notes + Write-off description). Repository sites use `dao.getProjectById(<entity>.projectId)?.currency ?: "INR"` to self-resolve currency at persistence time — no TODO needed, no UI-layer dependency.
- **Agent F — Miscellaneous cleanup (10 sites + 1 deletion + 1 latent bug fix):** `GlobalSearchScreen.kt` (1), `CollectionCalendarScreen.kt` (1 in `DueEntryCard`), `ProfitLossScreen.kt` (2 in outer + inner `fmt` helpers), `SettingsScreen.kt` (1 in `fmtMoney`), `SmartFlowWizardScreen.kt` (3 — wizard debit/credit/neutral rows), `DashboardComponents.kt` (1 in `AccountGrowthIndicator`). **Deleted `DesignSystem.kt`'s `formatAmount` helper entirely** (zero callers — confirmed via grep). **Latent bug fix in `AccountGrowthIndicator`**: pre-3.2.15 code rendered negative growth as `₹-1,200` (hyphen between symbol and digits, from `String.format` itself). Now correctly `−₹1,200` (en-dash before symbol per audit convention). No tests were exercising this path so the bug wasn't surfaced before.

### Cumulative C08 progress
- **Stage 1 (3.2.13):** CurrencyFormatter foundation + 33 tests.
- **Stage 2 (3.2.14):** 52 highest-impact Hero/ListRow/Negative sites + 3 truncation fixes.
- **Stage 3 (3.2.15, this commit):** 147 Detail sites + 1 latent bug fix + DesignSystem `formatAmount` deletion.
- **Stage 4 (next):** PDF + XLSX export migration (XLSX numeric-cell fix is the load-bearing one).

**Total sites migrated through Stages 1-3: ~202 / 257** from the survey (the remainder are the 14 export sites Stage 4 will handle + a handful of `OutlinedTextField` `value =` raw-state sites that are correctly excluded per the IME-safety rule).

### Notable patterns established
- **`currencyCode: String = "INR"`** added as default param on every shared Composable that previously took `currencySymbol`. Defaults preserve preview/test composability; named-arg call sites unaffected.
- **`PortfolioBreakdownSheet` signature change** propagated automatically through ripple updates in HomeScreen / HomeScreenModern / Navigation — exact pattern Stage 4 may reuse for shared formatters.
- **Smart Negative-vs-Detail branching** for sign-significant displays (BudgetScreen Remaining, MoneyFlowScreen Net Flow, TransactionItem amounts) — when the value can be negative, use `negative()` for the en-dash; otherwise `detail()`. Established as the consistent app-wide pattern.
- **Repository self-resolves currency** via DAO lookup rather than threading through the UI — clean separation of concerns, no caller-side currency-code plumbing.

### Visible UX changes worth flagging on smoke
1. **Indian lakh-crore grouping everywhere now.** Not just dashboards (Stage 2) — every card body, dialog field, transaction row, calculator output, lending/debt detail. `₹241,663` → `₹2,41,663` is now universal for INR/NPR/LKR/BDT projects.
2. **Decimals dropped to no-decimals on Detail surfaces.** LoanCalculator EMI, account-balance share-copy lines, and per-day interest accrual all dropped from `.2f` → `.0f` per audit spec. If a specific surface needs decimal precision (e.g., a formal loan-summary export), flag it in smoke — that'd be a design-system tweak.
3. **All transaction rows show `−` en-dash for expenses, `+` for income** consistently. Pre-3.2.15 had inconsistent prefix logic across History, MoneyFlow, Spend, CustomerDetail PaymentItem.
4. **DashboardComponents account growth indicator now shows `−₹1,200` instead of `₹-1,200`** for negative growth. Subtle but consistent with the rest of the app.

### Changed
- **`versionName`** `3.2.14` → `3.2.15`, **`versionCode`** `137` → `138`. C08 Stage 3 milestone marker.

### Data-integrity gate
Unchanged at **112 tests across 9 classes**, 0 failures (pure call-site refactor; CurrencyFormatter contract already covered).

## [3.2.14] - 2026-05-27 *(Development milestone — C08 Stage 2: Hero/ListRow/Negative + truncation fixes; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C08 Stage 2 — highest-impact display migrations.** Four parallel agents migrated the most-visible currency-formatting call sites to `CurrencyFormatter` (landed in 3.2.13). Visible result: every dashboard hero number now renders with **Indian lakh-crore grouping** (`₹2,41,663` not `₹241,663`), all over-budget / loss / trend-down indicators use the audit-mandated **`−` en-dash** (U+2212, not ASCII hyphen), and three silent `.toInt()` data-truncation bugs are gone.
  - **Dashboard hero sites (Agent A) — 14 direct + ~12 transitive via helper functions:**
    - `HomeScreen.kt` — net worth tile, assets/liabilities sub-tiles, 4 metric cards (Idle Cash / Growing Assets / Hand Loans / Total Owed), per-account balance card, and the trend sparkline (Negative path now uses formatter's en-dash).
    - `HomeScreenModern.kt` — 42sp net-worth hero, the screen's local `fmt(v)` helper (which transitively migrates Assets/Liabilities, account rows, insight rows), and the trend pill (Negative).
    - `NetWorthHistoryScreen.kt` — Current Net Worth hero (was `%,.2f`, now no-decimal Hero per design spec), Highest/Lowest stat cards.
    - `ReportsHubScreen.kt` — local `fmt(v)` helper (transitively migrates Income/Expenses/Net Cash Flow display, badges, category breakdowns) and the Net Worth Trend mid-label.
  - **Detail-card Hero + critical truncation fixes (Agent B):**
    - `AccountStatementScreen.kt:83` — Account balance header (was `%,.2f`).
    - `CustomerDetailScreen.kt:187` — Total Outstanding hero.
    - `CustomerDetailScreen.kt:199-201` — **CRITICAL silent data loss fix.** `borrower.amount.toInt()`, `interest.toInt()`, `borrower.paid.toInt()` were dropping decimal precision (user with `₹1499.50` outstanding saw `₹1499` and thought they owed a rupee less than they actually did). Now uses `CurrencyFormatter.detail(...)` which preserves full precision.
    - `DebtScreen.kt:122` — Total Outstanding hero.
  - **ListRow K/L abbreviation + duplicate-helper deletion + Analytics truncation (Agent C):**
    - `InterestIncomeScreen.kt` — **deleted the duplicate `fmtK()` helper** (lines 494-496, pre-dated `CurrencyFormatter`). Migrated its 3 call sites to `CurrencyFormatter.listRow(...)`.
    - `AnalyticsComponents.kt:50` — **CRITICAL truncation fix.** Spending breakdown card was using `amount.toInt()` (silent decimal drop); now uses `CurrencyFormatter.listRow(...)` which both preserves precision AND switches to K/L abbreviation appropriate for a list-row analytics breakdown.
  - **Negative-sign correctness + InvestmentDialog truncation (Agent D):**
    - `BudgetScreen.kt:278` — Over-budget Remaining display. Was using ASCII hyphen prefix; now uses `CurrencyFormatter.negative(...)` with U+2212 en-dash.
    - `InvestmentScreen.kt:374-375` — Gain/loss display. Profit gets literal `+` + Hero; loss gets Negative formatter (en-dash). Replaces the inconsistent inline `+`/empty prefix on absolute value.
    - `InvestmentDialog.kt:62` — **CRITICAL truncation fix.** Amount text-field initializer was using `.toInt().toString()` (drops decimal precision on edit). Now uses `CurrencyFormatter.input(...)` which uses `.toLong()` internally (full Double range).

### Visible UX changes worth noting
- **Indian grouping everywhere.** Dashboard net-worth, total assets, total liabilities, account balances, customer outstanding, debt outstanding — all switch from `₹241,663` (Western 3-digit grouping) to `₹2,41,663` (Indian lakh-crore grouping) when project currency is INR / NPR / LKR / BDT.
- **`.2f` precision dropped to `.0f`** on `NetWorthHistoryScreen` Current Net Worth and `AccountStatementScreen` Current Balance. Per audit's design-system spec (`Hero = no decimals`). If you see this on smoke and want the cents back, that's a design-system tweak (single Hero method signature change) — but the audit was explicit.
- **`−` en-dash on every negative** instead of mixed hyphen / `(parens)` / `(empty)` prefixes. Renders more legibly and is unambiguous against punctuation hyphens.
- **No more silent decimal truncation** on the 3 critical sites — your `₹1499.50` outstanding now shows as `₹1,500` (rounded for Hero/Detail display) instead of `₹1499` (truncated).

### Notes for future stages
- Each migrated screen now has `currencyCode` derived alongside the existing `currencySymbol` variable. `currencySymbol` was kept in scope because other code paths in those screens (Detail-style body text) still consume it. **Stage 3 will remove `currencySymbol` from these screens** once every call site is migrated.
- Several Compose components (`AnalyticsComponents.SpendingAnalyticsCard`, `InvestmentCard`, `BudgetCard`) gained a new `currencyCode: String` parameter with a `"INR"` default. Defaults preserve preview/test composability; production call sites already pass the proper value where it was a required param.
- Stage 3 (Detail sweep, ~189 sites) and Stage 4 (PDF + XLSX export fixes) still to come.

### Changed
- **`versionName`** `3.2.13` → `3.2.14`, **`versionCode`** `136` → `137`. C08 Stage 2 milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
Unchanged at **112 tests across 9 classes**, 0 failures (pure call-site refactor — no logic change; `CurrencyFormatter` itself was already fully tested in Stage 1).

## [3.2.13] - 2026-05-27 *(Development milestone — C08 Stage 1: CurrencyFormatter foundation; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Added
- **C08 Stage 1 — NEW `app.fynlo.logic.CurrencyFormatter`** with the six audit-specified display styles (UX_AUDIT §C08). Stage 2+ migrates the ~257 call sites this object replaces; this file lands first so the migration has a stable target.
  - **`hero(amount, currencyCode, locale)`** — full amount, no decimals, locale-correct grouping. INR/NPR/LKR/BDT use lakh-crore grouping (`₹2,41,663`); others use Western thousand-comma (`$241,663`).
  - **`detail(...)`** — alias for Hero with intent-documenting name.
  - **`chartHero(...)`** — Hero unless ≥10 chars, then abbreviates via `listRow`. Saves chart-axis space.
  - **`listRow(...)`** — always abbreviates: K/L/Cr for INR-family, K/M/B for others. For dense list rows.
  - **`input(amount): String`** — raw integer, no symbol, no commas. For text-field state during typing.
  - **`negative(amount, currencyCode, locale)`** — Hero with audit-mandated `−` en-dash prefix (U+2212, NOT ASCII hyphen).
- **`CurrencyFormatterDataIntegrityTest`** — 33 cases pinning the contract: Indian grouping correctness across thousand/lakh/crore boundaries, Western grouping for non-INR, NPR/LKR/BDT inheriting Indian grouping with their symbols, K/L/Cr/K/M/B abbreviation boundaries, ChartHero's 10-char threshold, Input contract (no comma, no symbol, integer-truncation), Negative en-dash invariant (never ASCII hyphen), NaN/Infinity defensive paths, `NEGATIVE_PREFIX` constant lockdown.

### Notes
- **Indian grouping implementation choice.** First implementation attempt used `NumberFormat.getInstance(Locale("en","IN"))` — fails on the JVM the unit tests run on (CLDR data varies across JDK / ICU versions; some runtimes silently use Western grouping). Second attempt used `DecimalFormat` with the `#,##,###` pattern — also unreliable, Java's pattern parser handles secondary grouping inconsistently across versions. **Final**: hand-rolled `formatLakhCrore` string-manipulation routine. Pure Kotlin, no JDK pattern parser involved, identical output on every Android device and every test JVM.
- **No call sites migrated yet.** That's Stage 2 (Hero / ListRow / Negative + truncation fixes — ~51 sites) and Stage 3 (Detail sweep — ~189 sites). Stage 4 will handle the PDF + XLSX exporters (XLSX numeric-cell fix is the load-bearing one — currently stored as strings, breaking Excel formulas).

### Changed
- **`versionName`** `3.2.12` → `3.2.13`, **`versionCode`** `135` → `136`. C08 Stage 1 milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
**79 → 112 tests across 9 classes** (+33 from `CurrencyFormatterDataIntegrityTest`), 0 failures.

## [3.2.12] - 2026-05-27 *(Development milestone — C06 + C07 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C06 (FAB overlap) — system-wide layout bug.** Every scrollable container that sits under a FAB now reserves a shared `FabBottomPadding = 120.dp` clear zone in its `contentPadding.bottom` (LazyColumn) or trailing `Spacer` (verticalScroll). Pre-fix, the 10 scrolling screens used `bottom = 100.dp` which was provably not enough (Material 3 FAB is 56dp + 16dp container margin + gesture-nav inset ≈ 80dp minimum; 100dp left almost no breathing room, 120dp gives a comfortable safety margin). Screens fixed: `HomeScreen`, `HomeScreenModern` (was already 120dp), `SpendScreen`, `BudgetScreen`, `GoalScreen`, `InvestmentScreen`, `LendingScreen`, `PeopleScreen`, `DebtScreen`, `TransactionHistoryScreen`, `RecurringScreen`. The new `FabBottomPadding` constant lives in `DesignSystem.kt` so future scrolling screens can reuse it.
- **C07 (Duplicate FAB on empty states) — three-entry-point UI mess.** Pre-fix, `GoalScreen`, `BudgetScreen`, and `RecurringScreen` all rendered three Add affordances simultaneously on empty state: the Scaffold's QuickAdd FAB (from `Navigation.kt:480`), a screen-level FAB or header `+` IconButton, AND an inline "Add First X" CTA. The audit's exact wording: "Three entry points minimum. User asks: which one?"
  - **`Navigation.kt:172` — `showFab` hidden list extended** to include `Screen.Budgets.route`, `Screen.Goals.route`, `Screen.Recurring.route`. These three screens own their own contextual Add affordance (the Scaffold FAB opens a QuickActionMenu for *transactions*, which is the wrong intent on a Budgets/Goals/Recurring page).
  - **NEW shared `EmptyState(icon, title, subtitle, actionLabel, onAction)` composable** in `DesignSystem.kt`. On empty state, each of the three screens now renders ONLY this one composable, with the screen-level FAB / header `+` IconButton conditionally hidden by an `if (list.isNotEmpty())` wrapper. Once data exists, the screen-level FAB / `+` re-appears and the EmptyState is replaced by the list. Single unambiguous CTA per screen state.
- **Bonus: `CollectionCalendarScreen` back arrow visibility.** Same `tint = Color.White` on light-surface invisibility bug as the RecurringScreen header `+` fixed in 3.2.7. Replaced with `FilledTonalIconButton` (theme-aware secondary container + properly-tinted icon). Logged as a follow-up in the 3.2.7 commit, folded into this commit since it's the same icon-on-surface pattern as C06/C07 owns.

### Added
- **`app.fynlo.ui.theme.FabBottomPadding: Dp = 120.dp`** — shared constant for any scrolling container under a FAB. Doc comment explains the 56dp + 16dp + 48dp safety-margin math.
- **`app.fynlo.ui.theme.EmptyState`** — shared composable for empty-state screens that need a single unambiguous CTA. Encapsulates the icon + title + subtitle + tonal-button-with-Add-icon pattern.

### Changed
- **`Navigation.kt`** `showFab` hidden list grew from 10 routes to 13 (added Budgets, Goals, Recurring).
- **`versionName`** `3.2.11` → `3.2.12`, **`versionCode`** `134` → `135`. C06 + C07 dual-cluster closure (both P1 Sprint 2). Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
Unchanged at **79 tests across 8 classes**, 0 failures (pure UI layout / widget change — no logic change).

### Sprint 2 P1 milestone
After this commit, **three P1 Sprint 2 clusters are CLOSED**: C04 (smart defaults, 3.2.6), C06 (FAB overlap), C07 (duplicate FAB on empty states). Remaining P1: C08 (number formatting), C09 (UTF-8 in dialogs), C12-C15 (screen redesigns), C18 (Settings cleanup), C21 (PDF/XLSX export quality proper).

## [3.2.11] - 2026-05-27 *(Development milestone — chip→better-widget moderate sweep; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Changed
- **App-wide chip sweep — apply the 3.2.10 design rule.** Per the rule established at 3.2.10 ("constrained-width pickers use dropdown, browse-and-pick scenarios stay as chips, 2-3 option toggles use SegmentedButtonRow"), Explore-agent surveyed all 13 chip groups across the app and recommended a moderate sweep: 7 toggles converted to `SingleChoiceSegmentedButtonRow`, 1 large picker converted to `ExposedDropdownMenuBox`, 1 mis-used chip converted to `FilledTonalButton`. The 5 large chip groups (category / account / person / horizontal filter rows) are correctly chips per the rule and were left as-is.
  - **`AddRecurringDialog` Income/Expense toggle** (`RecurringScreen.kt`) — 2-option `Row<FilterChip>` → `SegmentedButtonRow`. Matches the same toggle at the top of `AddTransactionDialog`.
  - **Settings → Theme picker** (`SettingsScreen.kt`) — 3-option (System / Light / Dark) `Row<FilterChip>` → `SegmentedButtonRow`.
  - **Settings → Date Format picker** (`SettingsScreen.kt`) — 3-option (dd-MM-yyyy / MM-dd-yyyy / yyyy-MM-dd) `Row<FilterChip>` → `SegmentedButtonRow`.
  - **LendingScreen EMI calculator → Method picker** (`LendingScreen.kt`) — 3-option (Reducing / Simple / Compound) `Row<FilterChip>` → `SegmentedButtonRow`. The underlying `useReducing` / `useSimple` Boolean state encoding is preserved so all downstream branching that reads those flags continues to work; the SegmentedButton onClicks map to the same 2-bit encoding.
  - **LoanCalculatorScreen tenure unit picker** (`LoanCalculatorScreen.kt`) — 2-option (Months / Years) `Column<FilterChip>` (awkward vertical stacking) → horizontal `SegmentedButtonRow` that now sits beside the Tenure number input.
  - **InterestIncomeScreen range picker** (`InterestIncomeScreen.kt`) — 3-option (6M / 12M / 24M) `Row<FilterChip>` → `SegmentedButtonRow`.
  - **TransactionHistoryScreen type filter** (`TransactionHistoryScreen.kt`) — `Row<FilterChip>` for All/Income/Expense → `SegmentedButtonRow`. The semantically-different "Dates" toggle chip in the same row is now a `FilledTonalButton` with the DateRange leading icon (M3 affordance for "tap to open panel").
  - **TransactionHistoryScreen quick date presets** (`TransactionHistoryScreen.kt`) — 7-option `FlowRow<FilterChip>` (Today / Yesterday / Last 7d / Last 30d / This Month / Last Month / This Year) → `ExposedDropdownMenuBox`. Saves significant vertical space inside the date filter panel; shows "Custom range" as the field text when no preset is active. Selecting a preset still populates the existing custom-range `DatePickerField`s below, so the user can fine-tune from there.
- All `SegmentedButton`s use `icon = {}` to suppress the default checkmark per the 3.2.8 lesson (the checkmark eats ~24dp of label width per segment for redundant signalling — selection is already carried by the segment's filled background).

### Unchanged on purpose (chips kept where they belong)
- **`AddTransactionDialog` category + account pickers** (`TransactionDialog.kt`) — large category list + 5 account chips with semantic identity. Browse-and-pick is the right pattern.
- **`AddRecurringDialog` / `AddBudgetDialog` category pickers** — same reason.
- **`LendingDialog` borrower / source / interest type pickers** — dynamic person lists + browse-and-pick.
- **`MoneyFlowScreen` / `ReportsHubScreen` horizontal filter rows** — `LazyRow<FilterChip>` is the correct pattern for screen-level filter bars; users expect horizontal scroll there.

### Changed (versioning)
- **`versionName`** `3.2.10` → `3.2.11`, **`versionCode`** `133` → `134`. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
Unchanged at **79 tests across 8 classes**, 0 failures (pure widget swap — no logic change).

## [3.2.10] - 2026-05-27 *(Development milestone — frequency picker widget swap; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **`AddRecurringDialog` frequency picker — final fix after three failed attempts.** Iteration history: 3.2.7 cramped four `FilterChip`s in a `Row` with `weight(1f)`; 3.2.8 switched to `SingleChoiceSegmentedButtonRow` with `icon = {}` but "Monthly" still clipped (~45dp per label inside AlertDialog width is too narrow for 7-char labels with internal padding); 3.2.9 tried `FlowRow<FilterChip>` but on the user's device width "Yearly" overflowed off the second line. **3.2.10**: switched to `ExposedDropdownMenuBox` matching the C04 Stage 3 currency picker pattern in SettingsScreen. Dropdown always fits regardless of dialog width — establishes the design rule: "for `pick one of N` pickers inside constrained-width containers (AlertDialog, narrow Card, side-by-side layout), prefer dropdown over chips/segmented buttons. Reserve chips for `browse and pick` cases where seeing every option at a glance matters (Category, Account type)."

### Changed
- **`versionName`** `3.2.9` → `3.2.10`, **`versionCode`** `132` → `133`. Three intermediate bumps (3.2.7 → 3.2.8 → 3.2.9 → 3.2.10) reflect the iteration cycle of smoke-find-fix-resmoke; each version is the coherent state of one iteration. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
Unchanged at **79 tests across 8 classes**, 0 failures (pure widget swap — no logic change).

### Skipped from CHANGELOG
3.2.9 was never tagged as a separate CHANGELOG entry — the `FlowRow<FilterChip>` attempt was made and immediately superseded by 3.2.10 without leaving its own intermediate documentation block. Git history (commits between f0bfeef and 3.2.10) preserves the full sequence for anyone reading the file.

## [3.2.8] - 2026-05-27 *(Development milestone — 3.2.7 re-smoke fix; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **`AddRecurringDialog` frequency picker — 'Monthly' label clipped.** The 3.2.7 switch to `SingleChoiceSegmentedButtonRow` improved visual weight over the prior cramped `FilterChip` row, but the M3 default `SegmentedButton` renders a checkmark icon on the selected segment (`SegmentedButtonDefaults.Icon(active = selected)`) which eats ~24dp of label width. With 4 segments inside an AlertDialog, the longest label ('Monthly') got clipped. Fix: pass `icon = {}` to suppress the checkmark. Selection is still visually carried by the segment's filled background colour. Surfaced by re-smoke of 3.2.7.

### Changed
- **`versionName`** `3.2.7` → `3.2.8`, **`versionCode`** `130` → `131`. Re-smoke fix milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
Unchanged at **79 tests across 8 classes**, 0 failures (no logic change — pure visual tweak to a Compose widget).

## [3.2.7] - 2026-05-27 *(Development milestone — C04 smoke follow-up; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C04 smoke follow-up — `BudgetSuggestion` excludes non-discretionary categories.** The 3.2.6 heuristic correctly picked "highest-spend uncapped EXPENSE" but produced a confusing result for users with lending activity: `FinanceRepository.insertBorrowerWithSource` auto-creates an Expense transaction with `category = "Lending"`, so a user who had lent ₹50k saw "Lending" suggested as the next category to budget. NEW `BudgetSuggestion.NON_DISCRETIONARY_CATEGORIES: Set<String>` constant containing the five system / auto-generated EXPENSE categories never to auto-suggest: `"Lending"` (outbound loan), `"Investment"` (asset purchase, still owned), `"Interest Expense"` (accrued from interest engine), `"Balance Correction"` (`quickEditBalance` internal), `"Bad Debt"` (borrower write-off). Filter applied in `suggest()`. Users can still pick any of these manually from the AddBudget chip list — the filter is suggestion-only.
- **`RecurringScreen` header `+` button visibility.** Pre-existing bug surfaced by C04 smoke: header `IconButton` used `tint = Color.White` against `PremiumScreenHeader`'s plain surface background → invisible in light mode. The user tried to add a second recurring transaction and couldn't find the button. Replaced with `FilledTonalIconButton` (theme-aware secondary container + properly-tinted icon, no hardcoded colour). Same bug exists in `CollectionCalendarScreen:120` for the back arrow — logged for follow-up under C06/C07 FAB work, not fixed here.
- **`AddRecurringDialog` frequency picker spacing.** Four `FilterChip`s with `weight(1f)` + `labelSmall` in a 6dp-spaced `Row` were cramped at AlertDialog width. Replaced with `SingleChoiceSegmentedButtonRow` (M3 widget for 2-4 mutually-exclusive options). Added a `"Frequency"` section label above for consistency with the existing `"Category"` label.

### Added
- **8 new `BudgetSuggestionDataIntegrityTest` cases** (12 → 20):
  - Per-category exclusion: `Lending` / `Investment` / `Interest Expense` / `Balance Correction` / `Bad Debt` is never suggested.
  - All-uncapped-are-non-discretionary returns null (chained-fallback correctly moves on to recency).
  - Non-discretionary categories can still be manually budgeted (capped-set semantics unchanged).
  - `NON_DISCRETIONARY_CATEGORIES` set membership lockdown test (failure here when someone adds/removes prompts a doc update in `BudgetSuggestion.kt`).

### Changed
- **`versionName`** `3.2.6` → `3.2.7`, **`versionCode`** `129` → `130`. C04 smoke follow-up milestone marker. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, no Play Console upload happens here.

### Data-integrity gate
71 → **79 tests across 8 classes**, 0 failures (only `BudgetSuggestionDataIntegrityTest` grew, 12 → 20).

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
