# Personal Ledger Design QA

## 3.3.25 / 265 - Evidence-Backed History

- History uses complete/recovered points only for chart, highest value and comparisons. Missing monthly baseline dates say Unavailable. The last 30 daily dates explicitly mark gaps; chart lines never bridge unsaved days. Original saved rows remain in an expandable archive, with recovered labels and preserved old values.
- Full rupees/paise replace Cr/K abbreviations on this history screen. Latest change uses a vertical layout; monthly comparisons share a row, highest value has full width. Source hash/policy stays in stored metadata, with plain backup-date text in the archive.
- Production phone visually checked: current 17,794,055.28, October 2 recovered 17,786,625.03, October 3 unavailable, one/six month unavailable; archive shows September 27 recovered 17,771,508.31 and both original values. No overlapping monetary text in the checked phone layout. Screenshot private at C:/Users/user/.codex/tmp/fynlo-3.3.25-history-qa/history.png (before final source-label-only adjustment).
- 607 unit tests pass, both APKs built/installed; history-only phone recovery and idempotent replay pass. Financial records unchanged in post-recovery DB comparison. No full PDF visual export, all-device accessibility sweep, or fresh cloud round-trip claimed. Existing header/hero styling outside the requested history integrity work was not redesigned.

## 3.3.24 / 264 - Physical Phone Verification

- Both APKs installed in place on Samsung SM_S942B with versions confirmed after private backups. Production Home, Net worth history and restart checked; developer guest Home and history checked. No financial form submissions or Fynlo crash observed. Production returned to Home.
- History screenshot inspected at C:/Users/user/.codex/tmp/fynlo-3.3.24-phone-qa/history.png (private). No Backfill action; saved-total labels and incomplete-history notice visible. Current history total agrees with Home after normal whole-rupee display rounding; the database stores the exact 17,794,055.28.
- Today's snapshot alone changed to the full balance sheet; older 28 rows preserved. Large displayed historic differences still reflect incomplete old snapshots and must not be presented as proven investment returns or cash movement. Existing repeated history heading and hero styling were not redesigned in this install-only task.
- All financial rows unchanged in before/after comparison, both SQLite integrity checks pass. Developer tables unchanged. One production deletion-marker timestamp refreshed normally. Prior full suite 598 passes; build/test tasks rechecked up-to-date. Two waiver reviews remain unchanged; no fresh sign-in/cloud round-trip or exhaustive screen audit claimed.

## 3.3.24 / 264 - Honest Net-Worth History

- Removed the approximate Backfill action and its stale empty-state instructions. Historical comparisons now say saved totals rather than asserting real gains/losses. One plain notice explains older incomplete data and calculation corrections; all existing historical rows remain accessible. Shared layout/theme unchanged.
- Current-day capture no longer uses a partially loaded UI summary. No phone installation or visual smoke in this task; compilation/build checks and repository/calculation tests are recorded in PROJECT_STATE_FOR_AI.md. Do not claim older history was reconstructed or the two waiver records were corrected.

## 3.3.23 / 263 - Physical Phone Delivery

- Samsung SM_S942B: both personal APKs updated in place with verified version/code after private backups. No uninstall/reset or financial submission.
- Production Home/Loans/Muhammed/detail/payment preview, debt detail/payment preview, History receipt/detail/edit, Settings and Book check opened successfully. Muhammed remains principal 14,00,000, interest 3,682, total 14,03,682; debt preview agrees with its statement. Restarted Home totals remain stable.
- Visually inspected dark-mode repayment history editor: readable explanation, description and notes only, disabled Save before changes, Cancel/Close available. Screenshot is private at C:/Users/user/.codex/tmp/fynlo-3.3.23-phone-qa/history-edit.png. Both real payment forms require purpose; no owner receipt was submitted.
- Four synthetic UI tests passed on the physical phone: borrower/debt receipt-note editors and borrower/debt interest-only previous-month purpose. Test-only APK removed afterward. Developer guest mode shows Local only, empty Loans/Owed and functioning new-loan/new-debt forms; cancelled without saving. No Fynlo crash found.
- Production financial tables identical before/after smoke, SQLite integrity OK; existing cloud deletion timestamp refreshed only. Developer added one automatic snapshot with financial tables still empty. Book check has 0 serious and 2 waiver reviews, left unchanged. Fresh auth/cloud round-trip and exhaustive every-account calculations are outside this smoke scope.

