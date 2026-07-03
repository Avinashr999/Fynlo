package app.fynlo.logic

import app.fynlo.data.SyncStatus
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerAccountabilityTest {

    @Test
    fun `ledger health warns when loan waiver exceeds unpaid interest`() {
        val borrower = Borrower(
            id = "loan-over-waived",
            name = "Over Waived Loan",
            amount = 36500.0,
            rate = 10.0,
            date = "2026-06-29",
            intType = "Simple Interest",
            interestWaived = 500.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-29"),
        )

        assertTrue(report.issues.any { it.title == "Loan interest waiver exceeds unpaid interest" })
    }

    @Test
    fun `ledger health warns when debt waiver exceeds unpaid interest`() {
        val debt = Debt(
            id = "debt-over-waived",
            name = "Over Waived Debt",
            amount = 36500.0,
            rate = 10.0,
            date = "2026-06-29",
            intType = "Simple Interest",
            interestWaived = 500.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-29"),
        )

        assertTrue(report.issues.any { it.title == "Debt interest waiver exceeds unpaid interest" })
    }

    @Test
    fun `ledger health ignores old loan interest-only rows after interest period rolls forward`() {
        val borrower = Borrower(
            id = "loan-rolled-interest",
            name = "Rolled Interest Loan",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-06-29",
            intType = "Simple Interest",
            paid = 18_000.0,
            paidPrincipal = 0.0,
            paidInterest = 18_000.0,
        )
        val oldInterestSettlement = Payment(
            id = "pay-old-interest",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-06-28",
            type = "Interest Only",
            amount = 18_000.0,
            interest = 18_000.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2025-06-29",
            interestPeriodEndDate = "2026-06-29",
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = listOf(oldInterestSettlement),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-29"),
        )

        assertFalse(report.issues.any { it.title == "Loan payment total mismatch" })
        assertFalse(report.issues.any { it.title == "Extra interest already collected" })
    }

    @Test
    fun `ledger health warns when loan date edit leaves stale advance interest period`() {
        val borrower = Borrower(
            id = "lakshmi-date-edit",
            name = "Lakshmi Devi",
            amount = 15_000.0,
            rate = 12.0,
            date = "2026-07-01",
            intType = "Simple Interest",
            paid = 64.0,
            paidPrincipal = 0.0,
            paidInterest = 64.0,
        )
        val staleAdvance = Payment(
            id = "old-advance",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-06-20",
            type = "Interest Only",
            amount = 64.0,
            interest = 64.0,
            interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
            interestPeriodStartDate = "2026-06-01",
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = listOf(staleAdvance),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-07-03"),
        )

        assertTrue(report.issues.any {
            it.title == "Interest payment needs review" && it.severity == LedgerIssueSeverity.WARNING
        })
        assertFalse(report.issues.any { it.title == "Extra interest already collected" })
    }

    @Test
    fun `ledger health ignores old debt interest-only rows after interest period rolls forward`() {
        val debt = Debt(
            id = "debt-rolled-interest",
            name = "Rolled Interest Debt",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-06-29",
            intType = "Simple Interest",
            paid = 18_000.0,
            paidPrincipal = 0.0,
            paidInterest = 18_000.0,
        )
        val oldInterestSettlement = DebtPayment(
            id = "debt-pay-old-interest",
            debtId = debt.id,
            name = debt.name,
            date = "2026-06-28",
            type = "Interest Only",
            amount = 18_000.0,
            interest = 18_000.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2025-06-29",
            interestPeriodEndDate = "2026-06-29",
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = listOf(oldInterestSettlement),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-29"),
        )

        assertFalse(report.issues.any { it.title == "Debt payment total mismatch" })
        assertFalse(report.issues.any { it.title == "Extra interest already paid" })
    }

    @Test
    fun `ledger health warns when debt date edit leaves stale advance interest period`() {
        val debt = Debt(
            id = "debt-date-edit",
            name = "Edited Debt",
            amount = 15_000.0,
            rate = 12.0,
            date = "2026-07-01",
            intType = "Simple Interest",
            paid = 64.0,
            paidPrincipal = 0.0,
            paidInterest = 64.0,
        )
        val staleAdvance = DebtPayment(
            id = "old-debt-advance",
            debtId = debt.id,
            name = debt.name,
            date = "2026-06-20",
            type = "Interest Only",
            amount = 64.0,
            interest = 64.0,
            interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
            interestPeriodStartDate = "2026-06-01",
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = listOf(staleAdvance),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-07-03"),
        )

        assertTrue(report.issues.any {
            it.title == "Interest payment needs review" && it.severity == LedgerIssueSeverity.WARNING
        })
        assertFalse(report.issues.any { it.title == "Extra interest already paid" })
    }

    @Test
    fun `ledger health warns for unclear loan interest period without treating it as paid ahead`() {
        val borrower = Borrower(
            id = "unclear-loan",
            name = "Unclear Loan",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 10_000.0,
            paidPrincipal = 0.0,
            paidInterest = 10_000.0,
        )
        val unclear = Payment(
            id = "unclear-interest",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-04-01",
            type = "Interest Only",
            amount = 10_000.0,
            interest = 10_000.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = listOf(unclear),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-05-01"),
        )

        assertTrue(report.issues.any {
            it.title == "Interest payment needs review" &&
                it.severity == LedgerIssueSeverity.WARNING &&
                "Unclear Loan" in it.detail &&
                "Choose whether it belongs to older interest" in it.detail
        })
        assertFalse(report.issues.any { it.title == "Extra interest already collected" })
    }

    @Test
    fun `ledger health warns when corrected loan date has paid interest ahead of accrued interest`() {
        val borrower = Borrower(
            id = "loan-interest-paid-ahead",
            name = "Samanvi Travels",
            amount = 125_500.0,
            rate = 24.0,
            date = "2026-04-22",
            intType = "Simple Interest",
            paid = 39_492.0,
            paidPrincipal = 0.0,
            paidInterest = 39_492.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = listOf(
                Payment(
                    id = "samanvi-paid-ahead",
                    loanId = borrower.id,
                    name = borrower.name,
                    date = "2026-06-30",
                    type = "Interest Only",
                    amount = 39_492.0,
                    interest = 39_492.0,
                    interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
                    interestPeriodStartDate = borrower.date,
                )
            ),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-07-01"),
        )

        assertTrue(report.issues.any { it.title == "Extra interest already collected" })
        assertTrue(report.issues.any {
            it.title == "Extra interest already collected" && it.severity == LedgerIssueSeverity.WARNING
        })
        assertFalse(report.issues.any { it.title == "Loan payment total mismatch" })
    }

    @Test
    fun `ledger health warns not critical when debt interest is paid ahead`() {
        val debt = Debt(
            id = "debt-interest-paid-ahead",
            name = "Advance Interest Debt",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 30_000.0,
            paidPrincipal = 0.0,
            paidInterest = 30_000.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = listOf(
                DebtPayment(
                    id = "debt-paid-ahead-row",
                    debtId = debt.id,
                    name = debt.name,
                    date = "2026-06-01",
                    type = "Interest Only",
                    amount = 30_000.0,
                    interest = 30_000.0,
                    interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
                    interestPeriodStartDate = debt.date,
                )
            ),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-01"),
        )

        assertTrue(report.issues.any {
            it.title == "Extra interest already paid" && it.severity == LedgerIssueSeverity.WARNING
        })
        assertFalse(report.issues.any {
            it.title == "Extra interest already paid" && it.severity == LedgerIssueSeverity.CRITICAL
        })
        assertFalse(report.issues.any { it.title == "Debt payment total mismatch" })
    }

    @Test
    fun `ledger health warns when borrower interest period appears complete`() {
        val borrower = Borrower(
            id = "borrower-period-complete",
            name = "Mohan Rao",
            amount = 25_000.0,
            rate = 18.0,
            date = "2024-05-22",
            due = "2025-05-22",
            intType = "Simple Interest",
            paid = 4_500.0,
            paidPrincipal = 0.0,
            paidInterest = 4_500.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = listOf(
                Payment(
                    id = "period-interest-row",
                    loanId = borrower.id,
                    name = borrower.name,
                    date = "2025-05-22",
                    type = "Interest Only",
                    amount = 4_500.0,
                    interest = 4_500.0,
                    interestAllocationType = InterestPolicy.CURRENT_PERIOD_INTEREST,
                    interestPeriodStartDate = borrower.date,
                    interestPeriodEndDate = borrower.due,
                )
            ),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2025-05-23"),
        )

        assertTrue(report.issues.any {
            it.title == "Loan may need next interest start date" &&
                it.severity == LedgerIssueSeverity.WARNING &&
                it.detail.contains("Mohan Rao") &&
                it.detail.contains("next day")
        })
        assertFalse(report.issues.any {
            it.title == "Loan may need next interest start date" &&
                it.severity == LedgerIssueSeverity.CRITICAL
        })
    }

    @Test
    fun `ledger health warns when debt interest period appears complete`() {
        val debt = Debt(
            id = "debt-period-complete",
            name = "Personal Loan",
            amount = 50_000.0,
            rate = 12.0,
            date = "2024-06-01",
            due = "2025-06-01",
            intType = "Simple Interest",
            paid = 6_000.0,
            paidPrincipal = 0.0,
            paidInterest = 6_000.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = listOf(
                DebtPayment(
                    id = "debt-period-interest-row",
                    debtId = debt.id,
                    name = debt.name,
                    date = "2025-06-01",
                    type = "Interest Only",
                    amount = 6_000.0,
                    interest = 6_000.0,
                    interestAllocationType = InterestPolicy.CURRENT_PERIOD_INTEREST,
                    interestPeriodStartDate = debt.date,
                    interestPeriodEndDate = debt.due,
                )
            ),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2025-06-02"),
        )

        assertTrue(report.issues.any {
            it.title == "Debt may need next interest start date" &&
                it.severity == LedgerIssueSeverity.WARNING &&
                it.detail.contains("Personal Loan") &&
                it.detail.contains("next day")
        })
        assertFalse(report.issues.any {
            it.title == "Debt may need next interest start date" &&
                it.severity == LedgerIssueSeverity.CRITICAL
        })
    }

    @Test
    fun `ledger health groups multiple unclear loan interest payments into one borrower review`() {
        val borrower = Borrower(
            id = "samanvi-grouped-interest",
            name = "Samanvi Travels",
            amount = 600_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 256_384.0,
            paidPrincipal = 0.0,
            paidInterest = 256_384.0,
        )
        val payments = listOf(
            Payment(
                id = "interest-review-1",
                loanId = borrower.id,
                name = borrower.name,
                date = "2026-06-30",
                type = "Interest Only",
                amount = 216_892.0,
                interest = 216_892.0,
            ),
            Payment(
                id = "interest-review-2",
                loanId = borrower.id,
                name = borrower.name,
                date = "2026-06-30",
                type = "Interest Only",
                amount = 39_492.0,
                interest = 39_492.0,
            ),
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = payments,
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-07-03"),
        )

        val groupedReviews = report.issues.filter { it.title == "Interest payments need review" }
        assertTrue(groupedReviews.size == 1)
        assertTrue(groupedReviews.single().recordType == "loan")
        assertTrue(groupedReviews.single().recordId == borrower.id)
        assertTrue(groupedReviews.single().detail.contains("2 interest payments"))
        assertTrue(groupedReviews.single().detail.contains("Samanvi Travels"))
        assertFalse(report.issues.any { it.recordType == "payment" && it.title.contains("Interest payment") })
    }

    @Test
    fun `ledger health groups multiple unclear debt interest payments into one debt review`() {
        val debt = Debt(
            id = "debt-grouped-interest",
            name = "Muhammed",
            amount = 400_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 154_000.0,
            paidPrincipal = 0.0,
            paidInterest = 154_000.0,
        )
        val payments = listOf(
            DebtPayment(
                id = "debt-interest-review-1",
                debtId = debt.id,
                name = debt.name,
                date = "2026-06-30",
                type = "Interest Only",
                amount = 137_162.0,
                interest = 137_162.0,
            ),
            DebtPayment(
                id = "debt-interest-review-2",
                debtId = debt.id,
                name = debt.name,
                date = "2026-06-30",
                type = "Interest Only",
                amount = 16_838.0,
                interest = 16_838.0,
            ),
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = payments,
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-07-03"),
        )

        val groupedReviews = report.issues.filter { it.title == "Interest payments need review" }
        assertTrue(groupedReviews.size == 1)
        assertTrue(groupedReviews.single().recordType == "debt")
        assertTrue(groupedReviews.single().recordId == debt.id)
        assertTrue(groupedReviews.single().detail.contains("2 interest payments"))
        assertTrue(groupedReviews.single().detail.contains("Muhammed"))
        assertFalse(report.issues.any { it.recordType == "debt_payment" && it.title.contains("Interest payment") })
    }
}
