# Personal Mode Cleanup Plan

Last updated: 2026-09-25

This plan converts Fynlo Ledger from a general-public app posture into a private lifetime ledger posture without losing existing data.

## Principle

Hide or reduce public/general noise only when it does not change financial records.

Do not remove core ledger features. The owner still needs loans, debts, accounts, investments, expenses, transfers, reports, Book Check, search, sync, backup, and security.

## Keep Prominent

- Dashboard net worth, assets, debts, daily interest, and cloud backup status.
- Borrowers / loans given.
- Debts / loans taken.
- Accounts and account statements.
- Investments and funding source clarity.
- Income, expense, transfer, and correction flows.
- Money trail on important records.
- Book Check, but grouped and calm.
- Search.
- Reports that explain personal ledger health.
- PIN/security and backup/export.

## Reduce From Main Screens

- Play Store review prompts.
- Public onboarding language.
- Generic "for all users" copy.
- Marketing-style claims.
- Developer/debug wording.
- Repeated dashboard warnings when one Book Check entry is enough.
- Automation/review cards that do not need immediate personal action.

## Move To Settings Or Docs

- Release notes.
- Play Console notes.
- Debug/audit export details.
- Developer-only diagnostics.
- App-store policy wording.

## Do Not Remove Yet

- Google sign-in/cloud backup.
- Internal Book Check repair tools.
- Data export/import.
- Sync conflict UI.
- Audit trail.
- Backup restore preview.
- Proof attachments.

These protect lifetime data and are still useful for a personal ledger.

## Safe Implementation Order

1. Keep all database tables and sync fields unchanged.
2. Add feature visibility flags only at UI level if needed.
3. Hide public/noisy surfaces from primary navigation first, not from data/storage.
4. Preserve access from Settings or a diagnostics/help screen.
5. Add tests only for logic changes; UI cleanup can be compile-verified and phone-smoked.
6. Update `PROJECT_STATE_FOR_AI.md` after each behavior change.
7. Bump version for every installable behavior-changing build.

## Current Cleanup Candidates

- Remove or hide Play Store review prompt copy from normal personal use. Done in 3.3.2: automatic review prompts and the Settings Play Store rating row were removed.
- Keep onboarding short: local ledger, cloud backup, PIN.
- Keep Dashboard focused on totals, cloud state, Book Check if action exists, and quick actions.
- Move long Book Check explanations into item details.
- Keep About screen personal, not promotional.
- Ensure all warnings use plain language and one next action.

## Audit Helper

- Added `scripts/read-only-ledger-audit.ps1` for future phone-connected checks. It copies the app database to `.codex-db-dumps/` and audits the copy in read-only SQLite mode.
- The script reports borrower/debt aggregate mismatches, zero-value investments, missing linked debts, weak account trail rows, and open sync conflicts.
- It must not be treated as a repair script. It does not write to the phone database.
