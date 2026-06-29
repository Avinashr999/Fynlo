# Fynlo Ledger Release Knowledge Hub - Internal Testing 3.2.106

Date range covered: 2026-06-12 to 2026-06-20
Current release line: internal testing, versionName 3.2.106, versionCode 230
Package name: app.fynlo
App name: Fynlo Ledger
Status: Internal testing is active in Google Play Console. Production launch is not complete yet.

## Mandatory use

Before any next release, Play Console work, accounting fix, Firestore fix, UI revamp, export change, or store asset update, read this file with:

1. PROJECT_STATE_FOR_AI.md
2. AI_AGENT_PROTOCOL.md
3. DESIGN_SYSTEM.md
4. UX_AUDIT_2026-05-25.md
5. docs/superpowers/specs/2026-06-17-template-lock-revamp-design.md
6. release_notes/README.md

This file is the handoff memory for the intensive pre-launch session. If a future AI or contributor is missing this context, they can easily repeat solved problems, especially around reset/sync, investment funding, Play Console setup, PDF layout, and template design consistency.

## Release status

The latest internal testing bundle was built after the reset/cloud sync fix and uploaded to Google Play internal testing.

Latest known AAB path:

```text
C:\Users\user\AndroidStudioProjects\Fynlo\app\build\outputs\bundle\prodRelease\app-prod-release.aab
```

Latest known Play release state:

- Internal testing active.
- Release available to internal testers.
- Version code 229.
- Version name 3.2.105.
- Google Play review status: not reviewed at time of upload.
- Native debug symbol warning was shown. This is useful to fix later for better crash analysis, but it did not block the internal release.

Release notes used for the final internal test upload:

```text
<en-IN>
Improved Reset All Data and cloud sync reliability.
Fixed old test data returning after fresh reset.
Improved ledger cleanup for clean testing.
</en-IN>
```

## Core accounting and money-action fixes

### Investment funding and net worth integrity

Issue found: adding/deleting/restoring an investment could increase net worth while Personal Cash stayed unchanged. The specific observed issue was net worth moving upward without an actual income or cash movement.

Fix direction completed:

- Investment funding now carries the selected source account id through `InvestmentDialog -> FinanceViewModel -> FinanceRepository`.
- The repository deducts by account id, not only by account name.
- The funding transaction records `fromAcctId`.
- Investment delete/restore restores only from the original funding transaction.
- This prevents repeated delete/restore from silently adding money back to net worth.

Do not regress this. Any future investment add, withdraw, delete, restore, or edit work must preserve source-account traceability.

### Double transaction and journal-only safety

The session also addressed double-counting risks from paired financial actions. Auto-generated journal-only rows must not behave like normal editable user-created cash actions unless the code intentionally supports a safe reversal path.

Important rule: account balance impact must come from one clearly owned money action, not from both a parent action and its derived journal row.

### Loan, debt, and investment traceability

User requested stronger accountability, not just totals. The app now treats traceability as a product requirement:

- Loans should show where the lent amount came from.
- Loan repayment should show where the money was deposited.
- Debts should show which account received borrowed money.
- Debt payments should show which account paid the debt.
- Investments should show which account funded the investment.
- Investment withdrawal should show which account received the withdrawal.
- Transaction detail/history should carry these links where available.
- Audit trail CSV should preserve timestamp, action, entity type, entity id, title, account, amount, before/after, and reason.

Ledger health can identify missing historical trace links. Legacy dummy records can still show warnings because older records were created before trace metadata existed. New records should be traceable.

## Reset, local data, and Firestore sync fix

Issue found: after Reset All Data, old cloud data could reappear from Firestore. The user could reach zero only after repeating reset actions, which is not acceptable for a fresh start flow.

Root causes identified:

- Firestore listeners did not handle removed records consistently for accounts, people, projects, and investment valuations.
- `SyncManager.stopListening()` cancelled its scope, then `startListening()` attempted to reuse a cancelled scope.
- Reset All Data did not clear all local audit/tombstone support tables.

Fixes completed:

- `FynloDao.kt` added cleanup/delete helpers including deleted remote docs, people, projects, and valuations.
- `SyncManager.kt` now recreates its scope after cancellation and handles removed remote records for more entity types.
- `FinanceRepository.kt` reset/wipe flow clears audit events and deleted remote document records.

User-verified behavior after the fix:

- Reset All Data alone made the dashboard empty.
- Old data did not return immediately afterward.

Operational rule for users and testers:

- For a fresh start, use Reset All Data.
- Do not press "Reset cloud sync to match local" after reset unless the local state is intentionally the state you want pushed to the cloud.
- If old data is visible locally, reset-cloud-sync will push that visible local state. It is not a cleanup button.

## UI and design revamp

The user approved the visual direction from the template screenshots and asked to apply that design language throughout the app.

Design source of truth:

```text
docs/superpowers/specs/2026-06-17-template-lock-revamp-design.md
docs/superpowers/mockups/fynlo-ledger-pro-template.html
```

Revamp areas touched during the session:

- Intro/login screen and app icon usage.
- App shell and top bar.
- Floating/pill bottom navigation.
- Dashboard spacing and cards.
- Loans list and loan details.
- Debt details and payment flows.
- Investment portfolio, holdings, update/withdraw actions.
- Reports hub and report detail screens.
- Expenses screen and add transaction flow.
- Settings, personalization, backup/export, ledger health, formatting, app info.
- Hamburger/deep utility screens including projects, money flow, budgeting, history, profile/security/contact-related flows.
- Dialogs, sheets, buttons, chips, and disabled states.

Design corrections made from user smoke screenshots:

- Update buttons in investment cards were inconsistent colors; they were unified.
- Dashboard was too airy; spacing was tightened.
- Loan search/keyboard blank area was improved.
- Settings feedback visibility was improved.
- The old quick-add FAB was removed where each screen already has its own add action.
- More uniform add buttons were applied across main screens.
- Several older-looking dialogs were refreshed into the newer sheet/dialog style.

Known design principle going forward:

- Do not introduce a third visual language.
- Keep screens close to the approved template: crisp headings, soft surface cards, deep green primary actions, restrained amber/red only for semantic states, and compact but readable spacing.

## PDF, Excel, and report export work

The user showed exported PDFs where headings, charts, and tables overlapped. The export layout was revised.

Fix goals completed:

- Report charts no longer overlap headings.
- Table sections are spaced better.
- PDF pages are cleaner and easier to read.
- Excel exports remain structured for review.
- Report styling better matches the app theme.

Future rule:

Any report/export change must be visually checked as an actual exported file, not only by compiling code.

## App icon and Play Store assets

Assets prepared for Play Store listing:

```text
play_store_assets/icon_512x512.png
play_store_assets/feature_graphic_1024x500.png
```

Important user feedback already handled:

- The icon text/capitalization needed refinement.
- The waveform symbol needed to match the in-app icon more closely.
- The feature graphic cards were initially too cramped and labels overflowed.

Phone screenshots prepared for Play Store upload:

```text
play_store_assets/screenshots_upload/01_signin.png
play_store_assets/screenshots_upload/02_dashboard.png
play_store_assets/screenshots_upload/03_add_transaction.png
play_store_assets/screenshots_upload/04_loans.png
play_store_assets/screenshots_upload/05_invest.png
play_store_assets/screenshots_upload/06_reports.png
```

These phone screenshots were prepared as 9:16 images, at least 1080 px on each side, and under the Play Store file-size limit.

Still optional before broader launch:

- 7-inch tablet screenshots.
- 10-inch tablet screenshots.

## Play Console setup decisions

Use these answers unless the app behavior changes.

### Store identity

- App name: Fynlo Ledger
- Package name: app.fynlo
- Pricing: free
- Main category: Finance
- Useful tags: Finance, Personal Finance, Investment, Loan
- Business tag can be added if helpful, but it should not replace the core finance positioning.

### Privacy and deletion links

Privacy policy:

```text
https://avinashr999.github.io/Fynlo/privacy/
```

Account/data deletion:

```text
https://avinashr999.github.io/Fynlo/delete-account.html
```

### Ads

Declare: no ads.

### App access / sign-in

Current behavior:

- Google Sign-In is supported.
- Continue without signing in is supported.
- No payment-gated review-only areas were active during internal release setup.

Play Console restricted access answer used/recommended at this stage: no restricted access. If a future subscription locks important screens, this answer must be revisited and reviewer access instructions may be required.

### Content rating and age

Recommended:

- App type: All other app types.
- No sexual, violent, language, or similar rating content.
- Online content: no media/social/feed-style content. Firebase sync is app data sync, not user-facing online content like Netflix/Amazon/news.
- Location sharing: no.
- Digital goods purchases: no, unless subscriptions/in-app purchases are activated later.
- Cash rewards/crypto/play-to-earn: no.
- Web browser/search: no.
- Primarily educational: no.
- Target age: 18 and over recommended because it is a finance ledger, even though younger users could technically track budgets.
- Restrict managed/child users if Play Console asks, because the app handles finance-like records.

### Data Safety

Likely disclosures based on current app behavior:

Collected or synced when user chooses Google/cloud features:

- Name/email/user id through Google/Firebase auth.
- Financial information entered by the user, including accounts, loans, debts, investments, transactions, balances, reports.
- Contacts/contact-like person records if the user creates or imports them.
- Crash logs, diagnostics, performance data, and device identifiers through Firebase/Crashlytics.

Not collected unless future features add it:

- Physical location.
- SMS/MMS/email message content.
- Photos/videos/audio.
- Calendar events.
- Web browsing history.

Transit/security:

- Data in transit should be declared encrypted where Firebase/Google services and HTTPS are used.
- Crashlytics diagnostics are for analytics/app performance and crash debugging.

Purpose mapping:

- App functionality: financial records, auth, sync, reports, backup/export.
- Account management: Google account linking/sync.
- Analytics/app performance: Crashlytics and diagnostics.
- Fraud prevention/security/compliance: auth/security-related identifiers where applicable.
- Personalization: only if user display name/theme/preferences are synced or stored.
- Advertising/marketing: no, unless future ad/marketing SDKs are added.

### Financial feature declaration

Fynlo Ledger is a personal finance ledger/tracker. It is not a bank, lender, payday loan app, direct loan provider, payment wallet, money transfer service, stock trading platform, crypto platform, or financial advisor.

If Play Console asks for financial features, choose the closest non-regulated personal finance / tracker / portfolio-management option available, and avoid selecting direct lending, payday lending, banking, wallet, money transfer, or trading unless the app actually starts offering those services.

## Build and verification commands

Use prod debug tasks for verification, not plain debug:

```powershell
.\gradlew.bat :app:compileProdDebugKotlin --no-daemon
.\gradlew.bat :app:testProdDebugUnitTest --no-daemon
```

Production release bundle:

```powershell
.\gradlew.bat :app:bundleProdRelease --no-daemon
```

Release bundle builds can take more than five minutes. Use a longer timeout for future production AAB builds.

Phone install tasks used during the session:

```powershell
.\gradlew.bat :app:installProdDebug --no-daemon
.\gradlew.bat :app:installProdRelease --no-daemon
```

Latest known verification status from this session:

- compileProdDebugKotlin passed.
- testProdDebugUnitTest passed.
- prod debug install passed.
- prod release AAB build passed after using a longer timeout.
- User completed broad manual smoke on real phone.
- User confirmed the current state looked good after the latest production/developer app comparison.

## Manual smoke checklist for next release

Before the next Play upload, verify these on a real phone:

1. Fresh install or Reset All Data starts with empty dashboard.
2. Continue without signing in works.
3. Google Sign-In behavior is understood and does not block review access.
4. Dashboard totals update after income, expense, transfer, loan, debt, investment.
5. Add/edit/delete account works and disabled actions explain why.
6. Add loan from a selected account shows source account in loan detail and transaction history.
7. Receive loan repayment into a selected account updates balances and trace.
8. Delete/restore loan does not double-count money.
9. Add debt to a selected account shows destination account and trace.
10. Pay debt from a selected account updates balances and trace.
11. Delete/restore debt does not double-count money.
12. Add investment from selected account deducts that account and records funding source.
13. Delete/restore investment reverses only the original funding transaction.
14. Withdraw investment credits the selected account and records trace.
15. Expenses can be added from the Expenses screen.
16. Transaction edit/delete/reversal does not create duplicate balance impact.
17. Reports open and charts are readable.
18. PDF export has no overlapping headings, charts, or tables.
19. Excel export opens with clean sheets and readable columns.
20. Ledger health has no critical issues for newly created records.
21. Audit trail records add/edit/delete/restore/payment/correction events.
22. Audit trail CSV exports and opens in Excel.
23. Settings personalization shows visible save feedback.
24. Reset All Data clears local and cloud-backed state without old data returning.
25. Reset cloud sync to match local is used only intentionally.
26. PIN and biometric lock flows still work if enabled.
27. Bottom navigation is readable and consistent across main screens.
28. Search screen does not create awkward blank space when keyboard opens.
29. Intro/login screen uses the latest app icon.
30. About, Privacy, Terms, Legal Disclaimer, and data deletion links are reachable.

