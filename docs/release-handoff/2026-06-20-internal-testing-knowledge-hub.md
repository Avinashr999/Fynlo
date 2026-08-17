## 2026-08-16 - New Investment Current Value Default Fix

- Fixed add-investment flow where a blank placeholder record could make a new holding save with current value `0` even when invested amount was entered.
- New holdings now start with current value equal to invested amount. Existing holdings keep their current value during normal edits.
- Any already-created zero-value holding should be corrected once using the investment value update action.

## 2026-08-16 - Dashboard Calm Pass

- Dashboard automation review was removed from Home to reduce noise and space usage.
- Bottom navigation wrapper was tightened so the floating pill no longer sits inside a visible padded background band.
- Automation/proof/budget/recurring features remain available from their own screens. No accounting, Firestore, sync, or ledger calculation logic changed.

## 2026-08-16 - Daily Interest and Net-Worth Explanation

- Dashboard now has a small read-only daily interest signal: receivable interest from borrowers, payable interest on debts, and the net daily effect.
- Net Worth History now explains assets, debts owed, and net worth in plain language so debt repayments or corrected money sources do not look like mysterious asset loss.
- This is explanation-only. It does not create entries, alter balances, change Firestore data, or repair old records.

## 2026-07-16 - Scrollable Headers and Compact Action Fix

- Dashboard quick actions were redesigned as compact minimal action surfaces after phone screenshots showed the row looked corrupted.
- Invest portfolio summary was reduced so the holdings list appears sooner and the screen does not feel oversized.
- Invest, Loans, Reports, and Expenses now keep page headers/hero sections inside their scroll content instead of pinning them under the app chrome.
- No accounting, Firestore, sync, account, loan, debt, investment, auth, or schema logic changed.
- Verification passed: prod-debug Kotlin compile and prod-debug unit tests.
## 2026-07-15 - Premium Motion and Loading UX Pass

- Added adaptive bottom navigation: main tab screens keep the full labeled floating bar at rest, compress to a shorter icon-focused bar while scrolling down, and expand again on upward scroll.
- Dashboard cloud-check/loading now uses skeleton blocks instead of a center spinner.
- No financial/accounting/sync logic changed.
- Verification passed: prod-debug Kotlin compile and prod-debug unit tests.

## 2026-07-15 - Behind-the-App Credibility Check

- User reported manual phone smoke as good; this pass checked backend/config/release safety behind the visible app.
- Passed: prod-debug Kotlin compile and prod-debug unit tests.
- Checked: developer tools are dev-debug gated, production network security disables cleartext, Android Auto Backup excludes Room/PIN/DataStore, app label remains Fynlo Ledger, and latest release artifacts exist.
- Checked: Firestore rules keep user data under `/users/{userId}`, require authenticated owner access for user collections, and deny all other unmatched paths.
- Secret-style scan found no actual committed secrets in app/docs/migration package.
- User confirmed the controlled cloud round-trip check was completed. Treat cloud/local parity as `PHONE VERIFIED BY USER` unless a later mismatch is reported.
- Limitation: lint and full release-readiness Gradle verification exceeded the local timeout window; rerun those with a longer timeout before the next Play upload.

# Fynlo Ledger Release Knowledge Hub - Internal Testing 3.2.106

## 2026-07-03 - Search Insets and Book Check Grouping

- Global Search now removes the duplicate local top inset and uses a compact 58dp search header so the search field sits closer to the status area instead of leaving a large blank gap above it.
- Book Check now groups unclear/stale interest payment review rows per borrower/debt instead of showing one card per old payment. This stays review-only: no balances, loan dates, payment rows, or cash/account totals are changed automatically.
- Added regression tests for grouped borrower/debt interest-payment reviews.
- Phone install/smoke is still pending because the phone is currently with the user.

## 2026-07-03 - Audit Matrix Status

- The user manually phone-verified the latest 17 local UI/accounting audit items in the developer app. Mark those local phone checks as `PHONE VERIFIED BY USER`.
- Cloud/local parity was later user-confirmed as completed after sign-in/sync testing. Treat it as `PHONE VERIFIED BY USER` unless a new mismatch is reported.
- Release AAB work is no longer gated by that cloud round-trip item, but still needs normal longer lint/release-readiness verification before Play upload.
- Profile & Security guest-mode sign-in was fixed: the screen now exposes an enabled `Sign in with Google` button, shows `Signing in...` / safe failure text / `Cloud backup active`, and starts cloud backup after successful auth. Developer app install/launch plus UI hierarchy confirmed the button is visible and tappable. Account selection and full cloud round-trip remain pending user-side confirmation.

## 2026-07-01 - Phone Visual Fixes After User Screenshots

Completed:

- Global Search top field no longer uses a too-short 58dp row; the search input now has enough vertical room so placeholder/query text is not clipped when the keyboard is open.
- Investment screen dark-mode values now use theme-aware colors instead of hardcoded light emerald colors, improving portfolio and holding readability.
- Loan/debt details and repayment dialogs now show interest reductions as `Reduced / waived: -amount`, making reduced amounts such as `INR 99` explicit.
- Installed `3.2.109` production debug and `3.2.109-dev` developer debug on the connected phone.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Developer app `app.fynlo.dev` installed on the connected phone as `3.2.109-dev` and launched successfully.
- `:app:installProdDebug` and `:app:installDevDebug` passed.

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

## Production wording and dev-only tools

Latest rule added 2026-06-29:

- Production users should see plain financial language, not implementation language. Prefer `Book check`, `Money action history`, `Google cloud backup`, `Profit & Loss`, and `error report`.
- Avoid visible production copy such as `Firestore`, `Crashlytics`, `schema`, `migration`, `journal_only`, `SYNC_PULL`, `developer`, or `QA`, unless the screen is explicitly dev-only.
- Developer/QA tools in Settings must remain dev-flavor only: `BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev"`. Do not expose these tools in `prodDebug`, because it confuses phone smoke testing and can make a production-flavored build look unfinished.
- Internal code comments can stay technical. The restriction is for user-visible app text, Play Store screenshots, and release-facing flows.

## Core accounting and money-action fixes

### 2026-06-30 structured sync conflict merge

The first sync conflict UI only recorded a review decision. The 2026-06-30 follow-up changed conflict capture for account and transaction rows to store structured JSON snapshots. `Keep phone` and `Keep cloud` now apply the selected snapshot locally, write an audit entry, and sync that selected row back to cloud.

Rules for future agents:

- Do not downgrade conflict snapshots back to `toString()` text.
- Keep conflict preview copy readable for normal users; hide internal ids/timestamps where possible.
- Only account and transaction conflicts currently have true structured apply support. Add other collections deliberately with model-specific repair/sync behavior.
- Mark reviewed remains a no-change acknowledgement.

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
- Removed developer-only publishing placeholders from `docs/terms.md`.
- Legal page factual pass completed for visible brand, package name, public URLs, privacy/data deletion links, Firebase/Google disclosures, and support contact consistency. Owner/lawyer review is still recommended before monetized production launch, especially subscription, refund, liability, and acceptance wording.

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

