# Financial Logic Spec

This is the current reconstructed accounting contract for Fynlo Ledger. It is intentionally written as product/accounting behavior, not as implementation jargon.

## Core Ledger Principles

1. Every money action must have one clear financial meaning.
2. Account balances are changed only by actual account movements.
3. Derived summaries must be recalculated from source records, not patched manually.
4. Net worth must not change for pure transfers between accounts.
5. Principal, interest, waiver, and account movement are different concepts and must not be mixed.
6. Old unclear data should be reviewed through Book Check instead of silently guessed.

## Account Movements

| Action | Source account | Destination account | Net-worth effect | Notes |
|---|---|---|---|---|
| Income | none/external | selected account | increases assets | One account credit. |
| Expense | selected account | none/external | decreases assets | One account debit. |
| Transfer | from account | to account | unchanged | Debit and credit must match. |
| Loan given | source account | borrower receivable | usually unchanged at net-worth level | Cash decreases, receivable increases. |
| Loan repayment collected | borrower receivable | deposit account | principal movement unchanged; interest income increases assets when collected | Principal reduces receivable; account increases. |
| Debt taken | external lender | selected account | assets and liabilities both increase | Account receives cash; debt payable increases. |
| Debt payment | selected account | debt payable | principal payment reduces asset and liability; interest expense reduces assets | Interest and principal split matters. |
| Investment purchase | source account | investment asset | usually unchanged at purchase | Cash decreases, investment asset increases. |
| Investment withdrawal | investment asset | receiving account | depends on value/gain treatment | Must not double-count cash and investment. |

## Interest Accrual

Interest is always derived from the current user-visible loan/debt start date and the selected method.

The app must not freeze interest just because interest was paid or collected ahead.

For a borrower loan:

- Accrued Interest = interest calculated from loan start date through the effective date.
- Collected Interest = interest payment rows that belong to the current period.
- Waived Interest = user-approved waived amount.
- Interest Due = `max(0, Accrued Interest - Collected Interest - Waived Interest)`.
- Advance Interest = `max(0, Collected Interest - Accrued Interest)`.

For a debt:

- Accrued Interest = interest payable from debt start date through the effective date.
- Paid Interest = interest payment rows that belong to the current period.
- Waived/adjusted interest, if available, reduces payable interest only with user confirmation.
- Interest Payable = `max(0, Accrued Interest - Paid Interest - Waived Interest)`.
- Advance Paid = `max(0, Paid Interest - Accrued Interest)`.

## Due-Date Capping

Some personal loans/debts should not accrue interest after the due date. The app supports a user-controlled stop-after-due behavior.

Rules:

- If stop-after-due is off, accrual continues beyond due date.
- If stop-after-due is on and a due date exists, accrual caps at due date.
- Turning the option on should not change principal or payment rows.
- The UI should say this plainly, for example: “Interest is calculated only up to due date.”

## Date Edits

When the user edits a loan/debt start date:

1. Update the visible date.
2. Rebuild paid principal and paid interest from actual payment rows.
3. Recalculate accrued interest from the new date.
4. Do not create duplicate payment rows.
5. Do not silently change payment dates.
6. If older payment rows are unclear, route them to Book Check review.

## Period-Aware Interest Payments

Interest-only payments can be one of these user meanings:

| Meaning | Effect |
|---|---|
| This period | Reduces current interest due. |
| Older interest | Keeps history but does not reduce current period due. |
| Advance interest | Explains why current interest due is zero until accrual catches up. |
| Extra/manual | Preserved for audit/review; not guessed. |

The user-facing wording should avoid internal terms. Use phrases such as:

- “This payment needs review.”
- “Choose where this interest belongs.”
- “Interest paid ahead.”
- “Old interest history.”

## Book Check Severity

| Severity | Meaning | Example |
|---|---|---|
| Critical | Actual data corruption or impossible account movement. | Transfer source and destination same, payment total mismatch, orphan critical movement. |
| Warning/Review | User decision needed or old data is unclear. | Interest payment period unknown, paid-ahead interest, missing old trace. |
| Info | Helpful status or non-blocking detail. | Synced, no duplicate rows. |

Paid-ahead interest is not serious corruption. It is a review/explanation item unless actual ledger rows are broken.

## Net Worth Guardrails

Net worth should not increase simply because the app deleted/restored a record, changed a date, or relabeled a source. Only real income, asset gains, liability decreases, or similar financial events should change it.

Critical checks:

- Investment funding must not create both cash and investment value.
- Debt-funded investment should not add cash again if debt receipt already moved money.
- Transfer must be equal debit/credit.
- Principal repayment should not count as income beyond the account movement already recorded.
- Interest collected/paid should affect assets once, through the actual transaction row.
