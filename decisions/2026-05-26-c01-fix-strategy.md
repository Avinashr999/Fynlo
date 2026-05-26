# C01 fix strategy — Payments as the single source of truth

**Date:** 2026-05-26
**Status:** Accepted (Stage 1 + Stage 2 both implemented 2026-05-26)
**Affects clusters:** C01 (Recalculate destroys payment history), C02 (stale exports), C03 (schema integrity), INF04 (data-integrity test gate)
**Sprint:** 1
**Supersedes:** —

> **Implementation status:**
>
> **Stage 1 (landed)** — C01 runtime destruction stopped. Backfill migration `v15 → v16` lands the synthetic Payment row for every legacy borrower/debt; `FinanceRepository.recalculateAllBalances()` no longer calls the destructive DAO queries and only derives `paid` from `payments` / `debt_payments` via the rebuild queries (whose `WHERE EXISTS` gate is removed and whose `SUM(...)` is wrapped in `COALESCE(..., 0)`). `RecalculateBalancesDataIntegrityTest` covers three cases (legacy-with-backfill, current-schema, brand-new) and the `checks.yml` data-integrity gate is re-enabled (closes INF04).
>
> **Stage 2 (landed)** — the invariant `paid == SUM(payments)` is now structurally enforced. `editTransaction` (`FinanceRepository.kt`) replaces its direct `updateBorrowerPaidAmount` / `updateDebtPaidAmount` calls with delete-old-Payment + insert-new-Payment + `rebuildBorrowerPaidFromPayments()` (and the debt twin). `deleteTransaction`'s "no matching Payment" fallback that mutated `paid` directly is removed — when there's no Payment to delete, `paid` is left as-is. `insertPaymentWithDest` and `insertDebtPaymentWithSource` replace their conditional principal/interest direct-writers with a single `rebuildBorrowerPaidFromPayments()` / `rebuildDebtPaidFromDebtPayments()` call after the Payment row is inserted. Ten DAO queries and their repository wrappers are deleted: `updateBorrowerPaid{Amount,Principal,Interest}`, `updateDebtPaid{Amount,Principal,Interest}`, `recalculateBorrowerPaid`, `recalculateDebtPaid`, and the dead-code `seedPaidPrincipalFromPaid` / `seedDebtPaidPrincipalFromPaid`. After this stage, the only writer of `paid` / `paidPrincipal` / `paidInterest` is the rebuild query — there is no way for new code to violate the invariant without re-introducing one of the deleted methods.

---

## Context

`UX_AUDIT_2026-05-25.md §C01` documents the highest-severity bug in the
codebase: tapping **Recalculate Balances** silently zeroes the cumulative
`paid` field on borrowers/debts whose principal/interest split was never
populated (the *legacy* data shape, where partial repayment lived only on
`borrower.paid`). The reproducer in
`app/src/test/java/app/fynlo/data/RecalculateBalancesDataIntegrityTest.kt`
makes the destruction visible: `paid: 50000.0 → 0.0`.

The bug is the end-state of a layered history, not a single mistake:

1. **v3-era schema** tracked repayments only as a cumulative `paid: Double`
   on the parent borrower/debt record. No separate repayment records.
2. **Migration 9 → 10** added `paidPrincipal` / `paidInterest` columns
   *and* seeded `paidPrincipal = paid` for existing rows — preserving old
   data at *that* point.
3. **Migration 10 → 11** ran `UPDATE borrowers SET paid = paidPrincipal +
   paidInterest`. Safe **once**, because of the seeding in 9 → 10.
4. The same destructive formula was *also* installed as the runtime DAO
   query `recalculateBorrowerPaid()` (`FynloDao.kt:62`) and is invoked on
   every user-initiated Recalculate. It is safe *only* when
   `paidPrincipal + paidInterest == paid` — i.e., when the split is in
   sync with the cumulative field. **Any code path that updates `paid`
   without also updating the split produces a row the next Recalculate
   will silently destroy.**