## 2026-06-29 - Account statement ordering and Family Cash investigation

- User reported confusion after opening account details/history: Family Cash had previously looked like Rs. 2,90,000, but production data now showed Rs. 1,64,500.
- Direct production-phone DB inspection explained the current stored balance: Family Cash opening audit amount was Rs. 1,16,15,000 and the ledger contained Rs. 1,14,50,500 of outgoing Family Cash transactions, leaving Rs. 1,64,500.
- No random balance rewrite was found in that inspection. The real product problem was traceability: same-day rows were only date-sorted, and legacy restored rows often had account names but blank account ids.
- Fix rule: account statements and global transaction history must order rows by business date, then transaction creation/update time, then id. Do not revert to date-only ordering.
- Account statements now show date/time and per-row balance impact, matching the transaction-history before/after trace. This is the first place to inspect when a tester asks "why did this account balance change?"
- Startup now backfills missing transaction `fromAcctId` / `toAcctId` values from current account names when safe. This is a metadata repair only: it must never move money or adjust balances.

## 2026-06-29 - Dashboard transfer discoverability

- User could not find account transfer from Dashboard because the visible quick actions only showed Expense, Income, Lend, and History.
- Dashboard quick actions now include `Transfer`.
- Transfer dialog supports both dashboard-started transfer, where source and destination are both chosen, and account-started transfer, where the source account stays preselected.
- Keep this visible entry point. Account transfer is a core accounting action and should not be hidden behind account edit flows.

## 2026-06-29 - Expenses add flow and loan grace-period rule

- Expenses `+` must open an expense-only dialog. It should not show Income there; income belongs on Dashboard or History-level transaction entry.
- The shared Add Transaction dialog now has a locked mode (`allowTypeSwitch = false`) for screen-specific flows.
- For lending grace periods, preserve accounting truth:
  - Extending a due date is a collection/planning change.
  - Waiving extra-days interest is a ledger adjustment.
  - Do not fake this as an interest payment, because that would overstate collected interest income.
- Future implementation recommendation: add an explicit `Waive interest` action for borrowers/debts that records a non-cash adjustment and subtracts it from interest outstanding while keeping `paidInterest` as real collected/paid interest only.

## 2026-06-29 - Interest waiver implemented

- The grace-period recommendation is now implemented with `interestWaived` on both borrower and debt rows.
- Waiver is non-cash: no account debit/credit, no payment row, no P&L income/expense, and audit `amountDelta = 0`.
- Outstanding math is now: principal outstanding plus `max(0, accrued interest - paid interest - waived interest)`.
- Firestore sync includes the waiver field and Room migration 27 -> 28 adds it locally with default `0.0`.
- Ledger Health should warn, not auto-repair, if a cloud/imported row has negative waived interest or more waived interest than unpaid interest.
- Keep this separation in future edits: payment means money moved; waiver means interest was forgiven.

## 2026-06-29 - Dashboard freshness and book confidence

- Dashboard freshness must represent latest money activity, not only last recalculation. The current rule takes the newest timestamp from account, transaction, borrower, debt, investment, and recalc data.
- Dashboard now surfaces a Book Check confidence card with score, cloud backup state, and last activity. This is a visibility layer only; it must not mutate balances.
- Add Transaction shows a non-blocking warning for likely duplicate rows: same date, same type, same category, same amount, and same source/destination account.
- Book Check issue rows now include plain-language fix suggestions. Keep this pattern for future ledger warnings: every warning should say where the user can fix it.
- If future agents add true monthly close, undo windows, or recurring reminders, those need separate data-model work and tests. Do not fake them as UI-only states.

## 2026-06-29 - Phase 2 roadmap memory

The user wants the technical/accountability roadmap and UI/UX roadmap done later as a combined Phase 2. Treat this section as pending product direction, not completed release notes.

Technical/accountability items:

1. Monthly close / lock period.
2. Undo window.
3. What's New / How to Use.
4. Advanced category rules.
5. Ledger timeline with before/after movement.
6. Monthly review screen.
7. Smart mismatch fixer.
8. Backup health center.
9. Export preview.
10. Recurring reminders.
11. Contact ledger.
12. Attachments.
13. Balance reconciliation wizard.
14. Role/privacy/reviewer mode.
15. Bank import assistant.
16. Personal finance insights.
17. Goal-based planning.
18. Loan grace/waiver history improvements.
19. Offline sync conflict resolver.
20. Dev-only release checklist screen.

UI/UX items:

1. Unified money-dialog shell.
2. Searchable dropdowns instead of crowded chips.
3. Separate Expense and Income dialog experiences.
4. Dedicated account-transfer dialog.
5. Clearer payment dialogs.
6. Standard confirmation pattern.
7. Card density standardisation.
8. Better empty states.
9. Guided Book Check.
10. Consistent icons.
11. Better validation text.
12. Better search and keyboard layout.
13. Reports export polish.
14. Settings regrouping.
15. Micro-feedback everywhere.

Dashboard Book Check decision:

- Keep one compact persistent confidence card.
- Do not show a second automatic top dialog for ordinary warnings; it duplicates the card and makes the dashboard feel noisy.
- Use a prominent nudge/card for serious issues and open the full Book Check only when the user taps it.

## 2026-06-29 - Phase 2 safe UI foundation pass

Completed safely while the phone was away:

- Added shared `FynloChoiceDropdown`.
- Add Transaction category/source selection now uses dropdowns instead of crowded chips.
- Budget and Recurring category selection now use dropdowns instead of chips.
- Added Settings -> App Info -> `What's new & how to use`.
- Export Data now uses the shared bottom-sheet form shell and shows a preview summary before Android's save-file screen.
- Added a dev-flavor-only Settings -> Developer -> `Release Checklist` dialog for future pre-AAB passes.
- Production debug compile passed after the changes.

Do not mark the full Phase 2 roadmap complete yet. The remaining accountability items need deeper data-model work and phone smoke:

- Monthly close / lock period.
- Undo window.
- Fully automatic smart mismatch fixer.
- Attachment/proof support.
- Balance reconciliation wizard.
- Reviewer/privacy mode.
- Offline sync conflict resolver.
- Any automatic repair that would mutate money rows.

## 2026-06-30 - Balance-safe edit preservation and install

Completed:

- Loan, debt, and investment edit dialogs now preserve the full existing record and update only the edited fields. This prevents edits from wiping hidden accounting data such as paid principal, paid interest, waived interest, default/frozen-interest state, realized/withdrawn investment values, timestamps, and trace fields.
- Investment source edits still go through the repository funding-source update paths, so account-source changes can reverse the old source movement and apply the new source movement.
- Production dashboard Transfer action is visible again after install.
- Expenses screen plus button opens an expense-only add dialog; income is no longer offered from that screen.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Loan/debt statement date visibility

Completed:

- Borrower detail now shows `Loan date` and `Due date` in the top statement area.
- Debt detail now shows `Taken date` and `Due date` in the top statement area.
- If a due date is blank, the detail screen says `No due date` instead of hiding the row.
- Confirmed product interpretation: Invest summary uses the shared soft ledger panel; data is local-first on the phone and cloud-synced to Firestore only when signed in.

Manual smoke when phone reconnects:

1. Open one borrower with a due date. Confirm Loan date and Due date are visible near the balance.
2. Open one borrower without a due date. Confirm Due date says `No due date`.
3. Repeat both checks for one debt/debtor.
4. Open Invest and confirm the portfolio summary card still feels like the soft ledger-panel style.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Installed production and developer debug variants to connected phone `3C15CA0055F00000`.
- `:app:testProdDebugUnitTest` passed.
- `:app:compileDevDebugKotlin` passed.
- Installed `app.fynlo` and `app.fynlo.dev` to connected phone `3C15CA0055F00000`.
- Installed versions: production `3.2.107` / versionCode `231`; developer `3.2.107-dev` / versionCode `231`.
- ADB UI smoke confirmed production Dashboard has `Transfer`, Dashboard freshness is current, Expenses opens `Add Expense`, and dev intro opens as `Fynlo Ledger`.

## 2026-06-30 - Dashboard account density and ledger trail clarity

Completed:

- Dashboard account rows were tightened so the account section consumes less vertical space without making balances hard to read.
- Removed the visible pencil beside account balances. Tap opens the statement; long-press keeps account editing reachable without cluttering the balance line.
- Transaction history and account statement rows now use clearer movement language: `Paid from`, `Received into`, `Lent from`, `Funded from`, and `From -> To`.
- Running balance language now says `Balance after`, making each row easier to audit.

Manual smoke when phone reconnects:

1. On Dashboard, tap an account row and confirm it opens the account statement.
2. Long-press a dashboard account row and confirm account edit is still reachable.
3. Open account statement/history and confirm rows show clear source/destination language.
4. Confirm running balance rows read `Before`, `Change`, and `Balance after`.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Global Search keyboard and top spacing polish

Completed:

- Global Search custom top bar was reduced from the overly tall spacing that matched the old EMI Calculator problem.
- Empty and no-result states now sit closer to the search controls instead of leaving a large blank top area.
- The screen uses keyboard-aware scroll/padding, and the keyboard search action clears focus so results are easier to inspect.

Manual smoke when phone reconnects:

1. Open Global Search and confirm the top area no longer feels overly airy.
2. Tap the search box, type two characters, and press the keyboard search/check action. Confirm the keyboard dismisses.
3. Search a loan/debt/transaction and confirm results remain visible above the keyboard.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Book Check safe repair action

Completed:

- Settings -> Book check now includes `Run safe repair`.
- The action exposes the same conservative cleanup paths that already run at launch, then recalculates balances and shows a clear result message.
- Safe repair can clean deleted-row residue, debt-funded investment journal traces, debt receipt amount mismatches, missing transaction account ids, account balance drift, and paid-total drift.
- It intentionally does not create guess-based transactions. If Book Check still reports a semantic issue after this action, the user should review the source loan/debt/investment/account record manually.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Monthly close / undo / proof / conflict hardening

Completed:

- Added schema v29 safety tables for monthly closes, undo actions, proof attachments, and sync conflicts.
- Added Settings controls for current-month close/reopen, undo last money action, sync conflict review, and proof record visibility.
- Monthly close blocks dated money changes inside locked months across transactions, loans, debts, investments, withdrawals, and interest waivers.
- Undo currently covers recent transaction add/edit/delete actions within 10 minutes; do not describe it as a full undo for compound loan/debt/investment flows until that is explicitly added.
- Proof attachments are stored, backed up/restored, and synced as metadata. Per-record file picker UI is not yet complete.
- Offline conflict capture records account/transaction conflicts before cloud overwrite; Settings lets the user review and mark them.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Compound undo and guided accountability pass

Completed:

- Undo now covers compound money actions, not just simple transactions. Bundles preserve loan/debt/investment records, linked transactions, linked payments, and touched accounts so undo can restore the ledger shape instead of only deleting a visible row.
- Balance replay helpers skip `journal_only` rows. This is mandatory because split rows such as interest/principal audit lines must not move account balances twice.
- Investment-only delete undo is special: it restores the holding without replaying the old funding balance because `deleteInvestmentOnly` does not reverse cash at delete time.
- Added proof attachment UI on loan, debt, and investment detail surfaces. Proof rows store local URI metadata; they are not cloud file uploads.
- Sync conflict review is now readable and decision-oriented, with phone/cloud comparison and reviewed-resolution actions. Current snapshots are text summaries, so this is not yet a true structured merge engine.
- Monthly close dialog now shows month totals before close/reopen.
- Book Check now provides a guided review path after safe repair.
- Audit trail rows expose before/after/reason fields in the app.
- Fresh empty books now show a Dashboard first-run checklist.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Installed production and developer debug variants to connected phone `3C15CA0055F00000`.

Rules for future agents:

- Do not bypass repository money-action methods for loans, debts, investments, repayments, withdrawals, transfers, or waivers. Those methods now create the trace and undo state.
- Do not replay `journal_only` rows into account balances.
- Do not claim sync conflict `Keep phone/cloud` is a real data merge until conflict rows store structured record snapshots.
- Treat binary proof upload as future scope. Current proof attachments are metadata links.

## 2026-06-30 - Lower-risk dialog cleanup

Completed:

- Converted lower-risk legacy dialogs to the premium confirmation/form sheet direction across navigation review prompts, contacts, recurring entries, profile security/account deletion, projects, customer reminder, and Settings review/repair/export surfaces.
- Settings -> Book check, Sync conflict review, Monthly close, Audit trail, and Restore backup preview now use the shared `FormDialog` shell.
- Settings destructive confirms now use `FynloConfirmDialog`.
- The only remaining `AlertDialog` is the non-dismissible reset progress indicator during active data wipe/restart. Keep it modal unless the reset flow itself changes.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Installed production and developer debug variants to the connected phone.

## 2026-06-30 - Room v29 launch crash fix

Completed:

- Fixed the app-closing-on-launch issue caused by Room schema validation for v29 safety tables.
- `MIGRATION_28_29` created four indexes, but the Room entity annotations did not list them. Room rejected the database at startup with a `Migration didn't properly handle: monthly_closes` error.
- Added matching index declarations to `MonthlyClose`, `UndoAction`, `ProofAttachment`, and `SyncConflict`.
- Regenerated Room schema `29.json`.
- Added Android migration test coverage for v28 -> v29 and extended the full migration reopen test through v29.

