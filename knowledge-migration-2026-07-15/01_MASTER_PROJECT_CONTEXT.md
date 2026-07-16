# Master Project Context

## Product

Fynlo Ledger is an Android personal finance ledger for tracking accounts, cash/bank balances, loans given, debts owed, investments, expenses, reports, and net worth.

The app is local-first. Users can continue without signing in, and cloud backup/sync is optional through Google/Firebase when configured and authenticated.

## Public Identity

- Public app name: Fynlo Ledger.
- Package name: `app.fynlo`.
- Developer flavor/package: `app.fynlo.dev`.
- Category: Finance / Personal finance ledger.
- Positioning: manual-entry personal finance ledger, not a bank, lender, or investment adviser.

## Technical Stack

- Kotlin Android app.
- Jetpack Compose UI.
- Room database.
- Firebase Auth / Firestore / Crashlytics / Performance / Analytics integrations.
- Google Credential Manager based sign-in path.
- Gradle product flavors for prod/dev.

## Core Product Promise

The app must help users answer quickly:

- How much money do I have?
- How much have I lent?
- How much do I owe?
- How much principal is pending?
- How much interest is due?
- Which account did money move from or to?
- Why does a total look the way it does?

## User Experience Direction

The user strongly prefers:

- Minimal screens.
- Clean spacing.
- Premium but calm colors.
- Plain financial language.
- Few noisy warnings.
- Clear confirmations and feedback after saving/deleting/editing.
- No developer/debug terminology in normal screens.

## Release State Summary

By 2026-07-15, the app had gone through internal-testing preparation and several version bumps through the 3.2.11x range. Play Console setup work included privacy policy, data safety, internal testing track, release notes formatting, native symbols, and R8 mapping upload.

The repository history shows repeated compile/test/install gates. Manual phone smoke was central to acceptance because the app’s critical behavior is financial trust and real-device usability.

## Documentation Habit Required Going Forward

After meaningful changes, update:

- `PROJECT_STATE_FOR_AI.md`
- `docs/release-handoff/2026-06-20-internal-testing-knowledge-hub.md`
- Any release worklog or migration package relevant to the change.

Future agents should treat these docs as mandatory context before editing money logic, sync logic, or release assets.
