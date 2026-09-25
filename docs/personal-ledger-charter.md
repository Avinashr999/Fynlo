# Fynlo Ledger Personal Mode Charter

Last updated: 2026-09-25

## Product Direction

Fynlo Ledger is now treated as a personal lifetime ledger first, not a generalized public-market finance app first.

The app can still be built and tested like a production Android app, but product decisions should be optimized for one trusted owner who needs long-term financial memory, clear interest calculations, and safe cloud-backed records.

## Non-Negotiables

- Never lose existing data.
- Never silently mutate loan, debt, account, investment, payment, or transaction history.
- Every repair that changes financial records must be previewed, reversible where possible, and confirmed.
- Cloud sync must protect lifetime data, not overwrite local records silently.
- Local data must remain usable when offline.
- Every feature or bug-fix release must bump `versionCode` and `versionName` before an APK/AAB is shared for testing.
- Book Check should explain issues in normal language, not developer/accounting internals.
- UI should stay clean and personal: only show what helps understand money, interest, balances, and history.

## Keep Strong

These areas are important for the personal app and should remain first-class:

- Dashboard totals and daily movement.
- Loans given / borrowers.
- Debts taken / lenders.
- Accounts and account statements.
- Investments, including debt-funded investments.
- Expenses and income.
- Transfers between accounts.
- Money trail for every important record.
- Book Check / repair preview.
- Search.
- Cloud sync and backup confidence.
- PIN/security lock.
- Reports that explain personal ledger health.

## Reduce Or Hide From Main UX

These should not dominate the app now that it is personal-use first:

- Play Store launch guidance.
- Public onboarding language.
- Generic multi-user explanations.
- Marketing-style feature sections.
- Developer-only diagnostics in normal screens.
- Noisy automation/review panels on the dashboard.
- Repeated warnings that do not require an action.
- Technical labels such as allocation, stale period, derived bucket, sync state internals, or repair identifiers.

Developer and release notes can still exist in docs or hidden/debug surfaces.

## Interest Calculation Rule To Preserve

Loans given and debts taken must calculate interest from the date money was actually given or received.

When principal is fully or partially paid:

- Interest for the paid principal portion should run from that principal's active start date to the payment/settlement date.
- Future interest should continue only on remaining principal.
- Interest-only payments should not reduce principal.
- Older-interest payments should settle their declared period and move the current interest window forward only for that period.
- Paid-ahead interest should explain why interest due is zero, but it must not freeze future accrual.
- Waived interest should reduce due interest only by the waived amount and should be visible as waived history.

Important date-policy note:

The existing engine uses standard day-count behavior where `daysBetween(start, end)` excludes the end date; for example, 28-06-2025 to 28-06-2026 is 365 days. If the personal ledger should count the final payment day inclusively, that must be introduced as an explicit policy with regression tests and a clear migration note, because it can change historical interest values.

## Data Migration And Repair Rules

Before any migration or data repair:

1. Take or verify a backup/export.
2. Run a read-only audit and show the differences.
3. Separate real corruption from valid personal accounting decisions.
4. Add tests for the exact scenario.
5. Make repair idempotent so it is safe to run more than once.
6. Only mutate records after explicit confirmation.
7. Log what changed in `PROJECT_STATE_FOR_AI.md`.

Allowed automatic cleanup:

- Derived cache refresh.
- UI summary recalculation.
- Non-financial display metadata.

Not allowed silently:

- Changing principal.
- Changing interest paid/collected.
- Changing account balance.
- Deleting transactions.
- Moving money between accounts.
- Reclassifying income, expense, loan, debt, or investment rows.

## Personal UX Direction

Every main screen should answer one of these questions quickly:

- How much do I have?
- How much should I receive?
- How much should I pay?
- Where did this money come from?
- Where did this money go?
- What interest is due now?
- What changed after the last payment?
- Is my cloud backup safe?

Preferred user-facing terms:

- Principal remaining.
- Interest due.
- Interest paid / collected.
- Interest waived.
- Paid ahead.
- Given from account.
- Received into account.
- Balance before / change / balance after.
- Needs review.

Avoid user-facing technical terms:

- Allocation.
- Stale period.
- Derived bucket.
- Replay engine.
- Sync entity.
- Migration row.
- Paise ledger internals.

## Personal Roadmap

Phase 1: Protect and clarify current data.

- Keep the current database.
- Strengthen tests around real personal cases.
- Add read-only audits for account totals, borrower/debt principal, investment funding, and interest windows.
- Keep Book Check actionable and calm.

Phase 2: Personal simplification.

- Hide or move public-release and developer-only surfaces away from the main experience.
- Keep only personal workflows prominent: lend, debt, transfer, income, expense, investment, account history, search, reports, backup.
- Tune wording to personal ledger language.

Phase 3: Lifetime backup confidence.

- Improve visible sync state.
- Add manual export/backup reminders.
- Add conflict previews before overwrite/merge.
- Keep local-first safety.

Phase 4: Interest precision policy.

- Decide whether final-day-inclusive interest is required.
- If yes, implement it behind tests and a migration note.
- Recheck all borrower and debt examples before installing to phone.

## Version Rule

Before sharing an APK/AAB after any app behavior change:

- Bump `versionCode`.
- Bump `versionName`.
- Record the change in `PROJECT_STATE_FOR_AI.md`.
- Run compile and unit tests.
- Install prod/dev debug builds for phone smoke when a phone is available.