## Known non-blockers and future work

These were not blocking the internal testing release but should be considered before wider production rollout:

- Upload native debug symbols if practical, so Play Console crash and ANR reports are easier to analyze.
- Consider real tablet screenshots for 7-inch and 10-inch Play listing sections.
- Plan a separate Gradle/AGP migration for deprecation warnings related to `android.builtInKotlin=false` and `android.newDsl=false`. Do not mix this with a launch hotfix unless necessary.
- Consider target SDK 37 later when the toolchain and dependencies are ready. Current uploaded release used target SDK 36.
- Continue improving ledger health so legacy/dummy data can be migrated or explained more clearly.
- Consider a stronger in-app "What's new" and "How to use" area for testers and first-time users.
- If subscriptions are enabled later, revisit Play Console app access, Data Safety, payments, and free/paid app strategy.

## Do not repeat these mistakes

- Do not assume Reset cloud sync is a cleanup button. It pushes local state.
- Do not let investment restore add cash unless the original funding transaction supports that exact reversal.
- Do not build only plain debug tasks. Use prod debug verification tasks for this app.
- Do not upload a new Play bundle with the same versionCode as a previous uploaded bundle.
- Do not enter Play release notes without language tags. Use `<en-IN>...</en-IN>` or the correct locale tag.
- Do not treat dummy-data ledger health warnings as automatically acceptable for new records. New records must be traceable.
- Do not create a new design style outside the approved template direction.
- Do not claim Play metrics are broken immediately after internal release. Play Console stats can take time and may be sparse for small internal tests.

## Current mental model

Fynlo Ledger is not just a pretty finance app. The core promise is trustworthy personal ledgering: every money movement should have a source, destination, visible trace, reversible behavior, and an audit story. Design polish matters because it builds confidence, but accounting correctness is the product.

## Visible brand rename - 2026-06-24

Visible app, store, legal, onboarding, About, export, PDF, CSV, backup, and Play listing copy now uses **Fynlo Ledger** to avoid Play Store naming confusion with other apps named Fynlo.

Do not rename the technical package, namespace, database, preferences, class names, or stable IDs as part of this brand change. The package remains `app.fynlo`, and internal identifiers such as `FynloDatabase`, `Fynlo_pin`, and `Theme.Fynlo` remain stable intentionally.

Next Play upload after this rename should use versionCode 230 / versionName 3.2.106 or newer, and the Play Console store listing should show app name `Fynlo Ledger`.
## Google Sign-In Internal Testing Issue - 2026-06-23

Tester symptom: tapping Continue with Google on the internal testing build showed "Google sign-in is not available in this build" / developer error code 10.

Diagnosis: this is normally a Firebase/Google OAuth signing-certificate mismatch for the Play-installed build. Internal testing installs are signed by Google Play App Signing, not only the local upload/debug key. Firebase must include the Play App Signing SHA-1 and SHA-256 for package `app.fynlo`.

Required Play/Firebase routine:

1. First try Google Play Console > App signing/App integrity if visible in the current Console layout.
2. If that page is hidden, use the proven fallback below: App bundle explorer > Downloads > download `Signed, universal APK`, then extract the `V3.0 Signer` certificate with `apksigner verify --print-certs`.
3. Add the Play App Signing SHA-1 and SHA-256 to Firebase Console > Project settings > Android app `app.fynlo`.
4. Ensure Google sign-in is enabled under Firebase Authentication > Sign-in method.
5. Download a fresh `google-services.json` if Firebase regenerates clients, and replace `app/google-services.json`.
6. Rebuild and upload a new internal testing AAB if the config file changed.

Code hardening done: `GoogleSignInHelper` now reads `R.string.default_web_client_id` generated by the Google services plugin, with the previous web client id kept only as fallback. The visible error message now says the Play build setup is missing instead of implying Google sign-in is intentionally disabled.

### Play App Signing Fingerprints - 2026-06-23

Play Console did not expose App integrity/App signing in the visible sidebar for this account layout. Fallback used:

1. Google Play Console > App bundle explorer > Downloads.
2. Download the Play-generated `Signed, universal APK`.
3. Run Android SDK `apksigner verify --print-certs` on that downloaded APK.
4. Use the `V3.0 Signer` certificate values for Firebase. Ignore the `Source Stamp Signer` certificate.

Play App Signing certificate extracted from `229.apk`:

- SHA-1: `D8:31:51:86:FD:1E:11:6A:AF:46:7C:9F:88:5C:82:B4:FD:B0:89:95`
- SHA-256: `A1:C6:ED:60:B9:13:CC:5D:F8:B9:41:43:BE:3C:66:88:59:88:8E:37:3A:D9:C1:B5:BF:97:93:84:A2:2A:7F:2A`

## Security Hardening Roadmap - 2026-06-23

Completed safe-now:

- FileProvider no longer exposes the whole app cache, files directory, or external cache.
- Generated share files now live under `cache/exports/` only.
- Export/share flows covered by this rule: Reports PDF, P&L PDF, Money Flow CSV/PDF, Monthly Summary CSV, and Loan Statement PDF.

Focused security review result: no launch-blocking issue found for the internal testing release. The official Codex Security deep scan workspace was opened, but deep-mode preflight could not prove worker-slot capacity, so treat that deep scan as not finalized.

Phase 2, after Play listing/internal-testing stabilization:

- Decide whether the local ledger database needs full encryption beyond Android app sandbox and PIN protection.
- Add stricter Firestore rule validation for document fields and schema once sync behavior is stable.
- Revisit Crashlytics/Analytics privacy controls if store disclosure or user consent strategy changes.
- Upload native debug symbols for future Play Console crash/ANR analysis.

## 2026-06-24 - Test Android Apps pass and network security cleanup

- `test-android-apps` analysis was requested. No adb device or emulator was available, so screen/performance automation could not run in that pass.
- Local verification passed for prod-debug unit tests and Kotlin compilation.
- `lintProdDebug` passed after moving debug user-certificate trust into `debug-overrides` in `app/src/debug/res/xml/network_security_config.xml`.
- Production network security already trusted only system certificates and disallowed cleartext traffic; this cleanup prevents debug lint noise without weakening Play release security.

## 2026-06-24 - Pending phone-connected Android QA

When the phone is connected again, run the remaining `test-android-apps` device-side checks that were blocked by missing adb device/emulator:

- Confirm adb sees the phone.
- Install or open the latest production/developer build as needed.
- Capture screenshots for login/offline, dashboard, loans, invest, expenses, reports, settings, and key dialogs.
- Watch logcat for crashes, Google sign-in errors, Firestore sync/reset errors, and ledger/action failures.
- Run basic performance checks for navigation, modal open/close, add/edit/delete flows, and report/export paths.
- Treat this as the continuation of the 2026-06-24 Android testing pass, not a new unrelated task.

## 2026-06-24 - Fynlo Ledger legal page cleanup

- Confirmed Privacy Policy, Account/Data Deletion, legal index, and Terms pages use the visible `Fynlo Ledger` brand.
- Updated About screen legal links to the public GitHub Pages privacy, terms, and data deletion URLs.
- Removed developer-only publishing placeholders from `docs/terms.md`; Terms still needs owner/lawyer factual review before monetized production launch, especially subscription and acceptance wording.

## 2026-06-24 - Phone QA date picker polish

- Phone-connected QA continued on the production package after installing the latest prod-debug build.
- Main screens and core sheets were visually checked on the connected phone: dashboard, loans, invest, reports, expenses, search, drawer, settings, add account, add transaction, add loan, and add investment.
- One safe polish fix landed: `DatePickerField` is now picker-only/read-only. This prevents the keyboard from opening unexpectedly on date fields and keeps form sheets calmer.
- Future QA should still include a quick tap on date fields in add/edit transaction, loan, debt, budget, recurring, goal, and investment forms to confirm the Material date picker opens and the keyboard stays hidden.

## 2026-06-24 - Play Store asset pass for Fynlo Ledger