Future rule:

- If a migration creates an index, the matching Room entity must declare the same index name and columns. Do not rely on SQL-only indexes; Room validates the entity schema on launch.

## 2026-06-30 - Account balance edit correction

Completed:

- Investigated Kalyani's negative cash case on the production phone database. The cash add was an older direct account balance edit, while the loan to Muhammed was a real ledger transaction. Safe repair replayed only ledger rows, so it kept the loan deduction and dropped the direct edit effect.
- Dashboard account edits now route balance changes through `quickEditBalance`, creating a visible `Balance Correction` transaction.
- Balance corrections now carry the account id in addition to the account name.
- Account statement quick-balance edit also passes the account id.
- Safe repair now recognizes legacy `Account edited:` audit deltas so older direct balance edits can be preserved during balance replay.

Future rule:

- Never save an existing account balance as a plain account update. Any balance change must create a ledger-visible correction transaction, otherwise Book Check/repair will eventually expose the mismatch.

## 2026-06-30 - Phase status clarification

Not remaining blockers:

- Contact Book base work is present: add/edit/delete, import from system contacts, search, contact IDs, empty states, loan/debt linking, and feedback messages. A future per-contact money timeline can be added later, but the base contact book is not unfinished Phase 2 work.
- History/accountability surfaces are present: Money action history, transaction/account history ordering, before/after balance impact, audit details, and CSV export paths.
- Investment valuation history is already on the premium dialog style and has improved contrast.
- The old `AlertDialog` remaining in Settings is intentional: the non-dismissible `Resetting...` progress lock while Reset All Data wipes local/cloud state and restarts the app. Keep this unless the reset flow itself is redesigned.

## 2026-06-30 - Contact money history and reset progress polish

Completed:

- Contact Book now has a per-person money history sheet opened by tapping a contact row or choosing `Money history` from the row menu.
- The sheet summarizes money lent to that person, money owed to that person, current receivable, and current payable.
- The timeline includes linked loans, debts, loan repayments, and debt payments. Matching uses `peopleId`, then phone digits, then exact name fallback for older records.
- Reset All Data progress now uses the premium rounded dialog style while staying non-dismissible during the active wipe/restart.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Dashboard accountability guidance

Completed:

- Dashboard now explains today's net-worth movement in layman terms using income minus expense, while calling out transfers separately so account-to-account movement is not mistaken for income or expense.
- Dashboard now has a monthly review card with income, expense, net flow, and entry count. It opens Monthly Summary for the deeper view.
- Smart duplicate warning is present in Add Transaction for same date/type/category/amount/account entries.
- First-use Dashboard guidance remains the setup path for fresh books.
- Sync conflict review already compares phone/cloud snapshots and lets the user choose the final source for structured account/transaction conflicts.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Automation and daily-use improvements

Completed:

- Dashboard now has an automation review card for recurring entries due now, budget alerts, and large entries that still need proof review.
- Add Transaction now suggests a category from description/history patterns when the user leaves the category blank.
- Add Transaction now shows a large-entry proof reminder for rows of `₹50,000` or more.
- Add Transaction feedback now points users to the Undo path in Settings.
- The broader 15-item user request is represented by existing and new surfaces: reminders, recurring entries, duplicate prevention, reconciliation/Book Check, auto category suggestion, contact history, one-tap payments/waivers, daily/monthly summary, risk alerts, proof prompts, smart search, undo safety, cloud status, and reset safety.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Running balance timelines

Completed:

- Account statements now show the selected account's own running balance impact for each row.
- Single-account transaction impacts now render as a visible `Before / Change / After` strip instead of a small inline trace.
- This makes balance movement explainable without changing ledger data.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Proof vault and smart Book Check explanations

Completed:

- Settings -> Proof records now opens a Proof vault review dialog.
- The dialog shows saved proof links and high-value proof gaps across loans, debts, investments, and large transactions.
- Proof vault does not mutate ledger data. It points the user back to the relevant detail screen to attach or replace proof.
- Book Check issue rows now include a plain-language `Why it matters` explanation plus the existing practical next step.
- Book Check now includes repair coverage guidance: safe repair can rebuild evidence-backed links/totals, but it must not guess missing accounts, delete duplicates, or decide which record is correct.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Launch readiness assistant

Completed:

- Settings -> Backup & Export now has a `Launch readiness` review tool.
- It summarizes personal ledger treatment, close readiness, smart alerts, clean onboarding, Reports 2.0, CSV import automation, backup confidence, security polish, business mode, plain-language explanations, and release quality.
- Previous-month close is handled as a deliberate assistant action, not a silent automatic background lock. The close button is enabled only when no serious Book check issue is present and the previous month is not already closed.
- This is the release-facing checklist to run before Play internal testing promotion together with compile, unit tests, phone smoke, Book check, Proof vault, backup/export, and Play Console review.
- To reduce Settings crowding, file actions remain under `Backup & Export`, while ledger trust actions now live under `Trust & Safety`.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - Technical hardening cleanup

Completed:

- Retired stale NetWorthWidget TODO references from the active project state. The widget was removed in 3.2.79, so there is no live widget currency bug to fix.
- Removed the throwing `allSnapshots` accessor from `AccountRepository`; project-scoped `getNetWorthSnapshots(pid)` remains the supported path.
- Replaced the throwing `allValuations` accessor in `InvestmentRepository` with the real DAO flow.
- Aligned Terms contact, dispute, notice, and tax-invoice email references to `fynloapp.support@gmail.com`, matching the public Privacy Policy/support contact.
- Enabled release/benchmark `ndk.debugSymbolLevel = SYMBOL_TABLE` so future release builds attempt native-symbol packaging.

Native symbol note:

- `:app:bundleProdRelease`, `:app:extractProdReleaseNativeDebugMetadata`, and `:app:extractProdReleaseNativeSymbolTables` pass. The generated native-symbol metadata folders are empty because the packaged native libraries are third-party dependency libraries without local debug metadata. Play's native-symbol warning can remain informational unless a future release adds first-party NDK code or a dependency ships symbols.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- `:app:bundleProdRelease` passed.

## 2026-06-30 - Dashboard and Investment screen de-clutter

Completed:

- Dashboard now keeps the first screen focused on total net worth, quick actions, and accounts. Book Check confidence is still visible when a fresh book needs guidance or when warnings/critical issues exist, but the healthy-state card no longer takes prime space.
- Automation review now appears only when there is something actionable: due recurring entries, budget alerts, or large rows without proof.
- Daily/monthly movement explanations are grouped under `Money movement` lower on the dashboard and only render when there is actual activity.
- Investment allocation focuses on the top three visible types and explains when smaller types are grouped into the portfolio total.
- Investment holding cards are calmer: current value, invested amount, gain/loss, source trace, and Update/Withdraw remain immediately visible; CAGR, withdrawals, notes, and proof attachments are under `Details and proof`.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed before the final spacing-only cleanup; compile passed again after that cleanup.

