# Payment and interest review - 2026-10-04

## Status and scope

The approved 3.3.22 / 262 payment-purpose and stale-cloud-import fixes are ready
to commit. This additional review found **six unresolved defects**. Do not treat
the existing passing unit suite as proof that the entire accounting system is
correct. No owner data was changed during this review; all reproductions use
synthetic values and an in-memory database.

Reviewed borrower/debt snapshots, payment replay, transaction-history editing,
the payment sync changes and the approved repair helper. This is not an
exhaustive security, performance, investment, transfer, or whole-app UI audit.

## Confirmed findings (all open)

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

`ReviewDiagnosticTest.kt` in this folder preserves the nine pure policy tests.
It is deliberately outside Gradle's ordinary test source set, not silently
skipped or counted as passing. To reproduce, place it temporarily in
`app/src/test/java/app/fynlo/logic/` and select its class. Promote each test to the
regular regression suite when its associated defect is fixed.

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

## Limits and next work

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