- Developer build was installed on the connected phone as `app.fynlo.dev` / `3.2.106-dev` / versionCode `230` before Play asset work.
- While capturing the sign-in screenshot, the Play review prompt appeared before login. `Navigation.kt` now gates the prompt behind both `isLoggedIn` and `isPinUnlocked`, so it cannot interrupt login/offline onboarding or store screenshots.
- Feature graphic regenerated at `play_store_assets/feature_graphic_1024x500.png` with `Fynlo Ledger` branding. The existing `play_store_assets/icon_512x512.png` remains valid and was not changed.
- Phone upload screenshots were refreshed and cleaned to exactly six files under `play_store_assets/screenshots_upload/`: `01_signin.png`, `02_dashboard.png`, `03_loans.png`, `04_invest.png`, `05_reports.png`, and `06_expenses.png`.
- Current phone screenshot dimensions are `1272x2800`, all below 8 MB and valid for Play phone uploads.
- Tablet screenshots remain a separate task: no tablet AVD exists locally, `emulator.exe` exists only under the SDK path, and `avdmanager` was not available to create a new tablet profile during this pass. Do not claim 7-inch/10-inch tablet screenshots until a real tablet or tablet emulator is used.

## 2026-06-25 - Debt edit destination-account delta safety

- Internal testing found a trust-critical debt edit issue: editing a debt amount updated the Debt page but did not adjust the account that originally received the borrowed money.
- Fix rule: the linked `Debt Received` transaction is the authoritative trace for where the debt money entered. Debt edits now update that transaction and apply only the principal delta to the original destination account.
- New debt funding transactions now persist `toAcctId`, so future edits can use id-based account updates instead of relying only on account names.
- Regression test added in `MoneyActionIdempotencyDataIntegrityTest`: create a debt into Business Investment, edit principal upward, confirm only the delta is added to that same account and the linked transaction remains traceable.
- Verification passed with the focused money-action integrity test, prod-debug Kotlin compilation, and full prod-debug unit tests.

## 2026-06-26 - Account correction on edit for core money actions

- Internal testing raised the broader case behind the debt-edit bug: users can pick the wrong account when creating a loan, debt, or investment, and the edit screen must let them correct it safely.
- Lending edit now shows the source account. Saving a corrected source reverses the original linked `Lending` transaction's account debit, debits the corrected account, and updates `fromAcct/fromAcctId`.
- Debt edit now shows the destination account. Saving a corrected destination reverses the original linked `Debt Received` account credit, credits the corrected account, and updates `toAcct/toAcctId`.
- Account-funded investment edit now shows the funding account. Saving a corrected funding source reverses the old funding account debit, debits the corrected account, preserves today's/current value, and updates the linked investment funding transaction.
- Dashboard due-soon now opens the collection calendar. That calendar includes both lent-out due items and debts owed, with overdue red, today green, near-due amber, and later upcoming blue; rows route to borrower or debt detail.
- If a tester reports a hand loan not appearing, first check the saved row fields: zero-interest unpaid loans are included in the active lending list unless status is settled/written off or paid is already at/above amount.
- Family Cash versus Business Investment correction path: after this build is installed, open the affected debt/loan/investment edit screen and correct the source/destination account. The app should reverse the old account movement and apply the corrected one; do not manually edit account balances unless ledger health still reports a mismatch after the correction.
- Verification: focused money-action integrity tests, prod-debug Kotlin compile, and full prod-debug unit tests passed.

## 2026-06-26 - Date picker and form selection polish

- Phone feedback found date selection could freeze or feel slow while jumping years in finance forms.
- Shared `DatePickerField` now uses a bounded year range of current year minus 80 through current year plus 50, which keeps normal finance use cases available and reduces picker workload.
- Lending add/edit now uses dropdowns for borrower and source account instead of wrapping chips, improving crowded forms on smaller phones.
- Investment add now uses a dropdown for funding source instead of three stacked source pills. Account and debt pickers were already dropdown-based.
- App launcher label source is already `Fynlo Ledger` for production and `Fynlo Ledger Dev` for dev. Launcher home-screen text may still truncate or cache; do not change package name or stable identifiers for this.

## 2026-06-26 - Debt-funded investment journal repair