## 2026-06-30 - Dashboard proof reminder and Invest density follow-up

Completed:

- Dashboard proof reminders now use a recent-action window: high-value transaction proof gaps from the last 45 days only. Older proof gaps are still visible in Settings -> Proof records, but they are not treated as urgent dashboard work.
- Proof records copy now clarifies that older proof gaps are review prompts, not balance errors.
- Dashboard money movement now renders as a compact Today/Month strip instead of two stacked explanatory cards, reducing first-screen height.
- Investment holding cards were compressed again: smaller icon, single-line type/date metadata, direct value/gain row, one-line funding trace, and a shorter `Details` expander.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Dashboard movement wrap fix and investment ledger rows

Completed:

- Fixed the Money movement count wrap issue by using the section-title count slot instead of a separate squeezed row item.
- Month movement now shows both inflow and outflow in the card detail so the net number has context.
- Invest now uses a compact portfolio summary instead of the tall hero panel.
- Holding rows no longer show large repeated Update/Withdraw buttons. Those actions moved into the holding menu with Details & proof, Valuation History, Edit, and Delete.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Prod and dev debug variants were installed to the connected phone.

## 2026-06-30 - Interest-only payment period rollover

Completed:

- Borrower and debt interest-only payments now automatically restart the interest period from the next day when the paid interest fully settles the amount due through the payment date.
- The rule is deliberately narrow: no principal component, full interest due covered, active/non-cleared record, and valid ledger date.
- The payment and transaction rows remain as history; only the borrower/debt interest period state advances.
- Rebuild/repair now counts interest payments only from the current interest period, while principal payments continue to count across the whole record lifetime.
- Undo for the newly-created payment restores the original borrower/debt snapshot, including the previous interest start date.

Manual smoke when phone reconnects:

1. Create or pick an active borrower with interest due.
2. Collect `Interest Only` for the full interest due today. Confirm the borrower date moves to tomorrow and current interest becomes zero.
3. Repeat with a partial interest amount. Confirm the borrower date does not move.
4. Repeat the full-interest flow for a debt payment. Confirm the debtor/debt date moves to tomorrow and current interest becomes zero.
5. Check account balance, transaction history, and audit trail for the payment row.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Phone install pending because the phone was disconnected.

## 2026-06-30 - Optional due-date clear and help entry placement

Completed:

- Optional date fields now expose a clear action once a date is selected.
- Loan and debt forms can now remove an accidentally entered due date while keeping the start/taken date.
- `What's new & how to use` moved to a top-level Settings help row for easier discovery.
- The drawer/hamburger menu was intentionally not expanded with this help item; Settings is the cleaner reachable home for support content.

Manual smoke when phone reconnects:

1. Open Add Loan, pick a Due date, then tap the clear icon. Confirm the due date becomes blank and the loan can save with only the Lending date.
2. Repeat in Add Debt for Due Date.
3. Open Settings and confirm `What's new & how to use` is visible near the top without opening App Info.

Verification:

- `:app:compileProdDebugKotlin` passed.

## 2026-06-30 - EMI calculator keyboard/layout polish

Completed:

- EMI Calculator now has keyboard-aware scrolling so the numeric keyboard does not trap the result area as badly.
- Tenure's keyboard check action dismisses focus/keyboard.
- A compact Monthly EMI preview appears inside the form as soon as the principal, rate, and tenure are valid, keeping the main answer visible while typing.
- The content now has slightly better top spacing below the shared app bar.

Manual smoke when phone reconnects:

1. Open EMI Calculator from the menu.
2. Enter principal, annual rate, and tenure. Confirm the compact EMI preview appears before closing the keyboard.
3. Tap the keyboard check button while Tenure is focused. Confirm the keyboard closes.
4. Confirm the full result cards and amortization section are readable after the keyboard closes.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Phone install pending because the phone was disconnected.

## 2026-06-30 - Dashboard account density and ledger trail clarity

Completed:

- Dashboard account rows were tightened with smaller icons, reduced vertical padding, and no visible pencil competing with the balance.
- Tapping an account row opens the account statement; long-press keeps account editing reachable without cluttering the row.
- Transaction history and account statement rows now describe money movement in plain ledger language such as `Paid from`, `Received into`, `Lent from`, `Funded from`, and `From -> To`.
- Running-balance copy now says `Balance after` instead of the vague `After`.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Global Search keyboard and top spacing polish

Completed:

- Global Search now uses a shorter custom top bar and less empty-state top padding.
- The screen is keyboard-aware, so the keyboard no longer leaves the search experience feeling like the EMI calculator did before its polish pass.
- Pressing the keyboard search action clears focus so results are easier to read.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-06-30 - Internal testing build 3.2.108

Completed:

- Version bumped to `3.2.108` with `versionCode 232`; developer builds report `3.2.108-dev`.
- Production and developer debug variants were installed to the connected phone.
- Installed package metadata confirmed `app.fynlo` is `3.2.108` and `app.fynlo.dev` is `3.2.108-dev`.
- Production release AAB rebuilt at `app/build/outputs/bundle/prodRelease/app-prod-release.aab`.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- `:app:bundleProdRelease` passed.
- `:app:installProdDebug` and `:app:installDevDebug` passed.
- Unit test result XML showed 413 tests with 0 failures/errors.

## Next session release follow-ups

Carry these forward before the next internal-testing/AAB update:

1. Play Console warning: the AAB still shows `This App Bundle contains native code, and you've not uploaded debug symbols`. This is not blocking the internal test upload, but it should be investigated so crashes/ANRs are easier to analyze.
2. Dark mode is currently a failed experience. Several screens have text and number contrast problems. Audit all main screens, submenu screens, dialogs, statements, search, reports, and calculators in dark mode.
3. Global Search still shows the top gap on device. The previous keyboard/top-spacing polish was not enough; inspect the live screen and fix the layout/insets at the root.

## 2026-07-01 - Release Follow-Up Fixes

Completed:

- Added a borrower/debt `Stop interest after due date` switch for grace cases where interest must stop at the due date.
- Centralized saved loan/debt interest calculations behind `InterestPolicy`, then routed summaries, payments, waivers, ledger health, payoff planning, calendar, people, and detail screens through it.
- Added Room migration 29 -> 30 and a schema export for the new fields.
- Improved dark-mode base contrast and fixed the Global Search top-bar inset/height issue.
- Rebuilt the production release AAB.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- `:app:bundleProdRelease` passed.

Remaining note:

