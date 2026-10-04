# Net-worth history and waiver review

## Scope and evidence

Reviewed the private 2026-10-04 post-install database backup from 3.3.23,
history-saving code, and both remaining waiver warnings. No phone database,
payment, waiver, date, principal or account balance was changed in this review.
Private DB/export evidence stays outside Git. Personal-use app; no AAB.

The prior same-data comparison of 3.3.22 and 3.3.23 reconciles exactly:

| Component | Rupees |
| --- | ---: |
| Previous calculated net worth | 17,949,863.20 |
| Reduction in borrower interest receivable | -191,069.15 |
| Reduction in debt interest payable | +35,261.23 |
| Current calculated net worth | 17,794,055.28 |

Cash/accounts, investment values and principal did not change.

## Confirmed history defects

1. `repairNetWorthHistoryPlaceholdersAfterLedgerLoad` accepted the first nonzero
   summary when ANY ledger collection had data. Independent StateFlows start
   empty, so an accounts-only, investments-only or debts-only emission could be
   persisted as the day's complete net worth. The zero-only guard did not catch
   those partial but nonzero totals. `saveSnapshotNow` also read a UI cache.
2. Backfill subtracted selected cash flows from today's net worth, invented
   zero liabilities, and presented the result as historical snapshots. That
   cannot reproduce past interest, valuations, debt balances or edited terms.

The backup has 29 saved history rows (2026-06-30 through 2026-10-04). For
example, October 4 history contains 4,797,275.74 assets/net worth and zero
liabilities, exactly the account total, whereas the complete current ledger
has assets 19,847,188.12 and liabilities 2,053,132.84. Other rows contain only
investments or only liabilities. June/July month-end rows have timestamps
from the approximate-backfill path. Not every old row is proven wrong, but
the series must not be treated as audited financial history.

## Bounded fix: 3.3.24 / 264

- Dashboard and capture share `NetWorthTotals` with unchanged 3.3.23 interest
  policy and balance-sheet inclusion rules.
- A snapshot is calculated from accounts, investments, borrowers, debts and
  both payment tables read in one Room transaction. It never accepts a partial
  UI summary. Capture waits for a selected project and uses its fixed ID/date.
- Existing same-day history is refreshed, with a capture timestamp. Repeating
  this updates one snapshot, not payments or account balances. Another ledger's
  same-date row is protected from overwrite (legacy table keys only by date).
- Removed speculative backfill and automatic deletion of zero history from
  this flow. Past rows remain untouched. UI explains that old saved totals may
  be incomplete and changes may reflect corrections rather than money movement.
- No schema migration or reconstruction of unverifiable past numbers. When
  installed, opening the ledger/history can refresh that day's saved total;
  it will not repair all previous dates. No cloud/auth changes.

## Waiver notices

### Suryanarayana Vempadapu

- Principal 2,500,000; 36% simple interest; start 2024-01-01; due 2024-01-02;
  stop-after-due enabled; no payment rows; saved waiver 2,327,671.
- Both dates inclusive: calculated interest 4,931.50. The saved waiver exceeds
  it by 2,322,739.50. Interest due is floored at zero; principal remains intact.
- The warning correctly requests review. Earlier acknowledgement of the
  net-worth explanation is not authorization to edit these terms. Confirm the
  intended due date/cutoff/waiver before any record correction.

### Kalyani Ammamma (original principal 500,000)

- Start 2025-12-01, 18% simple interest. On 2026-07-01 the saved payment is
  545,000: principal 500,000 plus interest 45,000. Saved waiver is 15,410.
- Interest through the inclusive repayment date (213 days) is 52,520.54.
  Interest before waiver is therefore 7,520.54. The waiver exceeds that by
  7,889.46; due is zero. Principal remains zero, and no interest accrues on
  that returned principal after July 1.
- This does not create a refund, cash credit or negative debt. The historical
  waiver can reflect earlier calculations/terms and is not silently reduced.
  Review-only remains appropriate; the receipt must not be reclassified.

## Verification and limits

- Focused in-memory repository and waiver tests passed before the full run.
  Coverage: full capture without UI collectors; partial same-day replacement;
  unchanged money records; idempotent repeated captures; historical zero/older
  rows retained; selected-project isolation and collision protection; both
  waiver cases remain warnings without principal changes.
- Added shared-total tests for written-off exclusion, hand loans, negative
  cash and empty ledgers. Final full suite: 598 tests, zero failures/errors/skips.
  Both prod/dev debug APKs build successfully as 3.3.24 / 264.
- No new phone installation, visual smoke, live repair or cloud round-trip
  in this task. Old history cannot be certified from current rows alone;
  dated backups or other contemporaneous evidence would be needed.

## Authorized installation follow-up

- Both apps installed in place and verified as 3.3.24 / 264 and
  3.3.24-dev / 264 after fresh private backups. Production Home/history and
  restart, plus developer guest Home/history, passed scoped phone checks.
- Production today's snapshot changed from accounts-only 4,797,275.74 to
  net worth 17,794,055.28, assets 19,847,188.12, liabilities 2,053,132.84,
  with a nonzero capture timestamp. All other 28 snapshots are identical.
- Before/after database integrity is OK in both apps. Financial tables are
  unchanged; only today's production snapshot and an existing deletion-marker
  timestamp changed. Developer tables unchanged. No Fynlo crash observed.
- No waiver or payment classification changed. No historical reconstruction,
  fresh cloud round-trip, uninstall, reset or AAB. Private evidence is under
  C:/Users/user/.codex/tmp/fynlo-3.3.24-phone-qa/ and excluded from Git.
