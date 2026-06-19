# Fynlo Release Knowledge Hub - Internal Testing 3.2.105

Date range covered: 2026-06-12 to 2026-06-20
Current release line: internal testing, versionName 3.2.105, versionCode 229
Package name: app.fynlo
App name: Fynlo
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

- App name: Fynlo
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

Fynlo is a personal finance ledger/tracker. It is not a bank, lender, payday loan app, direct loan provider, payment wallet, money transfer service, stock trading platform, crypto platform, or financial advisor.

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

Fynlo is not just a pretty finance app. The core promise is trustworthy personal ledgering: every money movement should have a source, destination, visible trace, reversible behavior, and an audit story. Design polish matters because it builds confidence, but accounting correctness is the product.