- Internal testing found a balance-drift row: an old debt-funded BBS investment trace was stored as a real `Transfer` from Business Investment to Family Cash. It should have been trace-only, so it made Family Cash too high and Business Investment too low by Rs. 2,00,000.
- Future debt-funded investments now record trace rows as `Info` + `journal_only` with no account source/destination columns. Account balances are not touched by this explanatory trace.
- Startup repair converts legacy debt-funded investment `Transfer` traces to journal-only rows and reverses any accidental account movement once. This should fix the observed Family Cash / Business Investment drift when the updated app is launched.
- Transaction History now shows per-transaction before/after balance impact for affected accounts, which gives users a visible ledger path instead of forcing them to infer movement from totals.
- Ledger Health now reports a critical `Debt receipt amount mismatch` when debt principal and the linked `Debt Received` transaction amount differ. This catches legacy rows created before destination-account edit repair, but does not silently rewrite old balances.
- Verification: prod-debug Kotlin compile and full prod-debug unit tests passed.

## 2026-06-26 - Debt receipt mismatch and account drift repair

- Follow-up phone DB inspection found the exact remaining issue: one Kalyani Ammamma debt had principal Rs. 6,75,000 but the linked `Debt Received` transaction still had Rs. 1,00,000. That stale receipt left Rs. 5,75,000 missing from Business Investment.
- Added `repairDebtReceiptAmountMismatches()` and wired it into startup. It updates the linked receipt to the debt principal and applies only the difference to the original credited account.
- Added `repairAccountBalanceDriftFromLedger()` and wired it into startup immediately after receipt mismatch repair. It rebuilds stored account balances from account CREATE audit opening balances plus current transaction ledger rows, while ignoring `Info` / `journal_only` traces.
- Production-phone verification after installing and launching the repaired build:
  - Business Investment: Rs. 25,00,000.
  - Family Cash: Rs. 27,90,000.
  - HDFC: Rs. 39,906.
  - Kalyani linked receipt: Rs. 6,75,000 and tied to Business Investment account id.
- Audit Trail now contains explicit `REPAIR` rows for the receipt mismatch and each stored-balance reconciliation. If a tester asks "where did money go?", check these repair rows first.
- Regression tests added for Kalyani-style receipt mismatch and Family Cash-style stored balance drift. Verification passed: `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, prod debug install, and dev debug install.

## 2026-06-26 - Investment journal trace relink and source correction

- Phone DB inspection proved the visible Business Investment total was correct: seven `Debt Received` rows total Rs. 25,00,000. The mismatch was explanatory, not cash-moving: the Rs. 75,000 BZA investment trace was linked to the Rs. 2,50,000 BZA investment instead of the Rs. 75,000 row.
- Added `repairDebtFundedInvestmentJournalTraceRefs()` to startup. It relinks a wrongly attached `Info` + `journal_only` investment row to the matching investment amount, or creates a missing journal-only trace when no exact row exists. It never changes account balances.
- Ledger Health now reports investment trace problems directly: missing trace, amount mismatch, duplicate trace, or a debt-funded investment trace that still has account movement columns.
- Investment edit now lets users correct funding source between `From account` and `Existing loan`. This prevents a mistaken source tap from requiring manual balance/account surgery later.
- Valuation History contrast was improved after phone feedback that values/history text were too faint.
- Production-phone verification after install/launch:
  - Business Investment remained Rs. 25,00,000.
  - Business Investment receipt rows still total Rs. 25,00,000.
  - Debt principal and receipt amount match for the Business Investment debt receipts.
  - BZA Rs. 2,50,000 and BZA Rs. 75,000 now each have exactly one journal-only investment trace linked to their own investment id.
- Verification passed: `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, prod debug install, and dev debug install.

## 2026-06-26 - Internal testing release 3.2.107

- Bumped the next Google Play internal testing upload to `versionCode = 231` / `versionName = 3.2.107`.
- This AAB is needed because direct adb installs only update the connected phone; internal testers receive the fix only after a new App Bundle is uploaded and published on the internal testing track.

## 2026-06-29 - Account transfer and loans overview

- Account transfers are not income and not expense. They must reduce the source account and add the same amount to the destination account, leaving total account value and net worth unchanged.
- The write path now stores transfer rows under `Account Transfer`, while legacy `type=Transfer/category=Transfer` writes are normalized to the same category at the validator boundary.
- Ledger Health should flag a same-account transfer as critical; this protects against accidental no-route or self-route transfer rows.
- Loans overview should only split principal and interest at the top hub level:
  - Lent tab: `Total Borrowers`, outstanding borrower principal, outstanding borrower interest.
  - Owed tab: `Total Debtors`, outstanding debt principal, outstanding debt interest.
- Do not add this split to individual borrower/debtor pages unless a future product request asks for it.