## 3.3.22 / 262 - Payment Purpose Safety

- C01/C12 financial integrity and repayment clarity. Shared FormDialog/FynloChoiceDropdown retained; no new colors or dashboard panels. Both borrower/debt forms require Interest only, Principal only, or Interest and principal before saving. Editing the amount retains purpose. Interest-only offers a clearly named previous-month settlement option, with plain text stating principal stays unchanged.
- Phone automated UI: PaymentPurposeUiTest OK (2 tests). Both forms rejected submit before purpose selection, then synthetic 29,000 previous-month interest saved principal 0 and correct period dates after amount editing. No owner receipt submitted by UI tests. Initial debt test selector matched the account field as well as Pay; narrowed to the monetary button and reran successfully.
- Real production statement after approved repair/restart: principal 14,00,000, October 1-4 interest displayed 3,682, total 14,03,682. List/detail/expanded money trail agree. Screenshot inspected, private evidence at `C:/Users/user/.codex/tmp/fynlo-3.3.22-payment-audit/phone-mohammed-corrected.png`. Existing header truncation and rounded whole-rupee detail formatting were not redesigned in this financial fix.
- 569 prod unit tests pass; prod/dev debug APKs build. Production installed 3.3.22 / 262; developer built only. Approved classification correction plus fresh-server verification passed; accounts unchanged and no duplicate financial rows. Test APK/payload removed from phone after checks. No production uninstall/reset, commit/push or AAB.

## 3.3.21 / 261 - Three Phone QA Follow-Ups

- Date: 2026-10-03 (work began 2026-10-02). Partial clusters C12 loan clarity and C16 color semantics, plus guest-status presentation hotfix. Archetypes: shared Home toolbar and Dialog. Existing blue/neutral tokens retained; no new decorative components, no schema/migration, no changes to stored repayments or financial write paths. Interest breakdown boundary fix has explicit regression coverage.
- Same-day simple loan/debt, 10,00,000 at 18%: accrued interest now includes the first day (493.15068... before currency formatting), matching existing due display 493 instead of zero. Same-day full/partial principal repayment and advance-interest breakdowns tested, future start remains zero, repeated calculation leaves rows intact. Existing paise due/settlement math is unchanged.
- Local-only cloud badge and tap message use actual Google linkage, not guest entry. Startup cloud feedback is suppressed for guests. Source-account balances in loan/debt forms use readable theme secondary text.
- Failing regression confirmed before fix: 4/5 initial tests failed with accrued zero. After fix: 562 unit tests, zero failures/errors/skips (six start-day tests, three cloud-presentation tests). Prod/dev debug builds and UI test APK compile pass. Existing test-only deprecation warnings remain; not production build failures.
- Both phone APKs updated in place after fresh validated private backups; installed names/codes confirmed. Phone initially relocked, then became accessible: developer guest Home announces Local only, and tapping shows device-only storage guidance; no false startup cloud feedback. Production Home shows Synced. New-loan/new-debt helper text visually inspected in dark mode, now neutral and readable. Both forms closed without saving; production returned to Home. No Fynlo crash in phone crash buffer (only the previous unrelated radio-service crash).
- Isolated Pixel 6 emulator tests: `LoanStartDayAndCloudUiTest`, OK (3 tests). Actual CollectPaymentDialog and PayDebtDialog with synthetic same-day 10,00,000 at 18% show two matching 493 labels (accrued and due), and badge account-link changes update without restart. Tests use in-memory fixtures/no-op submit callbacks, not owner financial records. No fresh Google sign-in, cloud round-trip or money submission exercised.
- Private phone evidence: `phone-dev-local-only.png`, `phone-loan-helper-fixed.png`, `phone-debt-helper-fixed.png` under `C:/Users/user/.codex/tmp/fynlo-3.3.21-qa/`. Existing backups are in the same directory, not Git. No records saved or deleted, no PIN/account changes, no AAB/commit/push. These bounded fixes do not constitute a full re-audit of all historical accounting methods.

## 3.3.20 / 260 - Drawer and Form Consistency