- Play Console's native-symbol warning is still non-blocking. The release build emits the AAB and mapping files, but no native debug-symbol zip is currently produced under `app/build/outputs`.

## 2026-07-01 - Book Check Interest-Only Rollover Correction

Completed:

- Book Check now mirrors the payment rebuild rule after an interest-only rollover: principal is lifetime, interest is current-period.
- This prevents old settled interest-only rows from reappearing as false `Loan payment total mismatch` or `Debt payment total mismatch` issues after the loan/debt date rolls to the next interest period.
- Connected-phone database inspection showed the reported four checks came from Samanvi Travels and Muhammed interest-only receipts dated `2026-06-30`; each loan had correctly rolled its interest start to `2026-07-01`.
- Connected-phone database inspection also explained Venkat Reddy Vzm Line's visible `₹17,901` interest: the stored accrued interest is `₹18,000`, with `₹99` waived interest.

Verification:

- Focused `InterestPolicyTest` and `LedgerAccountabilityTest` passed.
- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-07-02 - Loan date correction and interest accrual

Completed:

- Investigated Samanvi Travels in the connected production app database.
- Confirmed borrower loan records use one `date` field for both the visible loan date and the current interest accrual start; there is no separate stored interest schedule/date field.
- Found the visible dates and linked Lending transaction dates were already corrected to `2026-02-01` and `2026-04-22`.
- Found interest-only payments on `2026-06-30` were already recorded for those loans. Because paid interest was ahead of interest accrued from the corrected dates, current interest can appear as zero until accrual catches up.
- Hardened borrower and debt edit flows so edited records immediately rebuild `paid`, `paidPrincipal`, and `paidInterest` from payment rows before sync. This prevents stale form/summary values from surviving after loan/debt date corrections.
- Book Check now warns when paid interest is ahead of accrued interest, so this state is visible and explainable rather than silent.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-07-02 - Paid-ahead interest phone check

Completed:

- Installed the fixed production debug build on the connected phone. No AAB/release build was made in this pass.
- Verified Samanvi Travels on-device state: the current first loan row is `Rs.600,000` from `2026-02-01`, not `Rs.1,000,000`.
- Verified the `Rs.600,000` loan has one interest-only payment of `Rs.216,892`; accrued interest as of `2026-07-02` is about `Rs.59,573`, leaving advance interest of about `Rs.157,319`.
- Verified the `Rs.125,500` loan from `2026-04-22` has one interest-only payment of `Rs.39,492`; accrued interest as of `2026-07-02` is about `Rs.5,859`, leaving advance interest of about `Rs.33,633`.
- Added borrower and debt detail UI clarity for this state: `Interest paid ahead`, advance amount, accrued interest, collected/paid interest, and plain text explaining why interest due is zero.
- Phone UI tree confirmed the Samanvi borrower detail shows the new explanation and corrected `Loan date`.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- `:app:installProdDebug` passed.

## 2026-07-02 - Book Check interest review cleanup

Completed:

- Book Check interest-period warnings now include the affected borrower/debt, payment amount/date, and principal amount.
- The warning copy now tells testers to assign unclear historical interest as previous period, current period, advance interest, or extra/manual.
- Borrower and debt detail screens now avoid repeating `Interest due` inside the interest breakdown card; the card focuses on calculated interest, collected/paid interest, advance interest, and historical-review notes.
- Dashboard Book Check appears only in the main confidence card, not again as a secondary nudge.
- Startup sync wording now uses `Checking cloud backup...` while cloud state is still being verified.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-07-02 - Developer app balance clarity and exact-date EMI pass

Completed:

- Borrower detail now says `Total Receivable`; debt detail now says `Total Payable`.
- Detail rows use clearer terms: `Principal Outstanding`, `Interest Due`, and `Interest Payable`.
- Loan overview top metrics were reduced to count, principal, and interest so large values do not clip. Paid-ahead interest remains in detail/breakdown contexts.
- Book Check now emits a warning-level `Interest period may be complete` item when an old due period looks fully paid but dates were not advanced.
- Borrower and debt payment dialogs now warn when a payment may complete an interest period and explain that the due date is treated as the next period start.
- Settings personalization pre-fills the saved name and disables the save action when the name is already saved.
- EMI Calculator now has `Duration` and `Exact dates` tenure modes. Simple interest uses exact days; EMI methods explain the converted billing months.
- Installed only the developer debug app for phone verification. No production install and no release AAB.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.
- Focused `InterestPolicyTest`, `LedgerAccountabilityTest`, and `MoneyActionIdempotencyDataIntegrityTest` passed.
- `:app:installDevDebug` passed.
- Developer app launch sanity passed.

## 2026-07-03 - Minimal loan/debt statement UX

Completed:

- Borrower and debt detail screens were cleaned so the top summary stays focused on what a normal user asks first: principal pending, interest due/payable, total receivable/payable, account used, and dates.
- Paid principal and detailed interest/payment classification now live in Payment History instead of the main summary.
- Payment History rows now use plain labels: principal collected/paid, interest collected/paid, old interest history, interest paid ahead, extra interest, and interest needs review.
- Book Check paid-ahead items now read as review-level user explanations: `Extra interest already collected` and `Extra interest already paid`.
- Payment dialog helper text now says older interest, this loan/debt period, paid in advance, or extra note instead of technical allocation language.
- Developer app was installed and launched for sanity. Production app and release AAB were not touched in this pass.

Verification:

- `:app:compileProdDebugKotlin` passed.
- Focused `LedgerAccountabilityTest`, `InterestPolicyTest`, and `InterestEngineTest` passed.
- Full `:app:testProdDebugUnitTest` passed.
- `:app:installDevDebug` passed.
- Developer app launch sanity passed.

## 2026-07-03 - Developer app routing and keyboard polish

Completed:

- Dashboard Book Check now navigates to a dedicated Book Check route, opening Trust & Safety and the Book Check review list directly.
- Global Search no longer opens the keyboard automatically on screen entry. The top search bar is shorter to reduce the phone top-gap issue.
- EMI Calculator now clears focus/hides keyboard on entry and on user scrolls, and exact-date helper text is informational rather than warning-like.
- Interest payment dialogs now use plainer review copy for payments that fully cover interest, pay ahead, or belong to older interest.
- Confirmed Firestore sync already includes transfer account IDs and interest period fields; no migration was added for this pass.

Verification:

- `:app:compileProdDebugKotlin` passed.
- Focused `InterestPolicyTest`, `LedgerAccountabilityTest`, `MoneyActionIdempotencyDataIntegrityTest`, and `SyncLogicTest` passed.
- Full `:app:testProdDebugUnitTest` passed.
- `:app:installDevDebug` passed.
- Developer app launch sanity passed.

## 2026-07-03 - Profile/Security sign-in release gate

Completed:

