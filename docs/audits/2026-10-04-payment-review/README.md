# Payment and interest review - 2026-10-04

## Status and scope

### Phone delivery follow-up

Both personal APKs installed in place as 3.3.23 / 263 on 2026-10-04.
Physical-phone read-only smoke covered statements, payment previews, the
restricted history editor, restart and Book check. All four focused synthetic
UI tests passed on the phone. No real payment or financial edit was submitted.
Before/after backups confirm all production financial tables are identical;
SQLite integrity checks pass. One cloud deletion timestamp refreshed, and the
empty developer app added an automatic snapshot. No Fynlo crash observed.
Book check shows zero serious issues and two existing waiver reviews, not
silently repaired. Owner authorized commit/push; no public-release AAB.

## Fix follow-up: 3.3.23 / 263

All six findings below now have code fixes and regular regression coverage:

- Repayment history edits preserve the payment row and all money fields. Only
  description/notes can be edited there; financial edits are rejected before
  any write. The UI shows the same restriction. Both repository implementations
  check the current transaction before saving; undo is covered too.
- Personal simple-interest replay uses outstanding principal from the day after
  principal is paid. Flat-rate engine/EMI formulas are unchanged.
- Due-date stop caps accrual only; later payments still reduce what is owed.
- Zero-principal saved splits are respected by the policy AND Room aggregate
  queries, including penalty-only rows. Unknown mixed amounts are not assumed
  to be principal by the principal helpers.
- Frozen interest, including zero, is respected by borrower breakdowns,
  snapshots, payment previews and settlement quotes after its effective date.
- As-of filtering covers principal, interest and settled-period selection;
  payment-form period labels use the selected date. Explicit empty/future-only
  history never falls back to stale cached paid totals. The legacy API without
  supplied payment history remains distinct.

`app/src/test/java/app/fynlo/logic/PaymentReviewRegressionTest.kt` now contains
the former failing policy reproductions plus boundary cases. The history-edit
reproduction and cash/history/undo/Room checks are regular tests in
`PaymentResplitV330DataIntegrityTest`. No known-failure tests remain parked
outside the ordinary suite for these six findings.

Final verification: **590 unit tests pass**, zero failures/errors/skips. Both
prod/dev APKs and the Android test APK build successfully. **Four emulator UI
tests pass**, covering both repayment-note editors and both interest-only
payment forms. The isolated emulator had network disabled; no owner data was
used. The original 10 failures are now passing regular regression cases.

No owner-phone installation, historical
repair, migration, account balance write, commit/push or AAB in this fix request.
Derived interest totals may change where the old code overcharged interest;
that is not a change to saved payments or cash balances.

## Original review (before fixes)

The approved 3.3.22 / 262 payment-purpose and stale-cloud-import fixes are ready
to commit. This additional review found **six unresolved defects**. Do not treat
the existing passing unit suite as proof that the entire accounting system is
correct. No owner data was changed during this review; all reproductions use
synthetic values and an in-memory database.

Reviewed borrower/debt snapshots, payment replay, transaction-history editing,
the payment sync changes and the approved repair helper. This is not an
exhaustive security, performance, investment, transfer, or whole-app UI audit.

## Confirmed findings (open at original review, addressed above)

### P1 - Editing repayment history discards the saved purpose

- `app/src/main/java/app/fynlo/data/FinanceRepository.kt:803` deletes the payment
  matching amount/date and creates a new unclassified `Both` row, then resplits it.
  The debt branch at line 838 does the same; `TransactionManager.kt:140` repeats
  the pattern. History only protects `journal_only` rows; the main repayment
  transaction has no such tag, so its Edit action is available.
- Synthetic reproduction: loan 10,000 at 12%, start 2026-01-01; collect 200 on
  2026-02-03 as settled January interest. Edit only the transaction's notes.
- Observed: payment ID replaced and **88.22 becomes principal**, previously zero.
  This provides another route to the user's original symptom even though adding
  a new purpose-labelled payment is fixed.
- Fix direction: preserve payment identity and purpose on nonfinancial edits;
  route financial changes through the original payment with explicit preview.
  Avoid amount/date matching when multiple equal payments exist.

### P1 - Simple-interest replay keeps charging the original principal

- `app/src/main/java/app/fynlo/logic/InterestEngine.kt:507` uses
  `originalPrincipalPaise`, while the payment-aware breakdown uses dated remaining
  principal. The primary snapshot takes due from the former.
- Synthetic borrower and debt: 100,000 at 18%, start January 1; all principal
  paid January 10; evaluate January 20 (2026, both boundaries inclusive).
