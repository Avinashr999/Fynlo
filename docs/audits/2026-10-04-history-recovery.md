# Evidence-backed net-worth history recovery

## Scope

Owner requested recovery where evidence exists, exclusion of unverified totals
from trends, unavailable gaps rather than estimates, and no changes to financial
records. Both waivers are owner-confirmed intentional; no waiver edits performed.
Personal-use version 3.3.25 / 265; no AAB or public-release work.

## Sources and reconstruction

Read-only SQLite integrity checks passed. Source export includes all accounts,
investments, borrowers, debts and their saved payments. Dated source candidates:

| Date | Evidence | Assets | Liabilities | Net worth |
| --- | --- | ---: | ---: | ---: |
| 2026-09-27 | Named 20260927-164826 production DB copy | 19,818,887.73 | 2,047,379.42 | 17,771,508.31 |
| 2026-10-02 | Documented pre-261 production tar backup | 19,838,114.03 | 2,051,489.00 | 17,786,625.03 |
| 2026-10-04 | Verified post-3.3.24 production backup, control replay only | 19,847,188.12 | 2,053,132.84 | 17,794,055.28 |

September 27 and October 2 source ledger data hashes are identical:
`34d90452f945191f17438cc3d163a89dca7a2550bef727223981a8bbe976c902`.
Source counts: 6 accounts, 30 borrowers, 8 debts, 16 investments, 20 borrower
payments and 3 debt payments. Only the as-of date differs during calculation.
All saved prior payment classifications and loan/debt terms match the later
corrected October 4 backup. Later changes are the additional interest receipt,
its parent paid-interest total, the additional investment and account movement.
No old payment was reclassified to make this recovery work.

The October 4 control ledger hash is
`ce3b7c8fbf010867433de2a2a6543a5fea8addf6581f16a2cbcfdabb5f0ad817`.
Its calculated total exactly reproduces the independently phone-verified current
total. This date is not imported as a recovery: normal atomic capture remains
authoritative for today.

These are restated totals calculated from the records known in the dated backup
using the corrected complete-ledger policy, not claims that the old app displayed
these figures or that no real-world transaction was ever omitted. Other dates
are not reconstructed from today's edited records, interpolated, or inferred
from file names without dated capture evidence. The 26 remaining legacy saved
rows stay unverified. Days with no row also remain unavailable.

## Preservation and presentation

- Room 32 -> 33 adds history provenance, source reference and preserved original
  JSON columns only. No financial-table migration, deletes or balance changes.
  Existing rows default to LEGACY even if they have plausible totals/timestamps.
- Captures use COMPLETE_LEDGER_V1; reviewed recoveries use DATED_BACKUP_V1 with
  source digest and calculation policy. Replacing a legacy value preserves its
  original data. Recovery requires exact before-images and refuses other-project
  or existing trusted rows; the same request is idempotent.
- September 27 original 11,660,412.69 and October 2 original 4,803,275.74 are
  preserved inside the replacement records. The pre-upgrade October 4 row is
  also preserved when it is recaptured with explicit provenance.
- All repository trend readers and the PDF renderer exclude unverified rows.
  Monthly comparisons require the exact baseline date, not the last earlier
  available value. Charts do not join across missing days. The history page
  displays unavailable daily gaps, full rupees/paise, recovered labels and an
  expandable original-history view. Backup fingerprints stay behind the scenes.
- Backup format 3 includes history and originals. Empty appended fields preserve
  old v2 hash encoding; existing v1/v2 backups still decode. Restore replaces
  history with the selected backup's history rather than retaining unrelated
  trusted points. No backup restore was performed on the owner's phone.

## Verification

- Final installed APK restart verified three trusted production points and the
  unchanged current total. Developer empty history does not certify legacy rows.
  Both final database integrity checks pass. Every financial table matches the
  pre-install backup; all 26 remaining legacy rows are unchanged and all three
  preserved originals exactly match prior records after serialization defaults.
  One remote-deletion marker timestamp refreshed normally; its identity did not
  change. No Fynlo crash appears in the inspected crash buffer. Guest developer
  startup had a temporary cloud-check delay before Home, outside this fix's scope.

- 607 production unit tests pass, zero failures/errors/skips; final prod/dev APK
  builds pass. Coverage includes provenance gating, missing date lookup, invalid
  classifications, full reconstruction, idempotence, stale-input rollback,
  financial preservation, migration and backup hash/original round-trip.
- Phone recovery guard matched all six current input tables before writes.
  Recovery ran twice, then another repeat verification passed: OK (1 test).
  Initial final BackupData equality assertion was order-sensitive; independent
  full SQLite comparison showed all financial tables unchanged. The harness now
  compares sorted array contents with all fields retained, and rerun passes.
- No owner financial data is compiled into tools or tests. The harness requires
  explicit approval argument and a private before-image payload, is skipped in
  ordinary tests, and does not invoke cloud writes. Payload and test-only APK
  were removed from the phone after validation; production/developer retained.
- Private evidence: C:/Users/user/.codex/tmp/fynlo-history-recovery/ and
  C:/Users/user/.codex/tmp/fynlo-3.3.25-history-qa/. Never stage these or the private
  attachment/DB directories. Final phone check details are in PROJECT_STATE_FOR_AI.md.