### Unlocked Phone Follow-Up

- 2026-10-02, Samsung physical phone, production dark theme: drawer and contextual titles; Settings/Personalization; signed-in Profile/security; Budget and Goal empty states/forms; Contact Book with real names and add form; History and read-only transaction detail; Recurring/add form; Projects/new form; EMI calculator; About/version; Loans/new-loan form. Opened and cancelled forms without saving. Persistent Close/header and scroll-to-action behavior checked on long forms. EMI input opened keyboard and scrolling dismissed it. No Fynlo crash in device crash buffer (only an unrelated radio-service crash).
- Developer app: continued locally from sign-in screen, checked empty Home, Settings and Add Account form, cancelled without saving. No Google login, logout, PIN change, project switch, financial mutation or cloud round-trip. Production returned to Home afterward.
- Existing production signed-in UI shows Cloud backup active and Synced. This is not proof of a new cloud round-trip. Developer guest toolbar still announces Connecting after visiting Settings/account form; record as a follow-up to local-only status presentation, not as verified sync.
- Remaining visual follow-up: new-loan source-account balance helper retains older green styling in dark mode; contrast/theme alignment needs a focused check. Existing separate accrued/due accounting-display finding below is unchanged.
- Private evidence in the existing QA directory: phone-drawer, phone-settings, phone-settings-expanded, phone-profile, phone-budget-form, phone-goal-form-scroll, phone-calculator-keyboard, phone-calculator-after-scroll, phone-contacts, phone-contact-form, phone-history, phone-history-detail, phone-recurring-form, phone-project-form, phone-about, phone-loan-form, phone-dev-settings, phone-dev-account-form (PNG). Phone scope does not include every populated utility state, money submission, fresh auth, PIN or destructive confirmation. No new source/build/version or Git commit/push in this follow-up. Initial locked-phone note below is historical, superseded by these bounded checks.

Date: 2026-10-02. Partial clusters C16 color semantics, C17 disabled-button hints, C18 Settings, C19 empty states and C20 drawer. Owner-approved blue/neutral design; UI-only changes, no financial, schema, auth or cloud writes changed.

- All existing drawer destinations retained; direct Transaction history added. Contextual toolbar titles replace duplicated headings across the requested utility pages, including History. Settings sections and About privacy/disclaimer content are flat; Profile controls no longer imply ordinary disabled/off states are errors.
- FormDialog is the shared bounded sheet for loan/debt creation/editing, income/expense, transaction edit, repayment, waiver and investment withdrawal plus existing utility/settings forms. Close/header stay visible while content scrolls. Native date picker, protected reset progress and auth-specific confirmations keep their behavior. This is not a claim that every rare historical dialog/state was exercised.
- Pixel 6 API 34 emulator with guest test records only: inspected drawer, Settings/expanded settings, Profile/security, Budgeting and its form, Goals and its form, Contact Book with a long name and add-contact form, Projects/new-project form, Recurring/add form, EMI calculator, About/disclaimer, History, new loan and collect-payment form. Opened/cancelled financial forms without saving payments. Calculator keyboard opened on input and dismissed on scroll. Dark Settings and goal form inspected; shared forms/confirmations tested at 1.6x font scale.
- Visual QA caught pale empty-state copy, pale budget slider labels, old About notice cards and a duplicated History title. Corrected to theme-aware readable copy/flat sections. Payment amount presets are secondary tonal actions with meaningful payment icons; selected-date help text no longer says the date is above when it is below.
- Final build: both debug variants and 553 prod unit tests passed (zero failures/errors/skips). All three added instrumented UI tests passed again on the final APK (OK, 3 tests): disabled-save/input behavior, long dark form/header scrolling, and wrapping large-text confirmation actions.
- Final APK visual recheck: dark Budget empty state/form, Profile/security and Settings. Readable empty-state and slider copy, quieter security controls, neutral Settings icons and subtle sheet borders confirmed. Evidence: `final-budget-empty.png`, `final-budget-form.png`, `final-profile-dark.png`, `final-settings-dark.png`. No app crash in emulator crash buffer. Emulator System UI briefly reported a boot ANR; waiting cleared it. Earlier screenshots show pre-final contrast tweaks and are not labelled final.
- Evidence is private in `C:/Users/user/.codex/tmp/fynlo-3.3.20-qa/`. Pre-update prod/dev tar backups validated (57/52 entries, including databases). No uninstall/reset or phone financial edits.
- Final APKs installed in place on the phone. Confirmed prod 3.3.20 / 260 and dev 3.3.20-dev / 260; both launches accepted. Phone `deviceLocked=1`; no phone visual approval claimed. Crash buffer contains only an unrelated old radio-service crash, no Fynlo crash.
- Separate existing accounting-display finding, NOT repaired in this design pass: a synthetic same-day simple loan (10,00,000 at 18%, 02-10-2026, no repayments) displays Interest Due 493 but Accrued interest 0 in Collect Payment. `InterestPolicy.borrowerSnapshot`/`debtSnapshot` use the paise engine for due and the older breakdown for accrued. Investigate and test that source alignment separately; do not infer actual principal/account corruption or change stored payments from this observation.
- No Play Store AAB, commit or push requested. Signed-in cloud/PIN flows and every populated utility state were not exercised.