- Profile/Security sign-in entry point was inspected after the user reported `Google Sign-In setup is missing for this build`.
- Local Google services config contains entries for both `app.fynlo` and `app.fynlo.dev`, including the generated web client id used for ID-token sign-in.
- Local signing report confirms prod/dev debug and release SHA-1 fingerprints are represented in the checked-in Google services JSON.
- Added `FynloAuth` warning logs around Profile/Security sign-in failures so phone testing can identify Google status code 10/setup rejection, missing token, cancellation, or Firebase sign-in failure.
- Phone reproduction on `app.fynlo.dev` confirmed the Profile/Security button is wired and launches Google Play Services, then returns `ApiException status=10`.
- Root cause for the developer app: `app/src/dev/google-services.json` is a placeholder/copy. It declares `app.fynlo.dev`, but the Android OAuth client IDs and Firebase app id still match production. Google rejects this as an invalid dev app identity.
- User-facing copy now says the developer Google setup is not configured yet instead of leaving a generic setup-missing message.

Next release-gate action:

- To make the developer app sign in, create/register Firebase Android app `app.fynlo.dev`, add the local debug SHA-1/SHA-256, download the fresh `google-services.json`, and replace `app/src/dev/google-services.json`.
- Production sign-in should be verified separately on `app.fynlo`, especially for Play internal testing with the Play App Signing SHA-1/SHA-256.

Verification:

- `:app:compileDevDebugKotlin` passed.
- `:app:installDevDebug` passed.

## 2026-07-03 - Minimal Book Check visibility pass

Completed:

- Dashboard shows Book Check only for critical issues, so review-level old-payment warnings no longer crowd the daily home screen.
- Settings now groups this under `Review & safety` and keeps Book Check available for deliberate review.
- Book Check dialog keeps normal users on clear actions: what happened, why it matters, and what to do next.
- Advanced repair coverage and money-path trails are hidden unless internal/developer tools are visible.
- Visible Book Check rows translate internal terms into plain language such as money path, saved record, and account needs review.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-07-03 - Account paise display pass

Completed:

- Account-facing balances now use exact two-decimal display through `CurrencyFormatter.exact`.
- Updated dashboard account rows, account transfer pickers, transaction-history balance before/after rows, and loan/debt/payment/investment account pickers.
- High-level hero totals remain compact to keep the app minimal.
- Added formatter tests for exact paise and negative exact paise.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

## 2026-07-03 - Calculation, Account, and Organising Audit

- Audited core money paths for account mutation, transfers, lending/debt interest, investment funding traces, account statement ordering, and generated journal-entry filtering.
- Confirmed account transfers remain net-worth neutral: one account is reduced and the other receives the same amount through the transaction balance engine.
- Confirmed account statements/history use transaction date plus created/updated time ordering, so newer money movements appear first.
- Fixed summary lending profit so it uses actual interest portions from payment rows instead of summing the full `Loan Repayment` transaction amount, which can include principal.
- Fixed hand-loan breakdown consistency so hand-loan rows use total paid amount, matching the hand-loan total.
- Standardized generated journal-entry exclusion in dashboard automation, financial summary, reports, P&L, and monthly summary using the shared journal-entry detector instead of exact tag matching.
- Data-integrity touch: calculation/read model only. No schema migration and no account balance mutation.
- Verification: `:app:compileProdDebugKotlin`, focused accounting tests, and full `:app:testProdDebugUnitTest` passed.

## 2026-07-03 - Minimal UI/accountability cleanup gate

Completed:

- Removed the global content inset causing extra top space around Global Search.
- Kept Global Search keyboard closed until the user taps into search.
- Book Check review rows now show plain user language plus an `Open related record` action when navigation is safe.
- Account-facing balances preserve paise in account statements, account pickers, investment funding pickers, and reconciliation views.
- Shared card borders now use theme-aware outlines for better dark-mode readability.
- Generated journal-entry filtering now uses the shared detector across proof/reminder counts and report summaries.
- Cleaned remaining source mojibake artifacts and rechecked route/query strings plus Play Store URLs after the cleanup.

Verification:

- `:app:compileProdDebugKotlin` passed.
- `:app:testProdDebugUnitTest` passed.

Phone smoke still recommended before a new AAB:

- Open Global Search and confirm the top gap is gone and keyboard opens only after tapping search.
- Open Book Check and tap an actionable review row; confirm it opens the related loan/debt/history area.
- Check dark mode on Invest, Dashboard, Settings, and Book Check for readable numbers and borders.
- Open account statement/account pickers and confirm paise display is visible where needed.

### 2026-07-03 - Save feedback and confirmation consistency
- Product rule: normal money entry stays fast. Required fields keep the primary button pale/disabled until valid, with a short hint below the button.
- Successful adds, edits, payments, waivers, transfers, and account changes now give a short snackbar-style confirmation instead of silently closing.
- Risky account actions now ask clearly before continuing: close account, delete account, and balance edits that create a correction entry.
- Confirmations use plain user language: what will happen, why it matters, and the exact action button.

### 2026-07-03 - Loan/debt detail clarity
- Borrower and debt detail screens now always show the interest rate and interest method next to the account/date details, including no-interest records.
- Cleaned remaining old delete confirmation wording so risky actions read as plain questions instead of rough placeholder text.

### 2026-07-03 - Minimalism and consistency pass
- Added shared form action buttons so money entry forms use the same enabled, disabled, and destructive-action treatment.
- Standardized high-traffic loan, debt, income, expense, repayment, and waiver action text toward short sentence-case labels.
- Normal payments now use the standard green action style; red remains reserved for destructive or warning actions.
- Cleaned visible Book Check severity copy from technical severity wording toward user-facing repair language.
- Scope: UI/UX consistency only. No calculation logic, schema migration, account balance mutation, or sync behavior change.

### 2026-07-04 - Mostly-done minimalism cleanup
- Shared form dialogs no longer show the generic "Review the details before saving" subtitle unless a screen supplies useful copy.
- Shared form primary actions and template primary buttons now use the same pale disabled state before inputs are valid, then switch to the strong action color when ready.
- Cloud backup wording in Settings was simplified to user language: Google backup, online sync, and replacing backup from this phone.
- Scope: UI wording and shared action styling only. No calculation logic, account balance mutation, database migration, or sync engine change.

### 2026-07-10 - Cloud sync startup feedback and shared-phone guard
- Startup signed-in flow now gives brief cloud backup feedback: checking, synced, offline, or needs attention.
- Google sign-in from first launch and Profile & Security now checks for existing local records before enabling cloud backup.
- If the phone already has local ledger records, users must confirm that the selected Google account owns this phone's data before those local records are backed up.
- This reduces shared-device mistakes. It does not change Firestore ownership: records still live under `users/{uid}/...`, and rules remain owner-only.
- Scope: auth/sync safety UX only. No calculation logic, account balance mutation, migration, or release build.
- Verification: `:app:compileProdDebugKotlin` and `:app:testProdDebugUnitTest` passed.

