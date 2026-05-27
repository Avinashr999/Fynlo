# Changelog

All notable changes to Fynlo are documented here.

## [3.2.52] - 2026-05-27 *(Development milestone — form dialogs widened to 95% screen — Material 3 platform default was too narrow; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **Form AlertDialogs were rendering at ~280dp wide on a 1272dp-wide device (~22% of screen)** — Material 3's `usePlatformDefaultWidth = true` default. User feedback: "no margins and dialog screen was more narrow it have to be compact." Right — the dialog itself was too narrow regardless of content padding; fields inside ended up cramped horizontally even with the verticalScroll fix from 3.2.51.

**Fix applied to all 8 form-shaped AlertDialogs across 8 screens:**
- `BudgetScreen.AddBudgetDialog`
- `GoalScreen.AddGoalDialog`
- `PeopleScreen.PersonDialog`
- `ProjectsScreen.NewProjectDialog`
- `LendingScreen.EmiCalculatorDialog`
- `InvestmentScreen.LogValuationDialog` + `HistoryDialog` (both at once via the replace_all sweep)
- `RecurringScreen.AddRecurringDialog`
- `AccountStatementScreen.EditBalanceDialog`

Each gained two new params:
- `modifier = Modifier.fillMaxWidth(0.95f)` — the dialog itself fills 95% of available screen width instead of the ~280dp Material default. Leaves ~2.5% padding each side.
- `properties = DialogProperties(usePlatformDefaultWidth = false)` — required for the modifier above to take effect; without it the platform-default-width still wins.

Picked 0.95f over 1.0f so there's a visible edge between the dialog and the dim background — purely a visual breathing room thing. Picked over 0.85f / 0.9f because the user explicitly asked for "compact" — the more screen width the dialog claims, the less the content cramps.

### Lesson logged
Two-step rule for Material 3 AlertDialog form dialogs:
1. Wrap the form Column in `Modifier.verticalScroll(rememberScrollState())` (lesson from 3.2.50–3.2.51).
2. Set `modifier = Modifier.fillMaxWidth(0.95f)` + `properties = DialogProperties(usePlatformDefaultWidth = false)` (this lesson — 3.2.52).

Without both, form dialogs either clip vertically OR cramp horizontally. Candidate for `LINT_RULES.md` as a Material 3 AlertDialog hygiene rule.

### Changed
- **`versionName`** `3.2.51` → `3.2.52`, **`versionCode`** `174` → `175`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures.

## [3.2.51] - 2026-05-27 *(Development milestone — cross-dialog verticalScroll sweep — applying the AddRecurring lesson to every other form dialog proactively; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **Applied the 3.2.50 `verticalScroll` lesson preemptively to 5 other form-shaped AlertDialogs** so they don't re-discover the same content-clipping bug. User feedback after AddRecurring closure: "match the recurring transaction dialog like remaining dialog in different screens to make experience universal as it was our moto and i think we forgot." Right call — fixing once and sweeping is better than waiting for each dialog to fail on smoke.

**Six form-shaped AlertDialogs swept** (every one with a `text = { Column(...) { OutlinedTextField + ... } }` body containing > 3 inputs):
- `RecurringScreen.AddRecurringDialog` (fixed in 3.2.50)
- `BudgetScreen.AddBudgetDialog` — chip picker + custom category + limit + disabled hint
- `GoalScreen.AddGoalDialog` — 4 OutlinedTextFields (name + target + saved + deadline) + disabled hint
- `PeopleScreen.PersonDialog` — country code dropdown + name + phone + (optional ID) + disabled hint
- `ProjectsScreen.NewProjectDialog` — name + currency + color picker grid + disabled hint
- `InvestmentScreen.LogValuationDialog` — name + value + date + notes
- `LendingScreen.EmiCalculatorDialog` — principal + rate + tenure + EMI-method segmented + (optional compound due-date) + result panel — the tallest form in the app

**Each fix is one line** — wrap the form Column in `Modifier.verticalScroll(rememberScrollState())` and add the two imports.

**Skipped intentionally** (already-scrolling or short confirms):
- All confirm-style AlertDialogs (Delete X, Wipe ALL, Logout, etc.) — short single-Text body, no scroll needed.
- `InvestmentScreen.HistoryDialog` — has a `LazyColumn` inside which scrolls itself.
- `SettingsScreen` dialogs — all confirms.
- Form-shaped `Dialog`-based composables (TransactionDialog, LendingDialog, DebtDialog, PaymentDialog, InvestmentDialog) — they use raw `Dialog` not `AlertDialog`, with their own scroll handling. Not affected by AlertDialog's text-slot non-scroll behaviour.

### Lesson promoted to a rule
The `AlertDialog.text` slot does NOT auto-scroll in Material 3 / Compose. Any form with > 3 inputs (where input includes OutlinedTextField, SegmentedButtonRow, FlowRow of chips, OR composite blocks like color picker grids) must wrap its Column in `verticalScroll(rememberScrollState())`. Worth adding to `LINT_RULES.md` so future dialog work doesn't re-discover this.

### Changed
- **`versionName`** `3.2.50` → `3.2.51`, **`versionCode`** `173` → `174`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only modifier additions across 6 files).

## [3.2.50] - 2026-05-27 *(Development milestone — AddRecurring form wrapped in verticalScroll so bottom items aren't clipped (3.2.49 was still broken); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **AddRecurring form was clipping below "Frequency".** Third smoke surface in a row for this dialog. 3.2.49 swapped the Frequency picker from dropdown to SegmentedButtonRow but user screencap still showed only the "Frequency" label rendering — the 4 segmented chips, the "Use last day of month" checkbox, the day-of-month input, and the "Next occurrences" preview were all clipped. The AlertDialog text slot doesn't auto-scroll in this Material 3 + Compose version, so a tall form just overflows invisibly.
- **Fix: wrap the form Column in `verticalScroll(rememberScrollState())`.** Total form height is ~700dp; AlertDialog text slot is ~400dp; everything past the visible viewport is now reachable via swipe. Confirmed working via device screencap — all 4 Frequency chips render, the checkbox + day input + preview are visible after a scroll.

### Lesson logged
- **AlertDialog's `text` slot doesn't scroll its content** in Material 3 / Compose currently. If your form has > 6 inputs, wrap the Column in `verticalScroll(rememberScrollState())` explicitly or use a `Dialog` instead. This bites doubly when you only screencap the visible viewport in dev and miss that everything below is clipped.

### Changed
- **`versionName`** `3.2.49` → `3.2.50`, **`versionCode`** `172` → `173`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (1-line scroll wrap).

## [3.2.49] - 2026-05-27 *(Development milestone — AddRecurring Frequency dropdown → SegmentedButtonRow (3.2.48 was still broken); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **AddRecurring Frequency dropdown was rendering as a collapsed line.** 3.2.48 added a `supportingText` + bigger trailing arrow to the `ExposedDropdownMenuBox` but a user screencap showed the field still rendered with **only the chevron visible** — label "Frequency", value "Monthly", and the new "Tap to choose" supportingText were all clipped. The dropdown's OutlinedTextField was collapsing to ~0 height inside the AlertDialog text slot for reasons we don't fully understand (maybe Material 3 + AlertDialog + readOnly + supportingText layout interaction).
- **Replaced the dropdown with a `SingleChoiceSegmentedButtonRow`** of 4 segments (Daily / Weekly / Monthly / Yearly) above the day-of-month field. Same pattern as the Income/Expense segmented control at the top of the same dialog. Pre-3.2.49 the 3.2.10 history said 4 labels wouldn't fit; smoke proved the dropdown failure mode is worse, and modern Compose `SingleChoiceSegmentedButtonRow` with `maxLines = 1` + small `labelSmall` font handles the 4 labels fine. Worst case "Yearly" truncates to "Yearl…" on narrow devices — still legible.
- Added a small "Frequency" label above the row so the affordance is clear.

### Lesson logged
- `ExposedDropdownMenuBox` doesn't always render reliably inside an AlertDialog text slot — when smoke shows it collapsed, fall back to a SegmentedButtonRow rather than adding more `supportingText` patches. Visible-by-default chips beat a clipped picker.

### Changed
- **`versionName`** `3.2.48` → `3.2.49`, **`versionCode`** `171` → `172`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI swap only).

## [3.2.48] - 2026-05-27 *(Development milestone — RecurringScreen FAB + AddRecurring dropdown discoverability (3.2.47 smoke surfaces); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **RecurringScreen populated state had no FAB.** Smoke surface for 3.2.47. Every other list screen in the app (Lending, Debt, Goal, Spend, People) has a bottom-right FloatingActionButton when populated; Recurring only had a small `+` IconButton in the header (added by C07 with the rationale of "no triple entry point" — but the populated-state-only FAB doesn't conflict). Added the matching FAB inside the `else` (populated) branch of the screen Box; empty state still uses the inline EmptyState CTA exclusively.
- **AddRecurring Frequency picker was mistaken for a static label.** Smoke surface for 3.2.47. Pre-3.2.48 the `ExposedDropdownMenuBox` rendered an `OutlinedTextField` with a small default trailing arrow — visually indistinguishable from a normal text field, so users didn't realize they could tap it. Two changes:
  - `supportingText = { Text("Tap to choose") }` renders a helper line below the field.
  - Trailing icon swapped from the default tiny `ExposedDropdownMenuDefaults.TrailingIcon` to a 28dp `KeyboardArrowDown` (or `KeyboardArrowUp` when expanded) tinted primary-emerald. Picker affordance is now unambiguous.

### Lesson logged
- ExposedDropdownMenuBox's default trailing icon is too small to read as "this is a picker." When a dropdown lives inside an AlertDialog with multiple OutlinedTextFields above it, the user pattern-matches every TextField as input-only. Either:
  - Use a SegmentedButtonRow (preferred when ≤4 options fit horizontally) — but the 3.2.10 finding said 4-label Frequency didn't fit.
  - Or upgrade the dropdown's visual affordance: supportingText + larger primary-tinted arrow. Done here.

### Changed
- **`versionName`** `3.2.47` → `3.2.48`, **`versionCode`** `170` → `171`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only).

## [3.2.47] - 2026-05-27 *(Development milestone — C22 Stage 2: Recurring last-day-of-month + preview + Goals deadline; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C22 Stage 2 — 3 P3 audit items in 2 surfaces (audit items #218 last-day-of-month, #220 preview next occurrences, #207 Goals target-date). End-date #219 deferred — needs Room migration v17→v18; will land in its own commit.**