## 3.3.19 / 259 - Contextual Header and Loans

Date: 2026-10-02. Partial clusters C08 navigation, C10 visual tokens, C12 loan presentation.

- Brand retained on Home only; other shared headers use route titles. Header collapses on downward list scroll and returns on upward scroll. Full-screen detail/search headers remain separate.
- Lent/Owed and status controls use underline tabs. Borrower/debt rows use a shared flat layout with readable names, amounts, dates and accounts. Existing calculations and data writes are unchanged.
- Light blue #0866FF and dark accent #4D94FF; matching launcher background.
- Pixel 6 API 34 emulator: inspected long-name borrower row and large amount in light/dark, privacy hidden/visible, Lent/Owed switching, add-loan form, contextual header and collapse/return behavior. Test contact/loan created only in the separate emulator guest ledger.
- Found primary-tab navigation could restore Contact Book instead of Loans. Primary tab navigation now opens the tab root rather than restoring a nested drawer route. Verified the final APK on emulator: Loans -> drawer Contact Book -> Loans returns to Loans with its header and list. Singular borrower label verified. No emulator app crash during final smoke; emulator System UI showed a startup ANR, which cleared after waiting.
- Phone pre-update backups saved privately in `C:/Users/user/.codex/tmp/fynlo-3.3.19-qa/`. No uninstall, data reset or financial edit on the phone. Phone is locked; 3.3.19 phone visual verification remains pending.
- Evidence: `loans-light.png` and `loans-dark.png` in that private directory. These captures precede the final singular-count wording fix.
- Final prod/dev debug builds and all 553 prod unit tests pass with zero failures/errors/skips. Both final APKs installed in place on the phone; versions 3.3.19 / 259 and 3.3.19-dev / 259 confirmed. Both launches accepted, no Fynlo crash in phone crash buffer. No release AAB or commit/push requested.

# Personal Ledger Design QA - 3.3.18 / 258

Date: 2026-10-02

## Approved Direction

Owner-selected chart-free blue/neutral dashboard, light and dark themes. Reference:
`C:/Users/user/.codex/generated_images/019f66a5-c133-7b92-ac61-93e67b8e3748/exec-bf69a515-5099-4030-8efb-a27754217b25.png`

Implemented hierarchy: net worth, assets/debts, Income/Expense/Transfer, flat account rows, compact daily interest, recent activity and explicit History link. Brand mark is the recognizable white wave on flat blue, shared with the adaptive launcher icon. Existing report charts, account actions, loan operations and safety reviews are preserved.

## Scope and Compliance

- Partial audit clusters: C07 dashboard, C08 navigation, C10 visual tokens, C12 loan presentation, C16 consistency.
- Archetypes: Home, Report, Dialog and Settings. Current owner-approved reference supersedes older green/floating-navigation rules as documented in DESIGN_SYSTEM.md.
- Tokens: Material color scheme (blue primary, neutral surfaces), LedgerIncome and semantic error; shared typography, spacing and form components. No decorative background circles or gradients added.
- Data-integrity touch: no financial calculations, financial write paths, schema, migration, authentication or cloud transport changes. Added a read-only Room-emission readiness signal to stop local empty lists showing endless cloud-dependent skeletons.
- Preserved exact values; display formatting now includes paise on the dashboard. Financial summaries use existing derived sources, not mock amounts.