### 2026-07-15 - Google sign-in deprecation cleanup and Play bundle
- Modernized Google sign-in from the deprecated intent API to Android Credential Manager with Google ID tokens.
- First-launch sign-in and Profile & Security sign-in now use the same helper, loading state, local-data confirmation guard, and plain failure copy.
- Removed the direct `play-services-auth` app dependency; keep `androidx.credentials-play-services-auth` because it is the Credential Manager bridge.
- Play upload candidate: `versionName 3.2.113`, `versionCode 237`.
- Fresh AAB: `app/build/outputs/bundle/prodRelease/app-prod-release.aab`.
- Installed on connected phone: `app.fynlo` = `3.2.113`, `app.fynlo.dev` = `3.2.113-dev`.
- Verification: `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, `:app:installProdDebug`, `:app:installDevDebug`, launch sanity for both packages, and `:app:bundleProdRelease` passed.

### 2026-07-15 - Warning cleanup queue for next Play pass
- Theme warning: only known remaining `DEPRECATION` text is the compatibility suppression in `Theme.kt`. Review and replace only if edge-to-edge/status-bar behavior remains stable on phone.
- Native debug-symbol warning: Play says the AAB contains native code without uploaded symbols. Release builds already request `SYMBOL_TABLE`; next pass should find the generated symbols artifact, verify Play's expected upload format, and upload/document it with the next AAB.
- R8/app optimization note: release builds already enable R8 minify, resource shrinking, and `proguard-android-optimize.txt`. Next pass should verify no Gradle property disables full mode and decide whether a configuration-analyzer pass is useful before public rollout.

### 2026-07-15 - Play warning cleanup completed
- Theme deprecation: replaced the deprecated status-bar color compatibility path with `ComponentActivity.enableEdgeToEdge(...)`. The old `@Suppress("DEPRECATION")` is gone.
- Native debug symbols: the prod release pipeline now creates `app/build/outputs/native-debug-symbols/prodRelease/fynlo-prod-release-native-symbols.zip`. Upload this ZIP in Play Console when the native-code warning appears.
- R8/app optimization: release still uses `isMinifyEnabled = true`, `isShrinkResources = true`, and `proguard-android-optimize.txt`. `:app:verifyProdReleasePlayReadiness` now checks the AAB, R8 mapping file, optimized resource task, and symbol ZIP.
- No version bump, install, commit, push, or Play release was done in this pass. Do those later when the phone is available.
- Verification passed: `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, and `:app:verifyProdReleasePlayReadiness`.

### 2026-07-15 - 3.2.114 internal-test candidate
- Version: `versionName 3.2.114`, `versionCode 238`.
- User-facing cleanup: contact country-code picker no longer uses flag emoji; options are plain code/name labels.
- Source hygiene: app source emoji/flag scan is clean for `app/src/main` and `app/src/dev`; dev setup notes no longer use warning glyphs/arrows.
- Warning cleanup: removed the stale lint baseline that contained old SDK warning entries no longer reported by release lint.
- Installed on phone: `app.fynlo` and `app.fynlo.dev`; both launch sanity checks passed.
- Fresh AAB: `app/build/outputs/bundle/prodRelease/app-prod-release.aab`.
- Native symbols ZIP for Play Console: `app/build/outputs/native-debug-symbols/prodRelease/fynlo-prod-release-native-symbols.zip`.
- Verification passed: `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, `:app:installProdDebug`, `:app:installDevDebug`, launch sanity for both packages, and `:app:verifyProdReleasePlayReadiness`.

### 2026-07-15 - Premium Minimal design-system pass
- Direction applied: Premium Minimal + Soft Neo-Glass + Bento structure, with clarity first and visual effects kept light for performance.
- Shared ledger cards, panels, rows, and metric cards now use the same soft surface, subtle border, and low-elevation treatment.
- Added reusable premium primitives: `PremiumSoftGlassPanel` for future hero/confirmation/bento sections and `PremiumSkeletonBlock` for loading states.
- Dashboard initial cloud-check loading now uses shared skeleton blocks instead of a private one-off skeleton.
- Bottom navigation now has theme-aware soft-glass color, smoother selected-state scaling, and selected colors that remain readable in dark mode.
- Global Material shape tokens were tuned slightly more premium while preserving readable, compact finance layouts.
- Scope: visual system, loading, and navigation polish only. No accounting, sync, Firestore, auth, or schema behavior changed.
- Verification passed: `:app:compileProdDebugKotlin` and `:app:testProdDebugUnitTest`.

### 2026-07-16 - Visible premium dashboard/invest pass
- User feedback: the premium style was still too subtle on-phone, especially on Dashboard and Invest.
- Dashboard now shows stronger glass tiles inside the net-worth hero, compact premium quick-action tiles, and softly raised account rows instead of divider-only rows.
- Invest now uses the emerald hero style for portfolio value and stronger soft-glass holding cards with clearer value/invested/funding grouping.
- Scope: visible UI hierarchy only. No accounting, loan/debt/investment calculation logic, balance mutation, sync, Firestore, auth, or schema behavior changed.
- Verification passed: `:app:compileProdDebugKotlin` and `:app:testProdDebugUnitTest`.

### 2026-08-16 - Focused search, navigation, and investment polish
- Bottom navigation was tightened to reduce unused side/bottom padding while keeping the rounded premium style.
- Global search now uses cleaner status-bar spacing and the shared search field, addressing the large top-gap issue seen on phone.
- Lending, debt, global search, and transaction history now share the same `PremiumSearchField` treatment.
- Investment cards and portfolio metric tiles were compacted so the screen feels less spacious and more scannable.
- Scope: UI/UX polish only. No calculation logic, balance mutation, cloud sync, auth, schema, or Play release behavior changed.
- Verification passed: `:app:compileProdDebugKotlin`, `:app:testProdDebugUnitTest`, `:app:installProdDebug`, `:app:installDevDebug`, and launch sanity for `app.fynlo` / `app.fynlo.dev`.

### 2026-08-17 - Ledger trust and correction UX pass
- Investment add/edit now shows a clear review panel before saving: account-funded investments move cash, debt-funded investments only link the debt source, and new-loan investments create the debt source.
- New investments still default current value to invested amount; Book Check now also catches older or imported investments where current value is zero despite invested amount being present.
- Book Check is grouped into `Needs action`, `Review only`, and old-record notes so testers can focus on real money-risk items first.
- Regression coverage confirms cleared debt plus active debt-funded investment is valid and should not be treated as an error.
- Smoke focus for next phone pass: add investment from account, add investment from existing debt, edit investment source, delete investment with/without reverse, run Book Check, and confirm the warnings are plain and actionable.