5. **Migration 12 → 13** introduced a "rebuild from payments table" fix,
   which is correct — but `recalculateAllBalances()` runs the destructive
   SQL **first**, and the rebuild that follows is gated by
   `WHERE EXISTS (payments WHERE loanId = borrowers.id)`, so it can't
   recover a row whose only repayment record was the now-zeroed `paid`
   field.

The repository already has a `payments` table (and a sibling
`debt_payments` table) with `loanId`, `amount`, `principal`, `interest`,
`type`, `date`. The naming in UX_AUDIT §C01 ("Add `loanRepayments[]` and
`debtRepayments[]`") was aspirational — the right collections already
exist. **The architectural problem is not missing tables**; it is that
`paid` is treated as a writable field by some code paths and as a derived
field by others, with no invariant enforced between them.

## Decision

**`payments` and `debt_payments` are the single source of truth for
repayment history. `borrower.paid` / `debt.paid` (and the
`paidPrincipal`/`paidInterest` splits) are derived projections of those
tables, and may only be written by code that has just inserted, updated,
or deleted a row in the corresponding payments table.**

The Sprint 1 implementation:

1. **Backfill migration `v15 → v16`.** For every `borrower` row where
   `paid > 0` AND no rows in `payments` reference it, insert one synthetic
   Payment row with
   `amount = paid`,
   `principal = paid`,
   `interest = 0`,
   `date = borrowers.date` *(loan date — best available approximation per
   UX_AUDIT §C01 fix step 2)*,
   `type = "Legacy backfill"`,
   `notes = "Imported from legacy schema; actual repayment date unknown"`.
   Same shape for `debts` / `debt_payments`. The `"Legacy backfill"` type
   tag makes the row distinguishable in the UI, in exports, and in
   support investigations.

2. **Delete `FynloDao.recalculateBorrowerPaid()` and `recalculateDebtPaid()`.**
   These are the two destructive queries
   (`UPDATE borrowers SET paid = paidPrincipal + paidInterest` and its
   debts twin). They must not exist in the codebase after Sprint 1.

3. **`recalculateAllBalances()` becomes a single derive-from-truth pass.**
   It calls only `rebuildBorrowerPaidFromPayments()` and
   `rebuildDebtPaidFromDebtPayments()`. The `WHERE EXISTS` gate is removed
   from both — after the backfill in (1), every borrower/debt either has
   at least one payment row, or genuinely has zero repayments and
   `paid = 0`. Either case is correctly handled by `COALESCE(SUM(...), 0)`.

4. **Every code path that mutates `paid` must go through the payments table.**
   The direct UPDATE queries
   (`updateBorrowerPaidAmount`,
   `updateBorrowerPaidPrincipal`,
   `updateBorrowerPaidInterest`)
   are removed. The only writer is `insertPayment` / `deletePayment`,
   followed by a re-derivation from the payments sum. This is the
   invariant the data-integrity CI gate (INF04) will check on every PR
   once Sprint 1 ships.

5. **`updatedAt` correctness.** `updatedAt` is bumped on the parent
   borrower/debt whenever a Payment is inserted/updated/deleted for it.
   `recalculateAllBalances()` does **not** bump `updatedAt` when
   re-derivation produces the same numeric result — so sync conflict
   resolution stops being poisoned by no-op recalc passes (per UX_AUDIT
   §C01 root cause #3).

6. **C01 test is un-Ignored.**
   `RecalculateBalancesDataIntegrityTest.kt`'s destructive-case test
   (currently `@Ignore`'d per the INF01 resolution) drops the annotation
   in the same PR that ships the fix. The filtered
   `--tests '*DataIntegrity*' --tests '*Recalculate*' --tests '*Backup*'`
   step in `.github/workflows/checks.yml` is re-enabled in the same PR
   (closes INF04).

## Consequences

**Easier**

- **C02 collapses to a 5-line wrapper.** "Always call
  `recalculateAllBalances()` immediately before exporting." Because
  recalc is now safe and idempotent, this stops being a coordination
  problem.
- **C03 gains a real invariant:**
  `sum(payments WHERE loanId = X) == borrower[X].paid`. Auditable,
  testable, and gives future migrations a known-good shape to migrate
  *from*.
- **Sync conflict resolution becomes meaningful** — `updatedAt` actually
  reflects user intent, not a side-effect of background recalc.
- **The data-integrity CI gate becomes real** — INF04 goes from "no
  tests match" to "we run real Room SQL against a fixture on every PR."

**Harder**

- **Two call sites in the UI must be ported** to insert a Payment row
  first instead of `updateBorrowerPaid*`-style direct writes:
  the loan-detail screen's repayment dialog and the "Collect Pay" flow.
  Mechanical change, but it touches user-facing code so smoke-test
  coverage matters.
- **The synthetic backfill Payment is dated `loanDate`, not the real
  repayment date** — that data is genuinely lost and we are choosing a
  conservative approximation. The "Legacy backfill" type tag lets us be
  honest about this in the UI ("Imported from legacy data — actual date
  unknown") rather than fabricating precision.
- **A Firestore sync push happens for every backfilled Payment row.**
  For users with many legacy borrowers this is a one-time burst on first
  launch after upgrading to 3.2.2. The sync layer is already idempotent,
  so this is acceptable.

**Locked in** *(future ADRs would need to supersede this one to undo)*

- `payments` and `debt_payments` are the authoritative repayment history.
  Any future "let's denormalize for performance" instinct must change the
  *derivation*, never the source.
- `paid` / `paidPrincipal` / `paidInterest` on `Borrower` and `Debt`
  remain on the entity for read-path performance and Compose immutability
  — but they are not user-writable. The DAO's surface area shrinks
  accordingly.

## Alternatives considered

**A. Fix the SQL in place — make `recalculateBorrowerPaid()` non-destructive.**
Change to `UPDATE borrowers SET paid = paidPrincipal + paidInterest WHERE
paidPrincipal + paidInterest > 0`. *Rejected:* turns the symptom off but
leaves the deeper inconsistency (two writers for the same field, no
invariant) intact. The next migration that touches `paid` resurrects the
bug. Solves C01 without unblocking C02 or C03.

**B. Drop `paid`/`paidPrincipal`/`paidInterest` entirely; always compute
at read time.**
Cleaner conceptually, but Compose reads these fields on every
recomposition (Dashboard, every loan card). Recomputing per render is a
real performance regression. Keeping them as Room-stored derived columns,
with the invariant enforced at write time, is the better trade.

**C. Tombstone the legacy `paid` field rather than backfill.**
Treat all `paid > 0 && no payments` rows as "unknown history" and zero
them with a banner. *Rejected:* this is exactly what the bug already does.
The proactive-transparency principle in `RELEASE_PROTOCOL.md §6` (Halt
criteria) says don't destroy user data even when the schema is ambiguous
— backfill conservatively and label honestly.

## Cross-references

- `UX_AUDIT_2026-05-25.md` — §C01 (root cause, fix steps),
  §C02 (downstream cluster), §C03 (schema integrity),
  §9 INF01 / INF04
- `app/src/main/java/app/fynlo/data/local/FynloDao.kt` — destructive SQL
  at lines 62 (`recalculateBorrowerPaid`) and 79 (`recalculateDebtPaid`)
- `app/src/main/java/app/fynlo/data/FinanceRepository.kt:284` —
  `recalculateAllBalances()` orchestrator (the call sequence to rewrite)
- `app/src/main/java/app/fynlo/data/local/FynloDatabase.kt` —
  migrations 9 → 10 through 12 → 13 (the historical layers); the
  backfill migration this ADR proposes becomes `v15 → v16`
- `app/src/test/java/app/fynlo/data/RecalculateBalancesDataIntegrityTest.kt`
  — the C01 regression test (RED today, GREEN once this ADR is implemented)
- `RELEASE_PROTOCOL.md §8` — Sprint 1 release gates; the 3.2.2 release
  notes must reference back to this ADR before promoting to production