## Automated Verification

- `:app:assembleProdDebug`: PASS.
- `:app:assembleDevDebug`: PASS.
- `:app:testProdDebugUnitTest`: PASS, 551 tests, zero failures/errors/skips.
- New regression coverage: recent activity date/time ordering, retention of financing/transfers, exclusion of generated journal duplicates without mutation, empty/short lists, light/dark text and button contrast >= 4.5:1.
- `git diff --check`: PASS (line-ending notices only).

## Rendered Verification

Pixel 6 API 34 emulator, separate developer ledger:
- Light and dark dashboard rendered and inspected.
- Test account amount 18,000,000.74 fits with paise; account form saves with existing feedback.
- Privacy hides summary and account amounts; History navigation opens.
- Cold guest setup/sign-in entry traversed. No real account sign-in or cloud upload performed.
- Found endless skeletons with a local-only empty ledger; fixed by local data readiness. Confirmed empty developer loan screen on the phone no longer shows loading skeletons after local data is ready.

Connected physical phone:
- Private pre-update backups of production and developer sandboxes saved outside Git. No uninstall, data clear or financial edit.
- Both installed versions confirmed: 3.3.18 / 258 and 3.3.18-dev / 258.
- Production: dashboard, account list, Loans Lent/Owed, Investments, Reports, Expenses, History and transaction detail, global Search, drawer and Settings opened.
- Dashboard headline and account values match pre-update rounded values, with exact paise now visible. Transaction detail retains account, before/change/after and edit/delete actions.
- Dark screenshots inspected for readability, header/system-bar separation, bottom navigation, large values, recent activity and account rows.
- Developer login restyled screen and guest launch checked. No new Fynlo crash in the phone crash buffer (an unrelated older system radio crash was present).
- No financial records changed during phone checks. Normal existing launch/sync behavior was not disabled or reimplemented.

## Evidence and Limits

Private screenshots and pre-update backups are under:
`C:/Users/user/.codex/tmp/fynlo-3.3.18-qa/`

Representative screenshots: `home-light.png`, `home-funded-light.png`, `home-funded-dark.png`, `phone-home-light.png`, `phone-loans-light.png`, `phone-invest-dark.png`, `phone-reports-dark.png`, `phone-recent-dark.png`, `phone-history-dark.png`, `phone-search-dark.png`, `phone-settings-dark.png`, `phone-dev-login.png`.
The two phone files named `*-light.png` were captured in the phone's existing dark theme; their filenames are historical, not evidence of phone light-mode coverage.

Smoke verification is not a claim that every dialog, tablet, extreme font size, money mutation, export or Google sign-in was retested. No cloud round-trip was repeated because cloud/auth implementation was unchanged. User visual acceptance is still required. No Play Store release or AAB; no commit/push requested in this task.
## 2026-10-04 - 3.3.23 Repayment History Edit Safety

- Linked loan/debt receipt editing now uses the existing FormDialog shell with Description and Notes only. Payment amount/date/purpose/account are not editable through history; explanatory text states that balances stay unchanged. Save remains disabled until text changes.
- Pixel 6 read-only emulator, offline, synthetic callbacks only: RepaymentHistoryUiTest passes for borrower and debt receipts, asserting exactly two editable fields, no amount/date editors, initial disabled Save, and no financial-field changes in the saved callback. Existing PaymentPurposeUiTest also passes for both borrower/debt previous-month interest-only flows. Four tests total.
- No owner-phone install, live financial mutation, broad visual redesign or live cloud test. Emulator assertions verify these forms, not an exhaustive visual review of every app screen.
## Final 3.3.25 Phone Verification

- Production restart retains the September 27 / October 2 recovered points and
  today's complete total; current net worth remains 17,794,055.28.
- Developer guest history displays its empty state, not old unverified totals.
- Final before/after comparison confirms all financial tables unchanged and both
  databases healthy. No Fynlo crash found in the inspected crash buffer.
- Observed existing developer guest startup delay while checking cloud backup;
  it resolves to empty Home. Not changed by the history recovery work.