**`RecurringWorker.shouldRunToday` — last-day-of-month support (audit #218):**
- Pre-Stage-2: monthly recurrings fired only when `today.dayOfMonth == r.dayOfMonth` exactly. Months shorter than the target (Feb 28, Apr 30, etc.) silently skipped the run.
- Now: clamps the target to the month's actual length — `targetDay = minOf(r.dayOfMonth, today.lengthOfMonth())`. A user who picks day 31 gets:
  - Feb 28 / Feb 29 in leap years (the actual last day)
  - Apr / Jun / Sep / Nov: day 30
  - Jan / Mar / May / Jul / Aug / Oct / Dec: day 31
- Days 1-27 unchanged (always fire on the exact target day, no clamping).
- Yearly recurrings get the same clamp.

**`RecurringScreen.AddRecurringDialog` — last-day toggle + preview (audit #218 + #220):**
- New "Use last day of month" checkbox above the day-of-month input. Sets the value to 31 (worker's clamp does the rest) and disables the input. Unchecking restores to "1".
- Day-of-month input range widened from 1-28 → 1-31. Input validation rejects values outside that range as the user types. Confirm-button save also clamps to 1-31.
- New "Next occurrences" caption below the day input — shows the next 3 firing dates given (frequency, dayOfMonth). Pure-UI computation; rebuilds via `remember(frequency, dayOfMonth)`. Helps the user sanity-check before saving, especially useful for "last day of month" so they see how Feb shifts to the 28th.

**`GoalScreen.AddGoalDialog` — target-date field (audit #207):**
- `Goal.deadline` field already existed in the model (`""` default — yyyy-MM-dd or blank). Pre-Stage-2 the dialog never exposed it; now there's a 4th OutlinedTextField "Target Date (yyyy-MM-dd, optional)" with a placeholder.
- `GoalCard` shows the deadline below the progress bar when non-blank: "Target by 31 Dec 2026" (formatted via `DateUtils.formatToDisplay`).

### Deferred: Recurring end-date (audit #219)
- Adding `endDate: String` to the `RecurringTransaction` entity requires a Room migration v17 → v18 + a schema export under `app/schemas/` + a new `FynloDatabaseMigrationTest` case. Treating it as a separate stage so it stays isolated from the UI-only changes here.

### Closes
- **C22 audit items #218, #220, #207 (this stage).** #219 deferred (schema migration).

### Changed
- **`versionName`** `3.2.46` → `3.2.47`, **`versionCode`** `169` → `170`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only + 1 worker logic clamp; no schema change).

## [3.2.46] - 2026-05-27 *(Development milestone — About Resources row chevron visibility fix (3.2.45 smoke surface); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **AboutScreen Resources rows — chevron icon was barely visible.** Smoke surface for 3.2.45. The trailing `Icons.AutoMirrored.Filled.OpenInNew` icon was tinted with `MaterialTheme.colorScheme.outlineVariant` — that token is intended for hairline dividers (~3.5:1 contrast against the row's surfaceVariant background) and the chevron at 16dp was essentially invisible. Bumped to `onSurfaceVariant` (the standard "secondary icon/text" tone) and size 16dp → 18dp for parity with the leading icon. Chevron is now clearly visible.

### Lesson logged
- `outlineVariant` is for hairlines, not icons. When you want a secondary-but-visible icon, use `onSurfaceVariant`. Documented inline at the touch site.

### Changed
- **`versionName`** `3.2.45` → `3.2.46`, **`versionCode`** `168` → `169`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (1-line tint + size change).

## [3.2.45] - 2026-05-27 *(Development milestone — C22 Stage 1: About-screen Resources section (Privacy / Licenses / Changelog); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C22 Stage 1 — About-screen quick wins (audit C22 items #254 Privacy Policy URL, #256 Open Source licenses, #257 Changelog link; #258 proper-logo deferred).** First slice of the P3 v4+ backlog. Opens the cluster with a small contained change before tackling the larger items (OCR, AI categorization, bank statement parse, etc.).

**`LICENSES.md` — new file at repo root:**
- Static list of major OSS dependencies with their licenses (Apache 2.0 / EPL 1.0 / MIT) + canonical-text URLs. Categorized by area (Kotlin, Android Jetpack, DI, Firebase, etc.). Required by the audit's "Open Source licenses" point so users can audit the supply chain.
- Issue-tracker URL at the bottom for users to flag omissions.

**`AboutScreen` — new "Resources" section:**
- Three clickable rows below the Legal Disclaimer card, each opening a GitHub URL in the user's browser via `ACTION_VIEW`:
  - **Privacy Policy** (Shield icon) → `https://github.com/Avinashr999/Fynlo/blob/master/PRIVACY_POLICY.md` (file already existed in the repo, just unlinked from the UI).
  - **Open Source Licenses** (MenuBook icon) → `https://github.com/Avinashr999/Fynlo/blob/master/LICENSES.md` (newly created).
  - **Changelog** (History icon) → `https://github.com/Avinashr999/Fynlo/blob/master/CHANGELOG.md` (already existed).
- New private `AboutLinkRow(icon, label, onClick)` composable mirroring `SettingsActionRow` shape — leading icon + label + trailing `AutoMirrored.Filled.OpenInNew` chevron. Whole row tappable.
- `runCatching { context.startActivity(...) }` so an absent browser silently no-ops rather than crashing.

### Audit #258 (proper logo) deferred
- The current logo is `Icons.Default.AccountBalanceWallet` rendered as a Material icon. A proper logo requires an actual drawable asset (PNG / vector) which isn't part of this commit's scope. Logged as a deferred follow-up — the audit's point is valid but blocked on the design side.

### Closes (partial — C22 is the v4 backlog cluster)
- **C22 audit items #254, #256, #257** (this stage). #258 deferred (asset blocker).

### Changed
- **`versionName`** `3.2.44` → `3.2.45`, **`versionCode`** `167` → `168`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only).

## [3.2.44] - 2026-05-27 *(Development milestone — C20 closed: drawer cleanup — **closes P2 backlog**; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C20 — Drawer cleanup (audit #181, #182, #183, #184, #185, #186). Closes C20 + closes the P2 backlog (6 of 6 P2 clusters now done).**

**Compact header (audit #1 + #2 + drawer-user-identifier):**
- Pre-C20 the header was a 52dp `AccountBalanceWallet` icon + "Fynlo" headline + "Personal Finance Manager" tagline, total ~120dp tall (~25% of drawer vertical).
- Now: a single 40dp Person-icon avatar + signed-in user's name + email in one Row. Tagline dropped (it's on the About screen). The user's name + email come from `AuthManager.userName / userEmail`; falls back to `"Signed in"` / `"Tap Profile to sign in"` for the anonymous case.
- Photo-avatar rendering via `AuthManager.userPhoto` deferred — needs an image-loading library (Coil); not part of the audit spec. Person icon in emerald circle is the audit-default.

**Frequency-ordered flat list (audit #3 + #5):**
- Pre-C20 the items were grouped by semantic label (`ACCOUNT` / `FINANCE TOOLS` / `APP`) — the audit calls that out as not aligned with usage frequency.
- Now: flat list ordered by frequency, with one divider between the top group and the rest. Top group (primary emerald tint via new `accent = true` flag on `DrawerItem`): **Settings · Profile & Security · Budgeting · Savings Goals · Contact Book**. Bottom group (grey, secondary): Recurring Transactions · Manage Projects · EMI Calculator · About & Disclaimer. Logout stays at the very bottom, red.
- Section-label uppercase headers gone. The frequency ordering + tint difference does the same affordance work without the visual weight.

**Removed duplicate "Investments" drawer entry (audit #4):**
- The drawer's "Investments" entry pointed to `Screen.Invest.route` — the SAME route the bottom-nav "Invest" tab navigates to. Drawer entry removed to eliminate the duplicate and frees a slot. Reduces the audit's icon-mismatch concern at the same time (drawer used `AutoMirrored.Filled.ShowChart`, bottom-nav uses `AutoMirrored.Filled.TrendingUp` — no longer a contradiction since the drawer entry is gone).

**`DrawerItem` API addition:**
- New `accent: Boolean = false` param. When true (and not selected), renders the icon in primary emerald and keeps the label in onSurface. Used for the top-5 frequently-used items so they read as "this is the main stuff" without going as far as the full selected-state fill background.
- Param ordering: `(icon, label, selected, accent, onClick)` — `onClick` last so the trailing-lambda convention still binds correctly.

### Pattern lessons logged

- **Trailing-lambda binding** — when adding a new `Boolean = false` param to a Composable that callers invoke with a trailing lambda, the new param must go BEFORE the lambda param. First attempt put `accent` after `onClick` — callers' trailing `{ ... }` then bound to `accent` (type mismatch) instead of `onClick` (missing value).
- **Package-path shadowing** — fully-qualified `app.fynlo.data.AuthManager()` failed to resolve inside a Composable scope that already had `val app = context.applicationContext as app.fynlo.FynloApplication`. The local `val app` shadows the package root. Fix: add a direct `import app.fynlo.data.AuthManager` and call it unqualified. Same pattern surfaced in C10's ReminderWorker — worth a general lesson: when a screen has `val app = ...`, never fully-qualify with `app.fynlo.*` from that scope; use imports.

### Closes
- **C20 audit fixes #181–#186. P2 backlog closed.**
- Six P2 clusters now done: C10 (3.2.39), C11 (3.2.40), C16 (3.2.41), C17 (3.2.42), C19 (3.2.43), C20 (3.2.44).

### Changed
- **`versionName`** `3.2.43` → `3.2.44`, **`versionCode`** `166` → `167`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only restructure).

### Remaining work after C20
| | |
|---|---|
| ⏳ P3 | C22 v4+ backlog (required before first public release per release-cadence ADR) |
| ⏳ Breaking schema | C03b |
| ⏳ Infra | INF01–INF06 |
| ⏳ Deferred features | Task #24 (EMI features), #26 (Report-a-Bug form), #27 (C13 features), #28 (C14 features), #22 (NetWorthWidget DataStore) |

Per the release-cadence ADR no Play Console upload happens until **all** of that closes, including C22.

## [3.2.43] - 2026-05-27 *(Development milestone — C19 closed: empty-state standardization across 3 remaining surfaces; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C19 — Empty states standardization (audit #33, #38, #95, #130, #196, #212, #256).** Fifth P2 cluster closed.

Most empty surfaces already used the shared `app.fynlo.ui.theme.EmptyState(icon, title, subtitle, actionLabel, onAction)` composable per C07's earlier sweep — GoalScreen, RecurringScreen, BudgetScreen all routed through it. C19 cleans up the three remaining outliers the audit explicitly called out.

**`PeopleScreen` — bespoke `EmptyPeopleState` migrated:**
- Was a hand-rolled Column with a 64dp `Icons.Default.Person` icon at `onSurfaceVariant.copy(alpha = 0.4f)`, hand-styled title + body Text, and a plain `Button` CTA. Replaced by a thin wrapper around the shared `EmptyState` composable so styling and CTA shape match every other empty surface.
- **Redundant sub-header removed** when the list is empty. Pre-C19 the screen showed "Contacts link loans to people and enable WhatsApp / SMS reminders." above the LazyColumn AND a similar sentence in the empty-state body — the audit flagged that as "redundant double-explanation." Now the sub-header only renders when `people.isNotEmpty()`; the empty-state body carries the explanatory copy on its own.

**`MoneyFlowScreen` filtered-empty — promoted from plain text:**
- Was a bare "No flows found" Text. Promoted to a 48dp `Icons.Default.SwapHoriz` icon + filter-aware copy: "No flows yet / Log a transaction to see it appear here." when no filter is active, "No [tab] flows / Switch to All to see other flow types." when a filter is active.
- Used the empty-state Column shape directly rather than the shared composable since the shared `EmptyState` uses `fillMaxSize` and would swallow the rest of the screen. The visual style (icon at outlineVariant, body in onSurfaceVariant + outlineVariant) matches the shared composable.

**`InvestmentScreen.HistoryDialog` Valuation History — got an illustration:**
- Pre-C19: `"No records found"` plain bodySmall text inside the AlertDialog body. Audit called out "Valuation History empty: no illustration."
- Now: 40dp `Icons.Default.History` icon at outlineVariant + 2-line body ("No valuation history yet / Tap 'Update Value' on the investment to start tracking..."). AlertDialog body is space-constrained so we don't use the full shared `EmptyState` (which has a larger icon + button), but the visual style matches the other empty states.

### Audit items verified as already-correct
- **#33 Hand Loans empty (sparse text-only)** — no longer applies; the Interest/Hand TabRow was replaced by the Active/Overdue/Closed segmented filter in C12 Stage 2 (3.2.27). The per-segment empty-state messages all use a Box-centered Text inside the LazyColumn item; the filter context makes them informative ("No overdue loans — you're up to date 🎉" / "No closed loans yet"). Not a polish issue anymore.
- **#212 Budgeting empty (no CTA)** + **#196 Recurring "Add First" with mint chip** — both already route through `theme.EmptyState` per C07's sweep.
- **#256 Money Flow full placeholder with no CTA** — C15e (3.2.33) gave the screen real content; only the filtered-empty case (no flows match the active filter) remained as a plain-text empty, and that's what landed here.

### Closes
- **C19 audit fixes #38, #95, #130** (the 3 fixed here). #33, #196, #212, #256 verified as already-correct or no-longer-applies.

### Changed
- **`versionName`** `3.2.42` → `3.2.43`, **`versionCode`** `165` → `166`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only).

## [3.2.42] - 2026-05-27 *(Development milestone — C17 closed: DisabledButtonHint(reason) composable + 9-site sweep; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C17 — Disabled button hints (audit #18, #169, #203, #210, #232, #233).** Fourth P2 cluster closed.

**`DisabledButtonHint(reason: String?)` — new composable:**
- Renders a small 11sp `onSurfaceVariant` centered label below the button when the reason is non-null.
- When reason is null, renders a 14dp reserved spacer so the button column doesn't shift up as the user fills the missing field — no layout jump on the primary action.
- Companion-style usage: caller computes the disabledReason inline as a `when`-expression, passes it to both `enabled = reason == null` and `DisabledButtonHint(reason)`.

**Nine sites swept:**
1. **`TransactionDialog`** — was `enabled = amount > 0`; now also gates on category being picked + (Custom category requires text). Reasons: "Enter an amount to continue" / "Pick a category to continue" / "Type a custom category to continue".
2. **`LendingDialog`** — was `enabled = (selectedPerson != null || isEdit) && amount > 0`. Reasons: "Pick a borrower to continue" / "Enter the loan amount to continue".
3. **`DebtDialog`** — was `enabled = isValid` where `isValid = lenderName.isNotBlank() && amount.isNotEmpty()`. Reasons: "Enter the lender's name to continue" / "Enter the borrowed amount to continue".
4. **`InvestmentDialog`** — refactored the existing `canSave` into a deterministic `disabledReason: String?` first; canSave just checks reason for null. Reasons branch on `sourceType` (account / existing debt / new loan) so the hint matches whichever source picker is showing.
5. **`PaymentDialog`** (CollectPaymentDialog + PayDebtDialog) — both use `isValid = totalAmount > 0.0`. Same hint: "Enter an amount to continue".
6. **`BudgetScreen.AddBudgetDialog`** — reasons: "Pick a category to continue" / "Enter a positive monthly limit to continue". Hint sits inside the dialog's `text` slot (above the buttons) since AlertDialog's confirmButton slot only accepts a single button composable.
7. **`PeopleScreen`** Add/Edit Person — reason: "Enter a name to continue".
8. **`ProjectsScreen`** New Project — reason: "Enter a project name to continue".
9. **`RecurringScreen`** Add Recurring — reason: "Enter a name to continue".

**Pattern lesson:** AlertDialog's slot API doesn't let you put a hint inline beneath the confirm button (the slot expects exactly one button). For dialog-based forms the hint goes inside the `text` slot as the last item. For Dialog-based forms (TransactionDialog, LendingDialog, etc., which use raw `Dialog`/`Card` composition), the hint sits directly under the button.

### Closes
- **C17 audit fixes #18, #169, #203, #210, #232, #233.** Fourth P2 cluster closed.

### Changed
- **`versionName`** `3.2.41` → `3.2.42`, **`versionCode`** `164` → `165`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (UI-only addition; no logic / state-shape change).

## [3.2.41] - 2026-05-27 *(Development milestone — C16 closed: color semantics fixes — Outstanding emerald on Lent / project active-indicator radio; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C16 — Color semantics violations.** Third P2 cluster closed.

**`LendingScreen.LendingCard` — Outstanding column colour (audit #1):**
- Pre-C16: `if (isOverdue) SemanticRed else MaterialTheme.colorScheme.onSurface` — neutral on normal state didn't signal that Outstanding is a receivable (an asset).
- Now: `if (isOverdue) SemanticRed else Emerald500`. Asset-semantic colouring matches the LoansHubScreen Lent-tab hero (which already uses Emerald) so the colour story is consistent from list row → tab hero.

**`CustomerDetailScreen` hero Current Balance — flipped (audit #1):**
- Pre-C16: `if (totalOutstanding > 0) MaterialTheme.colorScheme.error else Emerald500` — positive outstanding rendered red as if it was a debt. But the user lent the money; outstanding is an asset they expect back.
- Now: `if (totalOutstanding > 0) Emerald500 else MaterialTheme.colorScheme.onSurface`. Positive renders green (asset still on the books), zero renders neutral (loan fully repaid — no celebration colour, just done).

**`ProjectsScreen` active indicator (audit #5):**
- Pre-C16: `Icons.Default.CheckCircle` tinted in `MaterialTheme.colorScheme.primary` (Emerald) — the green check read as an "income confirmation" rather than a selection state, especially next to currency rows that use green for positive values.
- Now: `Icons.Filled.RadioButtonChecked` — same emerald tint but the shape signals selection unambiguously.

### Audit items verified as already-correct or skipped with rationale
- **#2 Negative investment growth** — `InvestmentScreen` already renders growth with sign-aware coloring (`if (growth >= 0) Emerald500 else SemanticRed`) in both list rows (line 638) and the Gain/Loss card (line 520). No change needed.
- **#3 Restore button** — `CustomerDetailScreen` Mark NPA / Restore Active toggle already uses `Emerald500` on the Restore path and `SemanticAmber` on the Mark-NPA path (line 466). `SettingsScreen` Restore Real Data confirm button uses theme primary (not red). No change needed.
- **#4 Load Test Data** — the row icon is `Amber` (neutral-warning, not red). The confirm-dialog button is red because the action is destructive — same pattern as Cleanup Seeder + Wipe ALL. Kept consistent with destructive-confirm convention.
- **#6 Wallet / budget icons mixed yellow/red** — the only Wallet icon usages are account-type identifiers in account-picker rows (cash account icon). Not a semantic state indicator. Skipped without a more specific signal from the audit.

### Closes
- **C16 audit fixes #1 and #5** (this commit). Audits #2, #3, #4 verified as already-correct. #6 skipped with rationale.

### Changed
- **`versionName`** `3.2.40` → `3.2.41`, **`versionCode`** `163` → `164`.

### Data-integrity gate
Unchanged at **137 tests across 12 classes**, 0 failures (pure colour-token change; no logic or data path touched).

## [3.2.40] - 2026-05-27 *(Development milestone — C11 closed: DateUtils.format(date, Style) API + dateFormat pref threaded into exports + XLSX "Loan Date" → "Lent On"; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C11 — Date formatting inconsistency (audit #302, #307, #308, #309, #312, #325, #359, #360).** Second P2 cluster closed.

**`DateUtils.format(date, Style)` — formal 4-style API per DESIGN_SYSTEM §8.3:**
- `Style.Relative` — "today" / "yesterday" / "tomorrow" / "N days ago" / "in N days" / "N weeks ago" / falls back to Compact at > 8 weeks. Uses the new shared `pluralize` helper from C10 so the day/week counts pluralize correctly.
- `Style.Compact` — user's chosen pattern from Settings (`dd-MM-yyyy` / `MM-dd-yyyy` / `yyyy-MM-dd`). Caller passes `compactPattern: String` explicitly. Default falls back to `dd-MM-yyyy` (the in-app default).
- `Style.Definitive` — `d MMM yyyy` (e.g., `25 May 2026`). Used on receipt/statement surfaces where locale-agnostic month names eliminate ambiguity.
- `Style.ChartAxis` — `MMM d` (e.g., `May 25`). Used on chart x-axis labels.
- Malformed input → returns verbatim (defensive: don't crash a UI render over a malformed legacy row).
- Malformed compact pattern → falls back to default (Settings only stores 3 valid options, but defensive nonetheless).
- Formatter cache so repeated calls don't rebuild `DateTimeFormatter` instances.
- `LocalDate` variant + `String` (ISO `yyyy-MM-dd`) variant — both go through the same internal `format(LocalDate, ...)` core.

**Back-compat preserved:**
- `DateUtils.formatToDisplay(dateStr)` and `DateUtils.parseInput(...)` still work; `formatToDisplay` delegates to `format(dateStr, Style.Compact)` with the default pattern. Existing in-app call sites compile and behave identically; new code should use `format` directly so the style intent is explicit.

**Threading user dateFormat preference through exports:**
- `ExportUtility.generatePDF` / `generateMoneyFlowPDF` / `generateLoanStatementPDF` gained `dateFormat: String = DateUtils.DEFAULT_COMPACT_PATTERN` param. All PDF date cells (`borrower.date`, `borrower.due`, `debt.date`, `debt.due`, `investment.date`, `transaction.date`, `loanPayment.date`, `flow.date`) now reformat ISO → user pattern via `DateUtils.format(..., Style.Compact, dateFormat)`.
- `ExcelExportUtility.generateFullBackup` gained `dateFormat` param + an internal `dt(iso)` helper (named `dt` not `d` to avoid shadowing the `debts.forEach { d -> }` lambda parameter). Every date cell across Transactions / Lending / Debts / Investments / Loan Repayments / Debt Repayments sheets reformats via `dt(iso)`.
- `FinanceViewModel.exportToPDF` / `exportToXLSX` gained the param too; SettingsScreen's Export PDF + Export XLSX launchers pass the user's `dateFormat` collected via `UserPreferences.dateFormat(context).collectAsState()`.
- Per-screen Export PDF buttons (ReportsHubScreen, ProfitLossScreen, CustomerDetailScreen, MoneyFlowScreen) collect `UserPreferences.dateFormat(...)` and thread it through.

**XLSX label fix (audit "Loan Date" vs "Lent On"):**
- Lending sheet header `"Loan Date"` → `"Lent On"` so the XLSX matches the in-app label and the PDF loan statement column. Same field, same name everywhere.

**`DateUtilsDataIntegrityTest` — new test class, 15 cases:**
- Compact respects default + override patterns (×3).
- Definitive renders with 3-letter month name (×2 — May + Jan).
- ChartAxis renders as month-and-day (×2).
- Relative: today / yesterday / tomorrow / 2-6 day range (×2) / 7-56 day weeks range (×3) / falls back to compact at > 8 weeks.
- Malformed input returns verbatim + back-compat formatToDisplay.

### Deferred follow-up
**In-app date rendering still uses the default `dd-MM-yyyy`.** Every `DateUtils.formatToDisplay(dateStr)` call site in Composables (LendingScreen rows, transaction list rows, etc.) keeps the back-compat default. Making in-app rendering respect the user pref needs either:
- A `LocalDateFormatPattern` `CompositionLocal` initialized at the root + a Composable-aware `rememberFormatDate(dateStr)`, OR
- A static cache (`DateFormatCache.current`) loaded at app init + invalidated on Settings save.

Either approach is mechanical but touches every Composable site that renders a date. Logged as a follow-up. The bulk of audit C11 — exports and the formal API — closes here.

### Closes
- **C11 audit fixes #302, #307, #308, #309, #312, #325, #359, #360.** Second P2 cluster closed.

### Changed
- **`versionName`** `3.2.39` → `3.2.40`, **`versionCode`** `162` → `163`.

### Data-integrity gate
**+15 tests from `DateUtilsDataIntegrityTest`.** Total now **137 tests across 12 classes**, 0 failures (was 122/11 before C11).

## [3.2.39] - 2026-05-27 *(Development milestone — C10 closed: shared Pluralize helper + 13-site sweep; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C10 — Pluralization bugs (audit #117, #138, #152).** First P2 cluster closed. Replaces ad-hoc inline `${if (n != 1) "s" else ""}` patterns + literal `(s)` strings with a single shared helper used at every count-render site in the app.

**`app.fynlo.logic.Pluralize` — new helper:**
- `object Pluralize` with `pluralize(count, singular, plural?)` + `pluralNoun(count, singular, plural?)`. Default plural is `singular + "s"`; irregular plurals via explicit second arg (`pluralize(1, "child", "children")` / `pluralize(2, "entry", "entries")`).
- Top-level `pluralize(...)` + `pluralNoun(...)` shorthand functions for ergonomic call sites — most usages need only the top-level.
- `Int` AND `Long` overloads. Long is the common case for day-counts from `ChronoUnit.DAYS.between(...)`.

**Migration: 13 call sites swept** to the shared helper:
- `DebtPayoffScreen` "Across N active debt(s)" — the literal "(s)" bug. → `pluralize(N, "active debt")`.
- `AccountStatementScreen` "N transactions" → `pluralize(N, "transaction")`.
- `GlobalSearchScreen` "N results for ..." → `pluralize(N, "result")`.
- `InvestmentScreen` "N holdings" (two sites) → `pluralize(N, "holding")`.
- `SpendScreen` "N transactions" → `pluralize(N, "transaction")`.
- `TransactionHistoryScreen` "Delete N transactions?" + "N entries" → `pluralize(N, "transaction")` + `pluralize(N, "entry", "entries")`.
- `ExportUtility` PDF section header "All Flows (N entries)" → `pluralize(N, "entry", "entries")`.
- `CollectionCalendarScreen` "N day(s) overdue" + "Due in N day(s)" (Long counts) → `pluralize(days, "day")`.
- `CustomerDetailScreen` WhatsApp message templates "N day(s) overdue" + "(due in N day(s))" + "N payment(s)" → all via shared helper.
- `DebtDetailScreen` "N payment(s)" → `pluralize(N, "payment")`.
- `PinScreen` "Wrong PIN. N attempt(s) left." → `pluralize(N, "attempt")`.
- `ReminderWorker` (background notifications) "overdue by N day(s)" + "due in N day(s)" (4 sites; 2 borrower, 2 debt) → `pluralize(days, "day")`.

**Migrated `NetWorthHistoryScreen` screen-local helper to the shared one:**
- C15c's `pluralCount(n, "snapshot", "snapshots")` (3.2.31) replaced with `pluralize(n, "snapshot")`. The screen-local helper deleted. Now only one pluralization implementation app-wide.

**`PluralizeDataIntegrityTest` — new test class, 8 cases:**
- Singular at count == 1; plural at 0, 2+, negative; default plural = `singular + "s"`; explicit irregular plurals; `pluralNoun` variant; zero is plural. All bounded; no Hilt / Room / coroutine setup required.

### Localization follow-up
- Audit's "use Android `<plurals>` resources" is **deferred**. The app's strings live in Compose `Text(...)` calls rather than `string.xml`, so a `pluralStringResource(...)` migration would touch every call site anyway. Once localization (currently English-only) becomes a real concern, revisit and promote call sites to `<plurals>` for proper CLDR pluralization rules in other languages. The shared Kotlin helper is structured to make that future migration mechanical (every call site is the same shape).

### Closes
- **C10 audit fixes #117, #138, #152** (this commit). First P2 cluster closed.

### Changed
- **`versionName`** `3.2.38` → `3.2.39`, **`versionCode`** `161` → `162`.

### Data-integrity gate
**+8 tests from `PluralizeDataIntegrityTest`.** Total now **122 tests across 11 classes**, 0 failures (was 114/10 before C10).

## [3.2.38] - 2026-05-27 *(Development milestone — C21 Stage 4: XLSX overhaul — **closes C21 + closes the P1 backlog**; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C21 Stage 4 of 4 — XLSX overhaul (audit fixes C21 #12, #13, #14, #15, #16). Closes C21 in full and closes the P1 backlog.** Fourth and final stage of C21. The XLSX export is now a proper accounting deliverable instead of a tabular text dump.

**ExcelExportUtility — added `Cell.Currency` (audit #12):**
- New `Cell.Currency(val value: Double)` variant. Renders via numFmt code `[$<sym>-409]#,##0.00;[Red]-[$<sym>-409]#,##0.00`. The `<sym>` is read from `CurrencyUtils.symbolFor(currencyCode)` so the symbol matches the active project's currency (₹ / $ / € / etc.). All amount columns migrated from `Cell.Number` → `Cell.Currency`: account balances, transaction amounts, principal/paid on Lending/Debts, Investments invested/current/growth, Loan + Debt Repayment amounts. Rate-% columns stay `Cell.Number` (not currency).

**ExcelExportUtility — added native negative-red formatting (audit #13):**
- The `;[Red]-` half of the currency numFmt makes Excel/Sheets render negative amounts in red natively — no separate `<conditionalFormatting>` block needed. Covers the audit's "negative growth (red)" concern.
- "Mohan Rao-style overdue red row" deferred. The Status column already shows "Overdue" in text, and the Lending sheet has the Status column visible by default. Full per-row conditional fill is nice-to-have but more invasive XML; logged as a deferred follow-up.

**ExcelExportUtility — added frozen first row + auto-filter (audit #14):**
- New per-sheet `Sheet.freezeHeader: Boolean = true` flag emits `<sheetView><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView>` before `<sheetData>`. Header stays visible while scrolling.
- New `Sheet.autoFilterCols: Int = 0` flag emits `<autoFilter ref="A1:<col><lastRow>"/>` after `</sheetData>`. Filter dropdowns appear on every data sheet's header row. Range excludes the totals row so SUM stays visible regardless of filter state.

**ExcelExportUtility — added totals rows (audit #15):**
- New per-sheet `Sheet.totalsCols: List<Int> = emptyList()` flag indicates which column indices (0-based) get a SUM. After the body rows, a final row emits:
  - Leftmost cell → "Total" label in bold (style 4).
  - Each `totalsCols` column → `<c t="n" s="5"><f>SUM(B2:Bn)</f><v>precomputed</v></c>` — bold + currency-formatted SUM formula with the pre-computed value shipped in `<v>` so the file reads correctly before Excel re-evaluates.
- Totals rows applied to: Accounts (Balance), Lending (Principal + Paid), Debts (Principal + Paid), Investments (Invested + Current Value + Growth).

**ExcelExportUtility — added Summary sheet (audit #16):**
- New first sheet in the workbook — opens automatically when the file's launched. 2-column `Metric / Value` layout with 10 KPI rows mirroring the PDF cover:
  - Balance sheet: Net Worth / Total Assets / Total Liabilities / Total Cash / Total Investments / Invest Growth.
  - Activity: Monthly Income / Monthly Expense / Net Cash Flow / Total Lent Out.
- Same cash-basis exclusion as the P&L Statement (financing categories excluded from Monthly Income / Expense). Total Lent Out = lifetime principal (matches C15b audit #4 fix).
- `freezeHeader = true, autoFilterCols = 0` — 2-column metric/value list doesn't need filter dropdowns.

**Metadata sheet now reports currency:**
- Added a `Currency` row alongside the existing `Export type / Generated / Recalculated at` rows so the file's currency code is auditable from inside the workbook.

**`generateFullBackup` signature additions:**
- `summary: FinancialSummary = FinancialSummary()` — feeds the Summary sheet's balance-sheet KPIs.
- `currencyCode: String = "INR"` — drives the currency-symbol numFmt + Metadata row.
- Defaults stay tolerant for callers that haven't migrated; the existing `SettingsScreen` "Export full backup" launcher routes through `viewModel.exportToXLSX` which threads both new params.

### Closes
- **C21 audit fixes #12, #13 (the negative-red half), #14, #15, #16** (this stage).
- **C21 cluster closed in full** — all 4 stages landed: Stage 1 (3.2.35), Stage 2 (3.2.36), Stage 3 (3.2.37), Stage 4 (3.2.38).
- **P1 backlog closed.** Eleven P1 Sprint-2 clusters: C04, C06+C07, C08, C09, C18, C12, C13, C14, C15, C21.
- Deferred: audit #18 (Android PdfDocument framework limit), audit #13 "Mohan Rao red row" (logged as a deferred follow-up — native [Red] negative format handles 95% of intent).

### Changed
- **`versionName`** `3.2.37` → `3.2.38`, **`versionCode`** `160` → `161`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. The new `Cell.Currency` cell type is a render-layer addition; SUM totals are pre-computed at write time so the file's numbers are correct without depending on Excel's formula evaluator.

## [3.2.37] - 2026-05-27 *(Development milestone — C21 Stage 3: PDF charts + 5 new KPI cards; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C21 Stage 3 of 4 — PDF charts + 5 new KPIs (audit fixes C21 #10, #11).** Third of 4 stages closing C21. The cover now opens with 2 rows of KPI cards (was 1 row of 4) followed by three chart panels (asset allocation donut, monthly income/expense bar, net worth trend line).

**ExportUtility — added 5 new KPI cards (audit #11):**
- Row 1 (balance-sheet view, 5 cards across): `NET WORTH | TOTAL ASSETS | TOTAL LIABILITIES | TOTAL CASH | INVEST GROWTH`. `TOTAL LIABILITIES = totalDebtPrincipal + totalDebtInterest`; red when > 0, grey when zero.
- Row 2 (activity view, 4 cards across): `MONTHLY INCOME | MONTHLY EXPENSE | NET CASH FLOW | TOTAL LENT OUT`. Income/Expense are calendar-month, same financing-categories exclusion as the P&L Statement (so a debt receipt doesn't inflate income). `NET CASH FLOW = Monthly Income − Monthly Expense`, signed green/red. `TOTAL LENT OUT = borrowers.sum(amount)` lifetime — matches C15b's audit-#4 fix.
- Card width recomputed for 5-across layout: `(usable / 5)`. Card height unchanged at 44dp. Row 2 left-aligned (4 cards using same width as row 1).

**ExportUtility — added 3 chart panels (audit #10):**
- **`drawAssetAllocationDonut`** — slices of `totalAssets` by Cash (emerald) / Investments (green) / Receivables (blue). Donut hole at 55% inner radius. Legend to the right with category name + amount + percentage. Hidden when `totalAssets` is 0 so a fresh-install PDF doesn't render an empty circle.
- **`drawMonthlyBarChart`** — last 12 months income (green) + expense (red) side-by-side bars. Y-axis labels along the left at 0/50/100% of max; reference grid lines. Month abbreviations along the bottom. Tiny legend underneath. Hidden when every monthly bucket is 0.
- **`drawNetWorthTrendLine`** — line chart connecting `NetWorthSnapshot` points chronologically. Area-fill under the line at 16% alpha. Min/mid/max y-axis labels. Endpoint date labels along the bottom. Empty-state hint ("Not enough snapshots yet — open the app a few more days, or use the in-app Backfill action.") when fewer than 2 snapshots are available.

**ExportUtility.generatePDF — new param:**
- `snapshots: List<NetWorthSnapshot> = emptyList()` — feeds the net-worth trend chart. Default empty for callers that haven't migrated; chart renders the empty-state hint in that case.

**Callers updated (3 sites):**
- **`FinanceViewModel.exportToPDF`** — fetches snapshots via `repository.getNetWorthSnapshots(pid).first()` so the suspend method gets the current list without a side-channel subscription.
- **`ReportsHubScreen`** — added `viewModel.getNetWorthSnapshots().collectAsState(initial = emptyList())` and threads through.
- **`ProfitLossScreen`** — same.

### Page-break handling

Each chart panel calls `checkBreak(panelHeight)` before drawing, so any chart that doesn't fit on the cover page automatically starts on a fresh page. The data tables underneath still chain via existing `checkBreak` logic in `drawTableRow`.

### Stage 4 of C21 still pending

XLSX overhaul (audit #12, #13, #14, #15, #16): currency-format numeric cells, conditional formatting (overdue red / negative red), frozen first row + auto-filter, totals rows on Accounts / Lending / Debts, Summary sheet as first sheet.

### Closes
- **C21 audit fixes #10, #11** (this stage).
- C21 #1, #2, #8, #9 closed in 3.2.35. #3, #4, #5, #6, #7, #17 closed in 3.2.36. #18 accepted as Android framework limit.

### Changed
- **`versionName`** `3.2.36` → `3.2.37`, **`versionCode`** `159` → `160`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. Charts are pure render code; KPI computations route through the same already-validated `FinancialSummary` + transaction filtering paths.

## [3.2.36] - 2026-05-27 *(Development milestone — C21 Stage 2: PDF data correctness — Debts section, word-wrap, dynamic Status, Type column, interest-type default; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C21 Stage 2 of 4 — PDF data correctness (audit fixes C21 #3, #4, #5, #6, #7, #17).** Second of 4 stages closing C21.

**ExportUtility — added new section (audit #3):**
- **Liabilities & Debts** — between Lending and Investments in `generatePDF`. Same column shape as Lending (Creditor / Principal / Paid / Rate / Borrowed / Due / Status / Type) for visual parity. Hidden when caller passes an empty `debts` list, so the section only appears for users with active liabilities.
- New `debts: List<Debt> = emptyList()` param on `generatePDF`. Three caller sites updated:
  - **`FinanceViewModel.exportToPDF`** — threads `debts.value`.
  - **`ProfitLossScreen`** — added `viewModel.debts.collectAsState()` (wasn't collected pre-Stage 2; the P&L screen never used debts in-screen) and threads it.
  - **`ReportsHubScreen`** — added `borrowers`, `debts`, `investments` collectAsState (was passing `emptyList()` for all three pre-Stage 2; the Reports landing PDF only ever included summary + transactions, which was a documented gap). Now produces a comprehensive report matching what `exportToPDF` from the ViewModel does.

**ExportUtility — replaced cell truncation with word-wrap (audit #4):**
- `drawTableRow` previously truncated overflowing cells via `text.take(maxChars - 1) + "…"`. Now uses a new `wrapText(text, paint, maxWidth)` helper that splits the text into lines via `Paint.measureText`:
  - Word-by-word fit. Each candidate line tested before adding the next word.
  - Single words longer than the column fall back to per-character breaks (long filenames / hash-style notes).
  - Row height grows to fit the cell with the most wrap lines.
- Eliminates "Salary Transf…" / "Mohan Ra…" / "Investment Re…" data loss in narrow columns.

**ExportUtility — dynamic Status (audit #5):**
- New `computeBorrowerStatus(b, today)` helper: `WrittenOff → "Written Off"`; `paid >= amount → "Closed"`; `due < today → "Overdue"`; else `"Active"`. Replaces the prior `borrower.status` raw read which could lag reality (borrower with `due < today` and `paid < amount` might still show "Active" if no recompute had run).
- New `computeDebtStatus(d, today)` with the same logic minus the WrittenOff state (debts can't be written off in the current schema).
- Both used by the Lending and Debts tables; status colour green for Closed, red for Overdue / Written Off, black for Active.

**ExportUtility — Recent Transactions title (audit #6):**
- Pre-Stage 2 always said `"Recent Transactions (last 50)"` even when the user only had 9 transactions. Now:
  - `transactions.size <= 50` → `"5. All Transactions (N)"`
  - `transactions.size > 50`  → `"5. Most Recent 50 of N Transactions"`

**ExportUtility — Type column widened (audit #7):**
- Recent Transactions table column widths: Type `10% → 12%`; Description `27% → 26%`; Category `18% → 17%`. Net: "Transfer" no longer wraps. Total still adds to 100%.

**ExportUtility — no silent Interest Type default (audit #17):**
- `generateLoanStatementPDF` Interest Rate line previously rendered `"${rate}% p.a. (${borrower.type})"` — when `borrower.type` was blank, that produced `"${rate}% p.a. ()"` which a reader could mis-interpret as Simple Interest. Now renders `"Not specified"` for blank type. Loan-rendering on-screen has the same fix tracked separately.

**Stages 3-4 of C21 still pending:**
- Stage 3 — PDF charts + KPIs: asset allocation donut + monthly income/expense bar + net worth trend line; 5 new KPIs (Total Liabilities / Total Lent Out / Monthly Income / Monthly Expense / Net Cash Flow).
- Stage 4 — XLSX overhaul: currency-format numeric cells, conditional formatting, frozen panes + auto-filter, totals rows, Summary sheet first.

### Closes
- **C21 audit fixes #3, #4, #5, #6, #7, #17** (this stage).
- C21 #1, #2, #8, #9 closed in 3.2.35. #18 accepted as Android framework limit.

### Changed
- **`versionName`** `3.2.35` → `3.2.36`, **`versionCode`** `158` → `159`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. The new helpers are deterministic — `wrapText` uses `Paint.measureText` which is OS-provided and stable; `computeBorrowerStatus` / `computeDebtStatus` are pure functions over already-validated data. No new data path.

## [3.2.35] - 2026-05-27 *(Development milestone — C21 Stage 1: PDF identity, cover header, standardized filename pattern; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C21 Stage 1 of 4 — PDF identity + cover header + filename pattern (audit fixes C21 #1, #2, #8, #9; documents #18 as framework limit).** First of 4 stages closing C21. Every exported PDF and CSV now uses the same `Fynlo_<ReportType>_<yyyy-MM-dd>_<Subject>.<ext>` filename and renders an identity row (Project | User | Period | Currency) directly under the title on the cover.

**ExportUtility — added:**
- **`filename(reportType, subject, ext)`** helper. Produces `Fynlo_Report_2026-05-27_Personal.pdf` / `Fynlo_LoanStatement_2026-05-27_Mohan_Rao.pdf` / `Fynlo_MoneyFlow_2026-05-27_Personal.csv` etc. Subject sanitized to alphanumeric + underscore (replace via `Regex("[^A-Za-z0-9]+")` → `_`, trim leading/trailing underscores, fall back to `Untitled` on empty) so filenames survive Windows / Drive / email attachments without URL-encoding artefacts.
- **`headerInfoLine(project, user, period, currencyCode)`** helper. Renders `Project: X | User: Y | Period: Z | Currency: INR (₹)`. User segment omitted when blank (anonymous / fresh-install). Period defaults to "All time" when caller has no date range.

**ExportUtility.generatePDF / generateMoneyFlowPDF / generateLoanStatementPDF — added params:**
- `projectName: String = "Personal"`
- `userEmail: String = ""` (omitted from header when blank)
- `periodLabel: String = "All time"` (generatePDF + generateMoneyFlowPDF; loan statements span the borrower's full history so always "All time")
- Identity row rendered as bold 10pt text on the cover, immediately under the screen title. Generated/Recalculated lines stay below in grey.

**Callers updated (5 sites):**
- **`FinanceViewModel.exportToPDF`** — threads `currentProject.value?.name` + `AuthManager().userEmail` + "All time".
- **`ReportsHubScreen`** — threads current project name + signed-in email + period string built from the active range chip (e.g., `01 May 2026 – 27 May 2026` or `All time`). New filename: `Fynlo_Report_<date>_<project>.pdf`.
- **`ProfitLossScreen`** — threads project + email + "All time". New filename: `Fynlo_PL_Report_<date>_<project>.pdf`.
- **`CustomerDetailScreen`** — threads project + email. New filename: `Fynlo_LoanStatement_<date>_<borrowerName>.pdf` (replaces `loan_statement_<name>.pdf`).
- **`MoneyFlowScreen`** — threads project + email (`remember`'d so it's stable across recompositions) + "All time" for both PDF and CSV exports. New filenames: `Fynlo_MoneyFlow_<date>_<project>.{pdf,csv}` (replaces `money_flow_<date>.{pdf,csv}`).
- **`MonthlySummaryScreen`** CSV — filename only (no PDF generator for this surface yet). New filename: `Fynlo_MonthlySummary_<date>_Personal.csv`.

### Accepted limitation (audit #18)

Android's `android.graphics.pdf.PdfDocument` does NOT expose a public API for setting PDF info-dictionary metadata (Title / Author / Subject). Migrating to a different PDF library (iText / PDFBox / OpenPDF) for the sake of these three fields would be a larger dependency change than the user-visible benefit warrants. The identity row inside the PDF cover (above) carries the same Title / Project / Period data onto a page that opens directly when the user views the file. Logged in `ExportUtility.PDF_METADATA_LIMITATION_NOTE` (constant lives in source only — not rendered).

**Stages 2-4 of C21 still pending:**
- Stage 2 — PDF data correctness (#3 Debts section, #4 no truncation / auto-size, #5 dynamic Status, #6 Recent Transactions title fix, #7 Type column, #17 no silent Interest Type default).
- Stage 3 — PDF charts + KPIs (#10 asset allocation donut + monthly income/expense bar + net worth trend line; #11 5 new KPIs: Total Liabilities / Total Lent Out / Monthly Income / Monthly Expense / Net Cash Flow).
- Stage 4 — XLSX overhaul (#12 currency-format numeric cells, #13 conditional formatting, #14 frozen panes + auto-filter, #15 totals rows, #16 Summary sheet first).

### Closes
- **C21 audit fixes #1, #2, #8, #9** (this stage). #18 accepted limitation.

### Changed
- **`versionName`** `3.2.34` → `3.2.35`, **`versionCode`** `157` → `158`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. New helpers (`filename`, `headerInfoLine`) are pure string-building; callers route through the same `OutputStream` and `FileProvider` paths that were already validated.

## [3.2.34] - 2026-05-27 *(Development milestone — MoneyFlowScreen layout fix (3.2.33 smoke surface); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **MoneyFlowScreen LazyColumn was rendering with zero height (latent bug exposed by 3.2.33 smoke).** The wrapper Column around the Export button row used `Modifier.fillMaxSize()` which greedily consumed all remaining vertical space in the outer Column, leaving the `LazyColumn` below it with no space to render. This pre-dated the C15e work but was invisible until the user opened the screen looking for the new visualization. Fix: `fillMaxSize()` → `fillMaxWidth()` on the export-button wrapper Column so it takes only its intrinsic content height.
  - Restores visibility of the C15e Money Flow Visualization + Account Flows + Flow Summary + Transaction Flows filter-chip list — all of which were inside the zero-height LazyColumn.

### Changed
- **`versionName`** `3.2.33` → `3.2.34`, **`versionCode`** `156` → `157`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (1-line layout modifier change).

## [3.2.33] - 2026-05-27 *(Development milestone — C15 Stage 5: Money Flow category-grouped visualization (C15e) — **closes C15**; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C15 Stage 5 of 5 — C15e Money Flow category-grouped visualization (audit fix C15e #1).** Fifth and final stage closing C15. MoneyFlowScreen now opens with a "Where Money Flows" block showing the top inflow + outflow categories side by side as proportional horizontal bars; existing functionality (Account Flows, Flow Summary, filter-chip transaction list, CSV/PDF export) preserved underneath.

**MoneyFlowScreen — added:**
- **`MoneyFlowVisualization` composable** at the top of the LazyColumn (above Account Flows). Two parallel columns inside one surface:
  - **Inflows** (left, Emerald) — top 5 by amount from `INCOME` + `DEBT_RECEIVED` flows grouped by `flow.from` (which is `transaction.category` for income, lender name for debt-received). Rest bucketed as `Other` if non-zero.
  - **Outflows** (right, SemanticRed) — same shape, top 5 from `EXPENSE` + `DEBT_REPAY` flows grouped by `flow.to` (which is `transaction.category` for expense, lender name for debt-repay). Rest bucketed as `Other`.
- **Per-row layout** — category label + amount (right-aligned, color-coded) above a proportional-width bar. Each column independently scaled — the longest bar in each side hits ~100% width, others scale proportionally. Bars floor at 4% width so near-zero categories stay visible.
- **Empty-state placeholders** for either column when the user has no inflows or no outflows yet.
- **Exclusion** — `LENDING` (principal outflow), `TRANSFER`, `INVESTMENT` flows are NOT in the viz. Lending principal already surfaces in the Flow Summary block as "Lent Out"; transfers and investments are internal movements, not real wallet inflows/outflows.

**MoneyFlowScreen — kept:**
- Account Flows (top 5 accounts by total movement, with inflow + outflow + net).
- Flow Summary (Total Inflow / Total Outflow / Lent Out / Net Flow).
- Transaction Flows section with filter chips (All / Income / Expense / Transfer / Lending / Debt) — the per-flow list.
- CSV + PDF export dropdown.

### Audit C15c #4 follow-up

The audit notes C15c #4 ("X snapshots" plural bug) is "covered by C10". The localized `pluralCount` helper landed in 3.2.31's NetWorthHistoryScreen — sufficient for that specific surface. Broader C10 plural-form cluster work remains in the P2 backlog.

### Closes
- **C15e audit fix #1** (this stage).
- **C15 cluster closed in full** — all 5 sub-stages landed: C15a (3.2.29), C15b (3.2.30), C15c (3.2.31), C15d (3.2.32), C15e (3.2.33).

### Changed
- **`versionName`** `3.2.32` → `3.2.33`, **`versionCode`** `155` → `156`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. New `MoneyFlowVisualization` reads from the same `allFlows` list the rest of the screen already uses; no new data path.

## [3.2.32] - 2026-05-27 *(Development milestone — C15 Stage 4: Monthly Summary chart-hero + 12-month bar chart + axis labels + callout cards + linear-regression projection + CSV export (C15d); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C15 Stage 4 of 5 — C15d Monthly Summary: chart-hero + 12-month bar chart with axes + standardized callouts + projection + CSV export (audit fixes C15d #1, #2, #3, #4, #5, #6).** Fourth of 5 stages closing C15. Last remaining sub-stage is C15e Money Flow (build-or-remove).

**MonthlySummaryScreen — restructured (all six audit points):**
- **#1** `type_chart_hero` block — Net for current month (e.g., `Net for May +₹35K`) sits above the bar chart in one shared surface. Same shape established for C15b P&L Statement (3.2.30) and C15c Net Worth History (3.2.31). Subtitle states `Income ₹X · Expense ₹Y` so the user sees the underlying arithmetic.
- **#2** Bar chart extended from 6 months to **last 12 months**. Income (green) + expense (red) bars side-by-side per month. Financing categories (`Debt Received`, `Debt Repayment`, `Lending`, `Loan Recovery`, `Loan Repayment`, `Investment`, `Investment Returns`) excluded from both series — same cash-basis exclusion as the P&L Statement.
- **#3** **Y-axis labels** along the left edge of the chart (5 labels at 0/25/50/75/100% of max). **Four horizontal reference lines** at 25/50/75/100% of max so the user can read magnitudes off the chart.
- **#4** Four standardized callout cards replace the prior 6M Income / 6M Expense / Net Saved 3-chip row:
  - `Best Month` — month with highest (income − expense) net + the signed amount.
  - `Worst Month` — month with lowest net + the signed amount.
  - `Avg / Month` — mean monthly net across the 12 months.
  - `Trend` — recent-6-month avg minus prior-6-month avg (signed delta) with `vs prior 6m` subtitle.
- **#5** **Projection** — independent linear regression of the income series and expense series over the 12 historical months; the next 3 months projected and rendered as ghost bars at `alpha = 0.4` at the right edge of the chart. A vertical dashed divider separates historical from projected zones. Projected month labels are prefixed with `·` and use dimmed colour. Negative regression outputs are clamped to 0 (no negative income / expense bars). Legend dot under chart adds `Projected` in semi-transparent green.
- **#6** **CSV export** — `FilledTonalIconButton` (TableChart icon) at the top-right of the hero block. Writes a `Month,Income,Expense,Net` CSV of the 12 historical months (with currency code in headers) to the app cache directory and launches an `ACTION_SEND` chooser via the existing `FileProvider`. Latest month at the top.

**MonthlySummaryScreen — kept:**
- Idle Fund Alert at the top when more than 60% of net worth is sitting in cash. Outside the audit's scope but a good UX touch — survives the redesign.
- Month-by-Month Breakdown list at the bottom (the 12-month roll showing income / expense / saved per row), unchanged.

**Stage 5 of C15 still pending:** C15e Money Flow — decide build (Sankey or category-grouped flow visualization) vs remove the tile.

### Closes
- **C15d audit fixes #1, #2, #3, #4, #5, #6** (this stage).
- C15a closed in 3.2.29. C15b closed in 3.2.30. C15c closed in 3.2.31. C15e still pending.

### Changed
- **`versionName`** `3.2.31` → `3.2.32`, **`versionCode`** `154` → `155`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. The new projection uses pure linear-regression math (no data-layer call); CSV export writes through the same `FileProvider` that PDF export already uses; the cash-basis category exclusion is identical to what P&L Statement already validates.

## [3.2.31] - 2026-05-27 *(Development milestone — C15 Stage 3: Net Worth History chart-hero + callout cards + backfill + nag removal (C15c); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C15 Stage 3 of 5 — C15c Net Worth History: chart-hero + standardized callout cards + transaction-history backfill + open-daily nag removed (audit fixes C15c #1, #2, #3, #4, #5, #6).** Third of 5 stages closing C15. NetWorthHistoryScreen now opens with a `type_chart_hero` block (Current Net Worth above the line chart), three standardized callout cards, and a "Backfill from history" action that auto-generates month-end snapshots so the trend exists from day one instead of requiring weeks of daily app opens.

**NetWorthHistoryScreen — added (audit C15c #1 + #2):**
- **Chart-hero block** — Current Net Worth number + `NetWorthLineChart` Canvas now share one surface so they read as a single `type_chart_hero` unit per `DESIGN_SYSTEM.md §1.2`. Same shape established for C15b P&L Statement in 3.2.30.
- **Migrated `Icons.Filled.ShowChart` → `Icons.AutoMirrored.Filled.ShowChart`** (was a pre-existing deprecation; touched the file anyway).

**NetWorthHistoryScreen — added (audit C15c #3):**
- **Standardized 3-tile callout row** replaces the prior Highest / Lowest / Change % row:
  - `1-Month Change` — % change from the snapshot closest to (but not after) `today - 1 month`. Shows `Need more data` if no snapshot in range.
  - `6-Month Change` — same logic with `today - 6 months`.
  - `All-Time High` — `sorted.maxOf { netWorth }` formatted as listRow currency.
- Color: green when current ≥ then, red when below, neutral when no comparison snapshot available.

**NetWorthHistoryScreen — added (audit C15c #5):**
- **"Backfill from history" button** (OutlinedButton with `History` icon) — primary CTA in empty state, secondary action when data exists. Calls `viewModel.backfillNetWorthHistory()` which walks transactions month by month, computes a cash-basis approximate net worth for each month-end, and inserts a snapshot for that date. Existing snapshot dates are preserved (REPLACE is technically the conflict strategy but the backfill checks `existingDates` first).
- Status text appears under the button after each run: `"Added N month-end snapshots from history"` / `"Already up to date — no months to backfill."` so the user sees the result without a Toast/Snackbar.

**NetWorthHistoryScreen — fixed (audit C15c #4):**
- **"X snapshots recorded" pluralization** — added local `pluralCount(n, "snapshot", "snapshots")` helper so `1 snapshot recorded` no longer renders as `1 snapshots recorded`. Audit notes this is also covered by the broader C10 cluster but the local fix is the right thing on touch.

**NetWorthHistoryScreen — removed (audit C15c #6):**
- **"Open the app daily to track net worth trends" nag** from the empty state. The backfill CTA replaces it — instead of nagging the user to come back every day for weeks, generate the historical curve from data they already have.

**FinanceViewModel — added:**
- **`backfillNetWorthHistory(onDone: (Int) -> Unit = {})`** — for each calendar month-end between the user's earliest cash-basis transaction and the last completed month, computes `approxNW = currentNW − (cumulative cash flow from monthEnd+1 to today)`. Financing categories excluded so debt received / loans extended / investments don't double-count. Existing snapshot dates skipped via `repository.getNetWorthSnapshots(pid).first()`. Calls `onDone(added)` on Main with the count of snapshots inserted.

**Limitation by design:** investment unrealized value changes can't be reconstructed from history (we only have `currentVal`), so the backfilled curve treats investment value as flat. The result is a cash-flow-based trend — accurate for direction reading without requiring a pricing-history API. Daily auto-save (`saveSnapshotNow()` on screen open) still runs, accumulating real point-in-time data going forward.

**Stages 4-5 of C15 still pending:** C15d Monthly Summary (bar chart + axis labels + projection), C15e Money Flow (build a flow visualization or remove).

### Closes
- **C15c audit fixes #1, #2, #3, #4, #5, #6** (this stage).
- C15a closed in 3.2.29. C15b closed in 3.2.30. C15d, C15e still pending.

### Changed
- **`versionName`** `3.2.30` → `3.2.31`, **`versionCode`** `153` → `154`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures. The new `backfillNetWorthHistory` method routes through the same `repository.saveNetWorthSnapshot` path that `saveSnapshotNow` already used — no new data path. Cash-flow approximation is bounded by the existing financing-category exclusion (already tested across `BackupDataIntegrityTest` and `TransactionValidatorDataIntegrityTest`).

## [3.2.30] - 2026-05-27 *(Development milestone — C15 Stage 2: P&L Statement chart hero + callout cards + Total Lent Out fix (C15b); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C15 Stage 2 of 5 — C15b P&L Statement: chart hero + callout cards + Total Lent Out fix (audit fixes C15b #1, #2, #3, #4, #5).** Second of 5 stages closing C15. ProfitLossScreen now opens with a `type_chart_hero` block (Net P&L number above a rolling-12 income-vs-expense line chart), four headline callout cards directly under that, and a Lending Business section that correctly distinguishes lifetime-lent from currently-lent.

**ProfitLossScreen — added (audit C15b #1 + #2):**
- **Rolling-12 line chart** (`MonthlyPLLineChart`) under the Net P&L number, both inside one rounded surface so the hero reads as a single `type_chart_hero` unit per `DESIGN_SYSTEM.md §1.2`. Dual lines: income (green) + expense (red), shared y-axis (max of either series), 3-dp stroke with point markers at each month.
- **Month axis labels** along the bottom, every third month + the last one. `MMM` compact form.
- **Legend dots** under the chart (Income green / Expense red) so the colour mapping is explicit.
- **Empty-state hint** ("Log a few transactions to see the rolling-12 trend") when every monthly bucket is zero.

**ProfitLossScreen — added (audit C15b #3):**
- **Four callout cards** in a single Row directly under the chart hero: `This Month`, `Last Month`, `YTD`, `vs Last Year`. Each shows a signed net (`+₹X` / `−₹X`) in green/red based on sign. YTD = Jan 1 → today. `vs Last Year` = YTD this year minus the matching window last year (delta, not absolute). `PLCallout` composable, equal-weight tiles.

**ProfitLossScreen — fixed (audit C15b #4):**
- **"Total Lent Out" was previously misleading.** The old code summed `activeBorrowers.sumOf { it.amount }` — that excluded written-off loans (so not lifetime) but also included already-recovered principal (so not outstanding either). Two replacements:
  - **`Total Lent Out (lifetime)`** = `borrowers.sumOf { it.amount }` — every borrower's original principal, INCLUDING written-off (they're part of historical lending activity).
  - **`Currently Lent Out`** (new row) = `activeBorrowers.sumOf { (it.amount - it.paidPrincipal).coerceAtLeast(0.0) }` — what's still on the books.

**ProfitLossScreen — replaced (audit C15b #5):**
- **Static "You are profitable ↑" subtitle** replaced with the actual cash-basis arithmetic: `"Cash basis · income ₹X − expenses ₹Y"`. Stating the numbers means the user sees WHY P&L is positive (or negative) instead of an affirmation that doesn't match the underlying flow. The grossRevenue and total-expense values include interest paid + bad debt write-offs so the subtitle ties cleanly to the four PL sections below.

**ProfitLossScreen — kept:**
- All four PL sections: Revenue / Expenses / Lending Business (with audit #4 fix applied) / Investments.
- Project name header + Export PDF button at the top.
- Cash-basis financing-category exclusion unchanged.

**Stages 3-5 of C15 still pending:** C15c Net Worth History (line chart + backfill), C15d Monthly Summary (bar chart + axis labels + projection), C15e Money Flow (build a flow visualization or remove).

### Closes
- **C15b audit fixes #1, #2, #3, #4, #5** (this stage).
- C15a closed in 3.2.29. C15c, C15d, C15e still pending.

### Changed
- **`versionName`** `3.2.29` → `3.2.30`, **`versionCode`** `152` → `153`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (UI restructure + same-source calculations; the only formula change is the Lending Business section's Total Lent Out + Currently Lent Out split, both deterministic sums over already-validated borrower data).

## [3.2.29] - 2026-05-27 *(Development milestone — C15 Stage 1: Reports landing converted to pure launcher with previewed tiles (C15a); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C15 Stage 1 of 5 — C15a Reports landing converted to pure launcher with previewed tiles (audit fixes C15a #1, #2, #3, #4).** First of 5 stages closing C15. ReportsHubScreen no longer mixes inline rollups with launcher tiles — it's now a clean Home-archetype launcher where every tile carries a one-line preview value computed against the selected date range.

**ReportsHubScreen — stripped (audit C15a #1 + #4):**
- **Income / Expense two-column block** that inlined sums for the selected range — exact same data the P&L Statement renders in full.
- **Net Cash Flow + Savings Rate** row underneath — same data in the same target screen.
- **Net Worth Trend mini-chart** (Canvas line chart over `getNetWorthSnapshots()`) with first/last date labels — Net Worth History detail owns this.
- **"Where Money Went" section** with category bars (top 6, sorted) — same data in P&L Statement and Spend tab.
- **"Where Money Came From" section** with category rows (top 5) — same data in P&L Statement.

**ReportsHubScreen — added (audit C15a #2 + #3):**
- **`ReportTileCard` composable** replacing `ReportLinkCard`. Same shape across every tile — 36-dp icon circle + label + preview-value row, `heightIn(min = 116.dp)` so rows have consistent height. Audit fix #2.
- **One-line preview value per tile** computed from a memoised `RangeAggregate` over the selected range. Audit fix #3:
  - P&L Statement: `+₹35K` / `-₹12K` with green/red based on sign of (income − expense), financing activities excluded (same exclusion the screen used to use).
  - Net Worth: current `summary.netWorth` formatted as listRow currency.
  - Money Flow: gross movement total in the range (`No activity` if zero).
  - Interest Income: sum of `income` transactions where `category == "Interest"` in the range.
  - Monthly Summary: always shows **this calendar month's net** regardless of selected range (that screen has its own month picker; the tile surfaces the most-useful snapshot).
  - Debt Payoff: current outstanding debt total (`totalDebtPrincipal + totalDebtInterest`); shows `Debt free` when zero.
  - EMI Calculator: `Calculator` label (it's a tool, no data preview makes sense).

**ReportsHubScreen — kept:**
- Date-range chip row at the top — drives every tile preview value below.
- Export-PDF button — utility unchanged.
- Tile grid layout (3 tiles per row, two full rows + one partial row with EMI tile + 2 weighted spacers).
- All seven nav callbacks (`onNavigateToPL`, `onNavigateToNetWorth`, `onNavigateToMoneyFlow`, `onNavigateToInterest`, `onNavigateToMonthly`, `onNavigateToDebtPayoff`, `onNavigateToLoanCalc`).

**Stages 2-5 pending:** C15b P&L Statement (line chart + callout cards), C15c Net Worth History (chart + backfill), C15d Monthly Summary (bar chart + axis labels), C15e Money Flow (build a Sankey-style flow visualization or remove the tile).

### Closes
- **C15a audit fixes #1, #2, #3, #4** (this stage).
- C15b, C15c, C15d, C15e still pending.

### Changed
- **`versionName`** `3.2.28` → `3.2.29`, **`versionCode`** `151` → `152`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (UI restructure; preview-value calculations route through the same `summary` / `transactions` flows that already exist).

## [3.2.28] - 2026-05-27 *(Development milestone — C12 Stage 3: row simplification + Send Reminder picker + DebtDetailScreen — **closes C12**; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C12 Stage 3 of 3 — row simplification + action lift to detail screens (audit fixes #5, #6, #7, #8).** Final stage closing C12. List rows in LendingScreen and DebtScreen reduced to the audit's spec — icon + name + amount + chevron, one row tap navigates to detail screen, NO action icons in row. Both rows now visually identical (audit #5: "standardize Lent vs Owed rows to identical visual structure").

**LendingCard — stripped:**
- All 5 inline action callbacks gone: `onDelete`, `onEdit`, `onCollect`, `onDefault`, `onWriteOff`. Only `onClick` (row-tap → detail) remains.
- 70-line in-row WhatsApp smart-message builder, the SMS builder, the "Share loan summary" button, the "Add Phone" hint, the MoreVert dropdown menu, the inline "Collect Payment" button, the inline "Mark as Defaulted / NPA" toggle, the inline "Total Outstanding" duplicate, the "Days Elapsed / Per Day Interest / Paid So Far" strip, the "Both" SI+CI portion split — all moved to or duplicated in `CustomerDetailScreen`. Composable shrank from ~380 lines to 93.

**DebtCard — stripped:**
- All 3 inline action callbacks gone: `onEdit`, `onDelete`, `onPay`. Only `onClick` remains.
- MoreVert dropdown (Edit / Delete menu items), inline status Badge (redundant — the Stage-2 segmented filter makes status implicit), inline Pay Instalment button, inline Days Elapsed strip, inline "Both" portions block, inline Borrowed Amount + Interest columns — all moved to the new `DebtDetailScreen`. Composable shrank from ~137 lines to 88.

**LendingScreen — stripped state:**
- `editingBorrower`, `collectingForBorrower`, `defaultingBorrower`, `writeOffBorrower` state vars all removed.
- `CollectPaymentDialog`, the Mark-as-Defaulted `AlertDialog`, and the Write-Off `AlertDialog` blocks all removed.
- `val accounts`, `val people`, `val currencySymbol` references removed (no longer needed without the inline message builder).
- `AddLendingDialog` invocation simplified — Add flow only; Edit flow now lives in detail.

**DebtScreen — stripped state:**
- `editingDebt`, `payingDebt` state vars and their `AddDebtDialog`-as-editor + `PayDebtDialog` blocks removed.
- `val accounts` reference removed.
- `AddDebtDialog` invocation simplified — Add flow only; Edit + Pay flows now live in detail.

**CustomerDetailScreen — added:**
- **Send Reminder button** (OutlinedButton under Collect Payment) → opens an `AlertDialog` channel-picker with WhatsApp + SMS options (audit #8). Smart-message builder lifted from the old LendingCard; same overdue-aware text, same dispatching intents. If no phone on file, shows hint to use Edit instead of channel buttons.
- **Mark NPA / Restore Active button** (OutlinedButton, toggles label based on `borrower.status == "Defaulted"`) with confirm dialog.
- **Write Off button** (OutlinedButton, only shown when `totalOutstanding > 0`) with confirm dialog.
- All three actions land in the same `viewModel` methods the LendingScreen dialogs used to call — same business logic, new home.

**DebtDetailScreen — NEW (`app/src/main/java/app/fynlo/ui/screens/DebtDetailScreen.kt`, ~285 lines):**
- Mirrors `CustomerDetailScreen` visual structure so Lent + Owed detail surfaces feel like one design (audit #5).
- TopBar: Edit + Delete actions.
- Body: hero outstanding number (red because it's a liability) → Make Payment primary button (`PayDebtDialog` triggered here) → Payment History list (reads `viewModel.debtPayments`) → Notes card.
- Wired into nav graph at route `debt/{debtId}`. New `onNavigateToDebtDetail` prop on `LoansHubScreen` flows from `Navigation.kt` → `LoansHubScreen` → `DebtScreen.onNavigateToDetail`. Top-level Debts route also wired.

### Closes
- **C12 audit fixes #5, #6, #7, #8** (this stage).
- **C12 cluster closed in full** — Stage 1 (3.2.25) closed #1, #2, #9. Stage 2 (3.2.27) closed #3 + dropped #4. Stage 3 (this commit) closes #5, #6, #7, #8.
- Audit fix #4 (column-header sort affordance) deferred — list-row layout has no column headers to tap; revisit if user demand surfaces.

### Changed
- **`versionName`** `3.2.27` → `3.2.28`, **`versionCode`** `150` → `151`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (pure UI restructure; no logic / state-shape / data path change — every business-logic call routes through the same `viewModel` methods that already exist).

## [3.2.27] - 2026-05-27 *(Development milestone — C12 Stage 2: filter consolidation across Lending + Debt screens; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C12 Stage 2 of 3 — Active/Overdue/Closed filter replaces 3 filter UIs (audit #3 + #4).** Second of 3 stages closing C12. Both child screens of LoansHubScreen (LendingScreen + DebtScreen) now use the same Active/Overdue/Closed segmented filter at the top of their list, with per-segment counts ("Active · 3", "Overdue · 1", "Closed · 0") for 1-second status readability.

**LendingScreen — removed:**
- **Interest Loans / Hand Loans `TabRow`** (the internal selectedTab — sorted by loan-type rather than status, which is less useful for daily UX). Hand vs interest distinction lives in the row (% rate displayed inline).
- **Sort dropdown** (`Overdue / Amount / Name / Date` `DropdownMenu`) — audit #4. Processed list now uses a fixed default sort: overdue-first then by amount descending (matches the prior dropdown's default value).
- **Stats line** "N interest · N hand · N settled" — replaced by per-segment counts on the new filter.
- **Collapsible "Settled" section** at the bottom of the list — settled loans now live under the `Closed` filter where they're discoverable on one tap.
- **In-screen "Total outstanding" hero summary** — redundant with LoansHubScreen's C12 Stage 1 hero (3.2.25) that already shows this at the parent level.
- **Back-handler for the TabRow** — gone with the TabRow itself.

**LendingScreen — kept:**
- Search bar (search is not a filter — it's a query)
- EMI calculator button + Calendar shortcut (these are tools, not filters)
- Per-card actions (Stage 3 will simplify these and move full action surface to CustomerDetailScreen)

**DebtScreen — removed:**
- **In-screen "Total Outstanding" summary card** — same redundancy as LendingScreen; parent LoansHubScreen hero owns this read for the Owed tab.

**DebtScreen — added:**
- **Active/Overdue/Closed segmented filter** for parity with LendingScreen. `Active = paid < amount`, `Overdue = active AND due date past today`, `Closed = paid >= amount`. Filter-specific empty-state messages ("No overdue debts — you're on track 🎉", "No closed debts yet", "No active debts").

### Sort dropdown lost user-toggleable sort modes
- Pre-3.2.27 had Overdue / Amount / Name / Date sort modes via the dropdown. Stage 2's fixed sort (overdue-first then amount-desc) matches what most users wanted, but **user-toggleable sort is gone for now**. The audit's fix #4 calls for "column-header sort affordance instead" — meaningful but more work; deferred to a follow-up if user demand surfaces. If you used to sort by Name or Date specifically, that's the regression — let me know and we'll add it back as column-header taps.

### Closes
- **C12 audit fixes #3 + #4** (filter consolidation + sort dropdown removal).
- Stage 1 (3.2.25) closed #1, #2, #9. Stage 2 (this commit) closes #3, #4. Stage 3 will close #5, #6, #7, #8 (row standardisation + per-row action removal + Send-reminder picker).

### Changed
- **`versionName`** `3.2.26` → `3.2.27`, **`versionCode`** `149` → `150`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (UI restructure; no logic / state-shape change).

## [3.2.26] - 2026-05-27 *(Development milestone — LendingDialog interest picker unified with DebtDialog; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **LendingDialog interest-type picker now uses the same `ExposedDropdownMenuBox` widget as DebtDialog** — user smoke of C12 Stage 1 (3.2.25) surfaced the inconsistency: DebtDialog has a clean dropdown listing all 4 interest types, but LendingDialog had a `FlowRow<FilterChip>` with Simple Interest always visible and a `Pro`-gated "Advanced options" `TextButton` that revealed Reducing / Compound / SI+CI chips. Both pickers serve the same purpose; one widget for both.
  - **Free vs Pro gating preserved** by varying the dropdown's options at construction time:
    - Free user → dropdown lists only `["Simple Interest"]`
    - Pro user → dropdown lists `["Simple Interest", "Reducing Balance", "Compound Interest", "Both"]` (rendered as `"SI + CI"` via `InterestEngine.label`)
  - **Eliminates the extra-tap "Advanced options" affordance** for Pro users — they already paid for these options, no point hiding them behind a click. Free-tier gating still enforced because the advanced types simply aren't in their dropdown.
  - **Edge case handled** — if a free user has a borrower previously saved with an advanced type (Pro downgrade or admin override), the field still displays the current type correctly via `InterestEngine.label`, and the dropdown won't offer the advanced types — they can only switch back to Simple Interest from there.
  - **Dropped** the `showAdvancedInterest` state variable + the "Advanced options" TextButton. Cleaner code, fewer states.

### Changed
- **`versionName`** `3.2.25` → `3.2.26`, **`versionCode`** `148` → `149`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (UI widget swap; no logic change — same `selectedType` state, same persistence path).

## [3.2.25] - 2026-05-27 *(Development milestone — C12 Stage 1: LoansHub hero + "Both"→"SI + CI" rename; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C12 Stage 1 of 3 — Loans tab Home-archetype hero + interest-type label rename.** First of 3 stages closing C12 (audit's "biggest UX disaster," 10 fix points, XL effort). Stage 2 (filter consolidation) and Stage 3 (per-row action removal + Send-reminder picker) follow. Three fixes land tonight:
  - **Total Outstanding hero** on LoansHubScreen (audit #1, #2) — above the Lent/Owed segmented row. Shows "Total Outstanding" label → big colour-coded amount (`Emerald500` when Lent tab, `SemanticRed` when Owed) → "Across N loans" / "Across N debts" subtitle with proper singular/plural. Numbers sourced from already-computed `financialSummary.totalReceivables` (Lent) and `totalDebtPrincipal + totalDebtInterest` (Owed). Active count mirrors `LendingScreen.isActive` for Lent and `paid < amount` for Owed. Hero hidden when both `heroAmount == 0` and `heroCount == 0` so empty-state UX in the child screen takes over.
  - **"Both" → "SI + CI" everywhere user-facing** (audit #9). Stored value stays `"Both"` (DB rows + `InterestEngine` branch logic depend on it; migrating the stored value would require a schema migration). NEW `InterestEngine.label(storedType)` helper translates `"Both"` → `"SI + CI"`; any other value passes through. Applied at every UI display site:
    - `PaymentDialog.kt:115` — "Interest (% type)" subtitle
    - `CustomerDetailScreen.kt:209` — "Rate: X% • type" detail row
    - `DebtScreen.kt:290` — "Type: X" card line
    - `LendingScreen.kt:516` — WhatsApp share-copy line
    - `DebtDialog.kt:177` — Interest Type dropdown field + `:183` menu items
    - `LendingDialog.kt:167` — Interest Type chip picker

### Deferred to C12 Stages 2-3
- **Stage 2 (audit #3, #4):** replace 3 filter UIs (Lent/Owed tab + Interest/Hand internal tab + sort dropdown) with single `Active / Overdue / Closed` segmented control. Drop the sort dropdown. Structural change to filter logic in LendingScreen + DebtScreen.
- **Stage 3 (audit #5, #6, #7, #8):** standardise Lent vs Owed row visual; remove per-row action icons (move to CustomerDetailScreen); consolidate WhatsApp + SMS into a single "Send reminder" action with picker. Touches both screens' row layouts plus the CustomerDetailScreen action surface.

### Already done elsewhere
- **#10 FAB padding** — already done in C06 (`FabBottomPadding = 120.dp` constant applied to both LendingScreen and DebtScreen LazyColumns).

### Changed
- **`versionName`** `3.2.24` → `3.2.25`, **`versionCode`** `147` → `148`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (no logic change — `InterestEngine.label()` is a pure-function helper; UI sites are pass-through display).

## [3.2.24] - 2026-05-27 *(Development milestone — C14 Invest tab Home-archetype migration; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C14 — Invest tab Home-archetype migration (3 of 10 audit fix points landed; #5-9 feature-adds deferred).** Second of the C12-C15 P1 screen-redesign series. Three structural changes close the cluster's "Home archetype" spirit:
  - **Portfolio Value hero** (audit #1) — added at the top of the LazyColumn before the holdings list. Shows: big `Portfolio Value ₹X` number (Emerald if up, Red if down), coloured arrow + growth ₹ + growth % line (`↑ +₹50,000 (+15.2%)`), subtitle `₹X invested · N holdings`. 1-second portfolio readability per the audit's archetype spec. Hidden growth line when `netInvested == 0` (defensive — investment fully withdrawn).
  - **Allocation visual** (audit #2) — horizontal stacked bar by investment type (Stocks / Gold / FD / Insurance / etc.), with legend rows showing colour dot + type + amount + percentage. Uses the shared `ChartColors` palette already used by SpendScreen's category breakdown. Hidden when the user has only one holding type (the bar would be a single block — no information).
  - **Action button hierarchy fixed** (audit #4) — was INVERTED: `Update Value` rendered as `OutlinedButton` (secondary), `Withdraw` as filled Emerald `Button` (primary). Update Value is the more-frequent action (markets update daily; withdrawals are rare), so it now gets the primary filled treatment. Withdraw drops to OutlinedButton — still discoverable, but visually steps aside.
  - **Holdings section header** added between portfolio-level info and per-holding cards — `Holdings` titleSmall label marks the transition.
  - **Audit #10 (negative growth in red)** — already done. Card's gain/loss column has always used `SemanticRed` when growth < 0; portfolio hero now uses the same semantic.

### Deferred (Task #28 — C14 features deferred from 3.2.24 closure)
- **#5 CAGR / XIRR** per holding and portfolio. XIRR needs Newton's-method-style numerical solver — non-trivial finance math, own commit.
- **#6 Mutual Fund SIP taxonomy** — needs `AddInvestmentDialog` dropdown options refactor (add "Mutual Fund" as a distinct type from "Stocks"). Migration question for users who already tagged SIPs as Stocks.
- **#7 Celebration toast on growth increase** — small UX delight after `Update Value` saves a higher number.
- **#8 Verify FD type renders correctly** — investigation, may be a taxonomy / icon-mapping check. Folds into #6.
- **#9 Valuation History chart** — line chart of investment value over time per `DESIGN_SYSTEM.md §9.14`. Chart work is sizable; own commit.

### Closes
- **UX_AUDIT §C14 — Invest tab Home-archetype migration** (effectively, modulo deferred features). 2nd of 4 P1 screen-redesign clusters (C12-C15) closed.

### Changed
- **`versionName`** `3.2.23` → `3.2.24`, **`versionCode`** `146` → `147`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (pure UI restructure — no logic changes).

### Sprint 2 P1 milestone
After this commit, **8 P1 Sprint 2 clusters are CLOSED**: C04 (3.2.6), C06+C07 (3.2.12), C08 (3.2.18), C09 (3.2.19), C18 (3.2.20), C13 (3.2.23), **C14 (3.2.24)**. Remaining P1: C12 (Loans, XL — biggest user-pain), C15 (Reports, XL across 4 sub-screens), C21 (PDF/XLSX export quality polish).

## [3.2.23] - 2026-05-27 *(Development milestone — C13 Expenses tab Home-archetype migration; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C13 — Expenses tab Home-archetype migration (5 of 10 audit fix points landed; #5-9 feature-adds deferred).** First of the C12-C15 P1 screen-redesign series. SpendScreen already had most of the Home-archetype skeleton (hero number, category-split bars, recent list); the three gaps the audit called out are now closed:
  - **Hero MoM delta** (audit #1) — was just "₹X spent in May 2026". Now also shows a coloured arrow line: `↑ ₹500 more than last month` (red when spending up) / `↓ ₹500 less than last month` (Emerald when spending down) / `→ same as last month`. Hidden when there's no previous month's data (first month of usage). 1-second read of trajectory.
  - **Top-category callout** (audit acceptance "mostly on Food") — added a "Mostly on $cat — ₹X (NN%)" line below the hero, but only when one category clearly dominates (≥30% share of total). Below that threshold the "mostly" framing is misleading, so we hide it. Surfaces the audit's "1-second insight" requirement.
  - **Sectioned recent list** (audit #3) — was sorted-by-date-only. Now bucketed: **Today / Yesterday / This Week / Earlier**, only non-empty buckets render their header. Cap raised 15 → 20 rows (more headroom for users who log many txns/day). Bucket cutoffs: Today = exact match, Yesterday = today−1, This Week = within last 7 days (exclusive of today/yesterday), Earlier = everything else.
  - **Duplicate Add entry-point removed** (audit #4) — removed the in-page "Add" `FilledTonalButton` that sat above the Month selector. Per audit ("remove duplicate add entry points"), the Scaffold FAB owned by Navigation is the single Add affordance for this tab. The dialog wiring stays — empty-state CTA + FAB-launched flow both reuse it.
  - **Audit #2 (small donut/bar showing category split)** — **already done** (pre-C13). The existing Category Breakdown section renders horizontal bars per category sorted by amount, with budget-aware colour (Red ≥100%, Amber ≥80%) and budget-limit subtitle. Bars qualify as "small bar showing category split"; no new visual needed.
  - **Audit #10 (literal "Expense" category bug)** — **already done in C03a / C05** (TransactionValidator now sanitises "Expense" → "Uncategorized", and the chip picker is per-type).

### Deferred (Task #27 — C13 features deferred from 3.2.23 closure)
- **#5 Recurring toggle on transactions** — needs RecurringTransaction integration + new dialog affordance.
- **#6 Receipt photo attach** (camera + gallery picker, Storage backend).
- **#7 Tags field** (free-text comma-separated, persistence + filtering).
- **#8 Split transactions** (one ₹1000 grocery → 60% Food, 40% Household — schema implication on Transaction model).
- **#9 Edit Transaction supporting Type + Account changes** (currently EditTransactionDialog locks type and account; this needs a more complete edit dialog).

Each is a meaningful feature with its own scope; land independently when called for. Cluster-spirit closure (Home archetype) is what C13 was about; these are feature-adds that happen to live on the Expenses tab.

### Closes
- **UX_AUDIT §C13 — Expenses tab Home-archetype migration** (effectively, modulo deferred features). 1st of 4 P1 screen-redesign clusters (C12-C15) closed.

### Changed
- **`versionName`** `3.2.22` → `3.2.23`, **`versionCode`** `145` → `146`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (pure UI restructure — no logic / state changes).

## [3.2.22] - 2026-05-27 *(Development milestone — light-mode toggle visibility fix; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **Settings Switches: unchecked state visibility on light mode** — user reset all data, went through the new theme-aware setup wizard (3.2.21), then opened Settings to verify the redesigned Personalization Switches and reported "not clearly visible." Cause: M3's default `uncheckedThumbColor = outline` (light grey) on `uncheckedTrackColor = surfaceContainerHighest` (very light grey) faded into the SettingsCard's `surfaceVariant` background in light mode. The OFF state was effectively invisible. Now explicitly sets `uncheckedThumbColor = onSurfaceVariant` (darker, definitely visible) and `uncheckedBorderColor = onSurfaceVariant`. Applied to all 4 Switches that 3.2.20 / 3.2.21 added — Notifications "Loan reminders" + "Budget alerts" (3.2.20), Personalization "Follow system theme" + "Dark mode" (3.2.21).
- **First-launch setup `SelectionCard` unselected state visibility** — same root cause as above. The 3.2.21 SelectionCard unselected background used `surfaceVariant.copy(alpha = 0.4f)` which on the theme-aware light gradient (mostly `background`) faded into the page. Now full-opacity `surfaceVariant` plus a visible `outline.copy(alpha=0.4f)` border. Distinct from the page in both modes; dark mode still reads correctly because `surfaceVariant` is a step-up tonal from `background` in both themes.

### Changed
- **`versionName`** `3.2.21` → `3.2.22`, **`versionCode`** `144` → `145`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (UI color tweak — no logic).

## [3.2.21] - 2026-05-27 *(Development milestone — theme picker UX redesign + setup-screen theme-step removal + setup-screen theme-aware backgrounds; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed (3 related UX improvements per user feedback)

- **Settings → Personalization theme picker redesigned to match Notifications toggle pattern.** Was a 3-option `SingleChoiceSegmentedButtonRow` (System / Light / Dark) from the 3.2.11 chip-sweep — technically correct (3-state mutex) but visually inconsistent with the rest of Settings, which uses single-Switch rows for binary toggles. Now uses the **two-tier pattern Android's stock display-settings uses**:
  - **Row 1:** "Follow system theme" Switch — ON when override is null (i.e., app follows OS). Subtitle: "Match your phone's light / dark setting."
  - **Row 2** (only visible when Row 1 is OFF): "Dark mode" Switch — ON when override is `true` (dark), OFF when override is `false` (light). Subtitle: "Use dark theme regardless of system."
  - State mapping (preserves `ThemeController.darkModeOverride`): `null` → Row 1 ON + Row 2 hidden; `false` → Row 1 OFF + Row 2 OFF; `true` → Row 1 OFF + Row 2 ON.
  - When the user toggles "Follow system" OFF, the override is **seeded with the current visual state** (via `isSystemInDarkTheme()`) so the screen doesn't visually flip — it stays whatever the user is already seeing, just frozen under their control.

- **First-launch setup wizard: theme step removed.** Was `TOTAL_STEPS = 3` (theme → notifications → profile). Forcing the user to pick light/dark before they've even used the app is friction; the app defaults to "Follow system" (`darkMode(): "system"` in UserPreferences, the canonical safe default for 95% of users) and users who want to override can do so any time from Settings → Personalization. Now `TOTAL_STEPS = 2` (notifications → profile). Step indices in the `AnimatedContent` `when` and the save-on-Next `when` shifted accordingly; analytics step names updated to `notifications` / `profile`. The old `ThemeStep` composable is kept as dead code (not called from anywhere) for now — will be deleted in a follow-up cleanup commit if it stays unreferenced.

- **First-launch setup pages background now respects system theme.** Was a hardcoded `Brush.verticalGradient(Emerald900, Emerald700)` (dark "premium splash" look regardless of OS theme). Now uses the same theme-aware gradient as `OnboardingScreen`: `MaterialTheme.colorScheme.background → ... → Emerald700.copy(alpha = 0.04f)`. Reads light on light theme, dark on dark theme. Cascading migrations:
  - All `Color.White` references in `NotificationStep`, `ProfileStep`, `StepLayout`, `SelectionCard` → `MaterialTheme.colorScheme.onSurface` / `onBackground` / `onSurfaceVariant` per role.
  - Skip button text color → `onSurfaceVariant`.
  - Progress dot inactive color → `outlineVariant` (was 30%-alpha white; unreadable on light bg).
  - Back button: surface bg `surfaceVariant`, icon tint `onSurfaceVariant`.
  - Next button: was white-on-`Emerald900` (only readable on the old dark gradient). Now `Emerald500` container + white text — brand emerald accent works against both light and dark backgrounds.
  - `OutlinedTextField` in `ProfileStep` dropped the explicit white text-color overrides; relies on Material theming defaults so it reads correctly in both modes.
  - Large-icon container in `StepLayout`: `Emerald500.copy(alpha=0.12f)` (brand-tinted) replaces the 12%-alpha white circle.
  - `SelectionCard` borders + backgrounds theme-aware (was 6%-alpha-white background that disappeared into a light bg).

### Changed
- **`versionName`** `3.2.20` → `3.2.21`, **`versionCode`** `143` → `144`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (pure UI refactor — no logic / state-shape changes; existing `ThemeController` / `UserPreferences.darkMode()` paths untouched).

## [3.2.20] - 2026-05-27 *(Development milestone — C18 Settings cleanup (6 of 11 fixes); not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C18 — Settings cleanup. 6 of 11 audit fix points landed; 4 already done; 1 deferred (#4 Report-a-Bug in-app form, own commit).** 6th P1 Sprint 2 cluster effectively closed.
  - **Section headers redesigned** (audit #8) — `SettingsSectionLabel` dropped the emerald `•` bullet `Box` and emerald-coloured text; now plain bold `labelLarge` on default surface-variant colour. Quieter visual hierarchy. Danger Zone header keeps its red bullet (distinct attention-grabbing role).
  - **Date Format with examples** (audit #2) — switched from `SingleChoiceSegmentedButtonRow` to `ExposedDropdownMenuBox`. Field shows `dd-MM-yyyy   →   27-05-2026` so the example is visible at a glance; menu items show `dd-MM-yyyy   (27-05-2026)`. Today's date used so examples self-update.
  - **Loan & Budget Reminders split into 2 toggles** (audit #1) — `LOAN_REMINDERS_ENABLED` + `BUDGET_ALERTS_ENABLED` prefs added to `UserPreferences`. Both default to the master `notifications_enabled` value (existing users who disabled in setup see both OFF). UI shows two `Switch` rows with separate icons / subtitles. Master `notifications_enabled` is derived as `(loan OR budget)` so `ReminderScheduler` keeps running; when both sub-toggles OFF the master flips OFF and scheduler stops. Worker-layer differentiation (which alarm class reads which sub-key) is a follow-up; UI split lands now.
  - **Currency picker rows show `INR   ₹   Indian Rupee`** (audit #10) — was bare 3-letter codes. Both the field display and dropdown menu items use a shared `currencyLabel(code)` helper that reads `CurrencyUtils.supported` to unpack the symbol + name. Unknown codes fall back to the bare code.
  - **Rate-on-Play-Store gated by engagement** (audit #5) — row hidden until `transactions.size >= 5`. Fresh-install users don't see the prompt; once they've logged 5 transactions (demonstrable engagement) the row appears under App Info. No automatic prompt — Fynlo never had one; the audit's "rate immediately" concern was about the always-visible row affordance, which is now usage-gated.
  - **Dialog button colors standardized** (audit #9) — Cleanup Seeder confirm button gets `containerColor = Red` (was default), bringing it in line with Load Test Data + Wipe ALL. Restore Real Data stays neutral (audit explicitly called it "correct").

### Already done before this commit (verified, no changes needed)
- **#3 Default currency = system locale on fresh install** — landed in C04 Stage 3 (3.2.6, `rememberLastCurrencyOrLocale`).
- **#6 Developer section hidden in release** — already gated by `BuildConfig.DEBUG` at SettingsScreen line ~527. Audit was reporting an older state.
- **#11 Recalculate Balances description** — already covered by C02.

### Skipped as N/A
- **#7 Move Wipe ALL Data into Danger Zone** — Wipe ALL Data is debug-only (inside the DEBUG-gated Developer section, so absent from release builds entirely). The user-facing "Reset All Data" in the Danger Zone covers end-user needs. Moving Wipe ALL would expose a destructive debug-only tool to end users — wrong direction.

### Deferred (own commit)
- **#4 Report a Bug → in-app form with auto-attached version + device + last error log** — this is a whole new screen with form fields, crash-log attachment, email composition, and back-nav plumbing. Logged as Task #26. The current behavior (mailto: intent with device-info prefilled in body) still works; the audit's improvement is "form-with-attachments" replacing the email-compose flow.

### Closes
- **UX_AUDIT §C18 — Settings cleanup** (effectively, modulo #4 Report-a-Bug form). 6th P1 Sprint 2 cluster done.

### Changed
- **`versionName`** `3.2.19` → `3.2.20`, **`versionCode`** `142` → `143`.

### Data-integrity gate
Unchanged at **114 tests across 10 classes**, 0 failures (pure UI + prefs refactor — no logic that needs new test coverage; existing `UserPreferences` and `ReminderScheduler` paths still work).

### Sprint 2 P1 milestone
After this commit, **6 P1 Sprint 2 clusters are CLOSED**: C04 (3.2.6), C06+C07 (3.2.12), C08 (3.2.18), C09 (3.2.19), **C18 (3.2.20)**. Remaining P1: C12-C15 (screen redesigns), C21 (PDF/XLSX export quality polish).

## [3.2.19] - 2026-05-27 *(Development milestone — C09 CLOSURE: UTF-8 mojibake fixes + regression guard; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C09 — UTF-8 encoding pipeline bug in dialogs. CLOSES C09.** The audit's three named dialogs (Load Test Data, Restore Real Data, Wipe ALL Data) rendered garbled characters: `âš ï¸` instead of `⚠️` and `â‚¹` instead of `₹`. **Root cause confirmed not at the rendering pipeline level** (no `strings.xml` issue, no resource-decoding bug) — the source files themselves had been saved through a Windows-1252-thinking editor at some prior commit, scrambling every multi-byte UTF-8 codepoint into 3-char Latin-1 sequences. Fixed:
  - **4 user-facing string literals** restored to proper Unicode:
    - `SettingsScreen.kt:539` — Load Test Data dialog: `âš ï¸` → `⚠️`
    - `SettingsScreen.kt:555` — Restore Real Data dialog: `â‚¹3,962` / `â‚¹1,22,500` → `₹3,962` / `₹1,22,500`
    - `SettingsScreen.kt:562` — Wipe ALL Data dialog: `âš ï¸` → `⚠️`
    - `FinanceViewModel.kt:1102` — Dummy data note: `EMI â‚¹2500` → `EMI ₹2500` (only visible to users who load test data)
  - **14 comment / section-divider mojibake instances** cleaned up across the same 3 files: em-dashes (`â€"` → `—`) and box-drawing horizontals (`â"€` → `─`). Code hygiene; no runtime user impact, but the file is now valid UTF-8 throughout.

### Added
- **NEW `Utf8MojibakeDataIntegrityTest`** in `app/src/test/java/app/fynlo/data/` — 2 cases that scan every `.kt` / `.xml` file under `src/main/` and fail the CI data-integrity gate if any of 6 known mojibake byte sequences appear (`â‚¹`, `âš `, `ï¸`, `â€"`, `â€"`, `â"€`). Catches future regressions before they ship — if someone edits a source file on Windows with an editor that re-saves as CP1252, the test fails with `file:line — mojibake for 'X'` listing every offending site.

### Notes
- **PDF date format `20260525` from the audit was NOT reproduced in current code.** `ExportUtility` uses `LocalDate.now()` which already toString()s as `2026-05-27` (ISO 8601 with dashes). The audit-described bug was likely fixed in a prior commit; current behaviour matches the audit's acceptance criterion (`25-05-2026` per user date-format setting).
- **The audit's "Resources.getString() returning ISO-8859-1" hypothesis (fix point #1) turned out to be wrong.** The dialog strings aren't in `strings.xml` — they're Compose `Text("...")` literals in `.kt` files. The root cause was just a source-file encoding issue, no resource-pipeline issue. Documented for future cluster work.

### Closes
- **UX_AUDIT §C09 — UTF-8 / encoding pipeline bug in dialogs**. 5th P1 Sprint 2 cluster closed.

### Changed
- **`versionName`** `3.2.18` → `3.2.19`, **`versionCode`** `141` → `142`.

### Data-integrity gate
**112 → 114 tests across 10 classes** (+2 from `Utf8MojibakeDataIntegrityTest`), 0 failures.

### Sprint 2 P1 milestone
After this commit, **five P1 Sprint 2 clusters are CLOSED**: C04 (3.2.6), C06+C07 (3.2.12), C08 (3.2.18), **C09 (3.2.19)**. Remaining P1: C12-C15 (screen redesigns), C18 (Settings cleanup), C21 (PDF/XLSX export quality polish).

## [3.2.18] - 2026-05-27 *(Development milestone — C08 Stage 4 + C08 CLOSURE: PDF + XLSX export migration; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C08 Stage 4 — PDF + XLSX export migration. CLOSES C08.** Final stage of the number-formatting consistency cluster. Two changes, one is load-bearing for Excel users.

  - **PDF (`ExportUtility.kt`) — currency-aware formatting.** The private `fmt(v) = "₹${String.format(locale, "%,.2f", v)}"` helper hardcoded ₹ and `.2f` precision regardless of project currency. Now delegates to `CurrencyFormatter.detail(v, currencyCode, locale)`. Threaded `currencyCode` through all 3 PDF generators (`generatePDF`, `generateMoneyFlowPDF`, `generateLoanStatementPDF`) plus the `generateMoneyFlowCSV` text export. Callers updated: `FinanceViewModel.exportToPDF` reads `currentProject.value?.currency ?: "INR"`; `CustomerDetailScreen.kt:148` (loan statement) and `MoneyFlowScreen.kt:189` + `:205` (money flow CSV + PDF) pass their existing `currencyCode` derivation. Visible result: Indian lakh-crore grouping (`₹2,41,663`) for INR/NPR/LKR/BDT projects in PDF cards/tables; Western grouping for other currencies; no decimals per Detail spec.

  - **XLSX (`ExcelExportUtility.kt`) — load-bearing numeric-cell fix.** Pre-3.2.18 every cell emitted as `<c t="s"><v>idx</v></c>` (shared-string lookup). Even numeric amount fields. Excel saw amounts as text → `SUM()` returned 0, sorting was alphabetic, charts couldn't plot the column. Migration:
    - NEW `Cell` sealed class — `Cell.Text(String)` for strings (still goes through shared-string interning), `Cell.Number(Double)` for amounts.
    - Row model changed from `List<String>` to `List<Cell>`. Every per-sheet builder updated: amounts go through `n(value)` shorthand (→ `Cell.Number`), labels through `t(value)` (→ `Cell.Text`).
    - `buildSheet` now emits the right cell type: text → `<c t="s" s="..."><v>idx</v></c>` (shared-string lookup); number → `<c t="n" s="2"><v>15000.00</v></c>` (raw double value with US-locale decimal, referencing the new number-format style).
    - `STYLES_XML` gained a custom `numFmt` (`numFmtId="164"`, `formatCode="#,##0.00"`) and a corresponding cell-XF style (`s="2"` = `STYLE_NUMBER`). Excel renders the column as comma-grouped numbers; users can apply their own currency symbol via cell formatting if they want lakh-crore or specific currency display.
    - All 8 sheets updated: Accounts (Balance), Transactions (Amount), Lending (Principal/Rate/Paid), Debts (Principal/Rate/Paid), Investments (Invested/CurrentValue/Growth/Growth%), Loan Repayments (Amount), Debt Repayments (Amount). The Metadata sheet stays all-Text since its values are timestamps/labels.

### Closes
- **UX_AUDIT §C08 — Number formatting inconsistency** (4-stage migration across 4 sub-versions: 3.2.13 / .14 / .15 / .18). 4th P1 Sprint 2 cluster closed.

### Notes
- Sites migrated through C08: ~216 total (~33 foundation tests + 52 Hero/ListRow/Negative + 147 Detail + ~14 export sites + helper deletions). Sites NOT migrated (correctly excluded): `OutlinedTextField` `value =` raw-state strings (would break IME mid-typing) and percentage / ratio formatting (not currency).
- **Audit's Detekt lint rule deferred** to INF backlog. Adding a Detekt custom rule that fails on `String.format("%.2f", amount)` patterns is workable but not blocking — every existing site is migrated; the rule would only catch regressions.
- **XLSX columns can be re-formatted by user.** The `#,##0.00` format is locale-neutral. If the user wants lakh-crore grouping or a currency-symbol prefix in their spreadsheet, they apply it via Excel/Sheets cell formatting. The load-bearing change is that the underlying value is now a number, not a string.

### Changed
- **`versionName`** `3.2.17` → `3.2.18`, **`versionCode`** `140` → `141`. C08 closure milestone.

### Data-integrity gate
Unchanged at **112 tests across 9 classes**, 0 failures (Stage 4 is pure refactor at the export boundary; the XLSX numeric-cell change has no business-logic delta, and the PDF change is plumbing `currencyCode` through existing well-tested formatting).

### Sprint 2 P1 milestone
After this commit, **four P1 Sprint 2 clusters are CLOSED**: C04 at 3.2.6, C06 + C07 at 3.2.12, **C08 at 3.2.18**. Remaining P1: C09 (UTF-8 in dialogs), C12-C15 (screen redesigns), C18 (Settings cleanup), C21 (PDF/XLSX export quality proper — mostly the export-related design work like proper page breaks, group totals, embedded fonts for ₹/अ glyphs).

## [3.2.17] - 2026-05-27 *(Development milestone — EMI Calculator navigation entries; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **EMI Calculator — added missing navigation entry points (latent bug surfaced by 3.2.16 smoke).** User asked "where can I find" after the 3.2.16 visual polish + rename. Investigation: the `LoanCalc` screen has been **registered as a route since 3.0+ but had ZERO entry points in the UI** — not in the side drawer, not in the Reports hub tiles, not anywhere. Pure orphan. The 3.2.16 polish made an unreachable screen prettier; this commit makes it reachable.
  - **Side drawer "Finance Tools" section** — new `DrawerItem(Icons.Default.Calculate, "EMI Calculator", ...)` placed after Investments. Conceptually correct grouping (calculator is a finance tool).
  - **Reports hub** — new `ReportLinkCard("EMI Calculator", ...)` tile on a new third row. `ReportsHubScreen` signature gained an `onNavigateToLoanCalc: () -> Unit = {}` parameter (default no-op preserves preview composability); `Navigation.kt`'s `ReportsHubScreen` call wires it to `navGated(Screen.LoanCalc.route)`.

### Notes
- This is the **last known orphaned screen** in the navigation graph. Audit confirmed: every other route (MoneyFlow / DebtPayoff / NetWorthH / InterestIncome / MonthlySummary / ProfitLoss reachable via Reports tab; Calendar reachable via Lending screen; GlobalSearch via top-bar search icon; FlowWizard via Flow CTAs) has at least one entry point. LoanCalc was the lone unreachable.

### Changed
- **`versionName`** `3.2.16` → `3.2.17`, **`versionCode`** `139` → `140`.

### Data-integrity gate
Unchanged at **112 tests across 9 classes**, 0 failures (pure navigation plumbing).

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
