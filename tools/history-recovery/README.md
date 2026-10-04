# Dated history recovery

These tools do not restore a database or change financial records. Keep inputs,
outputs, backups and owner identifiers outside Git. Never use today's ledger to
fill a requested past date. The date argument must be supported by the dated
backup's capture evidence. A valid SQLite file alone does not establish that date.

1. Make private copies of a dated backup, including matching WAL/SHM files.
2. Run `export_dated_ledger.py DATABASE CAPTURE_DATE PRIVATE_INPUT_JSON` for
   each candidate. Use a filename ending in `-input.json`.
3. Run `NetWorthHistoryRecoveryTest` with `FYNLO_HISTORY_RECOVERY_DIR` set to
   that private directory. Its opt-in offline pass uses the real calculation
   engine and writes `-result.json` files. The ordinary suite needs no data.
4. Review source dates, identities, complete-table counts, payment classifications
   and known corrections. No review reasons does not by itself establish real-world
   correctness. Compare against later corrected backups where available. Keep the
   SHA-256 of the canonical source ledger and record the calculation-policy version.
5. Export a fresh pre-install phone backup as the current input. After approval,
   `prepare_request.py CURRENT_INPUT PRIVATE_REQUEST RESULT...` builds a guarded
   request. It refuses current/future dates and complete existing points.
6. The debug-only instrumentation harness `ApprovedHistoryRecoveryTest` is skipped
   unless `approvedHistoryRecovery=yes` is explicitly supplied. It expects
   `files/approved-history-recovery.json`, verifies all six input tables against
   before-images, and runs the history-only repository operation twice. It asserts
   financial backup contents are unchanged. It does not call cloud APIs.
7. Compare private pre/post database copies, including every financial and audit
   table. Verify original values remain in `originalSnapshotJson`. Remove the
   private payload and test APK after verification, not the owner's apps.

Past days without sufficient evidence remain unavailable. Recovered totals are
recomputed using the documented current policy, not a claim about what a buggy
old app displayed. Never interpolate missing dates or force totals to an old
remembered number. Do not enable a production importer for arbitrary payloads.