- Expected interest 493.15 through payment day; actual snapshot due **986.30**.
  The same cause affects partial repayments. This must be fixed in the personal
  loan policy without accidentally changing an explicitly flat-rate EMI contract.

### P1 - Stop-after-due is bypassed by the primary due calculation

- `InterestPolicy.kt:542` and `:578` pass uncapped `asOf` into paise replay/terms.
  Breakdowns separately apply `effectiveAsOf`, so different fields disagree.
- Synthetic borrower and debt: 100,000 at 18%, January 1 start, January 31 due,
  stop-after-due enabled; evaluate February 10.
- Expected 1,528.76; actual primary interest due **2,021.91**.
- Fix must stop accrual at the cutoff while still applying payments made later;
  simply filtering all rows to the due date would lose late repayments.

### P1 - A saved zero-principal split can become all principal

- `InterestPolicy.kt:449` and `:455` fall back to the full amount whenever type
  is not `Interest Only` and principal is zero.
- Synthetic valid saved row: `Both`, amount 100, principal 0, interest 100.
  Both principal helpers return **100**, expected zero. Both replay and summary
  callers use these helpers. A zero is a valid saved split, not a missing value.
- Fix direction: distinguish a complete saved split from an unclassified legacy
  row; respect penalty/rounding fields, and do not guess unknown history.

### P1 - Frozen interest is ignored by the primary borrower snapshot

- `InterestPolicy.kt:265` obtains interest due from paise calculations even when
  its breakdown correctly respects `Defaulted` / `frozenInterest`.
- Synthetic borrower: 100,000 at 18%, January 1 start, frozen interest 100;
  evaluate January 20. Breakdown accrued is 100; primary interest due **986.30**.
- Freeze semantics, payments and waivers need one shared policy across totals,
  details and payment previews.

### P2 - Future repayments reduce an earlier snapshot's principal

- `InterestPolicy.kt:265` and `:286` pass all payment rows to principal and
  breakdown calculations; paise replay independently filters by as-of date.
- Synthetic borrower and debt: 100,000 principal, February 1 principal payment
  10,000; January 15 snapshot shows **90,000**, expected 100,000.
- Fix needs consistent as-of filtering, including current-period settlement
  selection, without falling back to aggregate totals containing future rows.

## Reproduction evidence

Temporary tests were run with `:app:testProdDebugUnitTest`, selecting
`app.fynlo.logic.ReviewDiagnosticTest` and the history-edit diagnostic in
`PaymentResplitV330DataIntegrityTest`. **10 tests ran; all 10 failed** at their
expected correctness assertions, confirming the findings above. The history-edit
test was rerun with principal asserted before ID: expected 0, actual 88.22.

At review time, nine policy tests were kept outside the ordinary suite as
`ReviewDiagnosticTest.kt`. They have now been moved into the regular test suite
as `PaymentReviewRegressionTest.kt`; run that class to verify the fixes.

For the tenth test, add this method temporarily to the existing
`PaymentResplitV330DataIntegrityTest` (which supplies the in-memory repository and
`seedLoan` / `pay` helpers):

```kotlin
@Test
fun `notes edit must preserve payment classification`() = runBlocking {
    seedLoan("Simple Interest")
    val payment = pay("review-notes", "2026-02-03", 200.0).copy(
        type = "Interest Only", interest = 200.0,
        interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
        interestPeriodStartDate = "2026-01-01",
        interestPeriodEndDate = "2026-01-31")
    repository.insertPaymentWithDest(payment, "Personal Cash", "personal")
    val transaction = db.dao().getTransactionsByRef("loan")
        .single { it.category == "Loan Repayment" }
    repository.editTransaction(transaction, transaction.copy(notes = "Receipt note corrected"))
    val after = db.dao().getPaymentsForLoanOnce("loan").single()
    assertEquals(0.0, after.principal, 0.0)
    assertEquals(payment.id, after.id)
    assertEquals(InterestPolicy.OLD_PERIOD_INTEREST, after.interestAllocationType)
}
```

## Original review limits and next work (historical)

- These findings are not proof that every live account is affected. No fresh
  live account-by-account audit or correction was performed in this request.
- Potential cloud deletion/parent-summary races were noticed during source
  reading but not reproduced; do not label them confirmed defects yet.
- Resolve the six confirmed findings with regression tests before declaring
  accounting globally settled. Keep principal, payment identity and cash history
  intact; historical corrections still require evidence and owner approval.
- Commit/push here preserves the already verified 3.3.22 fix and this honest
  open-risk record. No new runtime changes, version bump, phone installation or
  AAB are part of the review follow-up.
