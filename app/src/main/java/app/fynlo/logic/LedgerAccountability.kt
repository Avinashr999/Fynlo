package app.fynlo.logic

import app.fynlo.data.SyncStatus
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Transaction
import java.time.LocalDate
import kotlin.math.abs

enum class LedgerIssueSeverity { INFO, WARNING, CRITICAL }

data class LedgerIssue(
    val severity: LedgerIssueSeverity,
    val title: String,
    val detail: String,
    val recordType: String = "",
    val recordId: String = "",
)

data class LedgerDuplicateCandidate(
    val title: String,
    val detail: String,
    val transactionIds: List<String>,
)

data class LedgerMoneyTrail(
    val recordType: String,
    val recordId: String,
    val title: String,
    val amount: Double,
    val route: String,
    val referenceId: String,
)

data class LedgerAccountabilityReport(
    val score: Int,
    val issueCount: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val duplicateCount: Int,
    val linkedRecords: Int,
    val missingTraceCount: Int,
    val syncSummary: String,
    val issues: List<LedgerIssue>,
    val duplicates: List<LedgerDuplicateCandidate>,
    val trails: List<LedgerMoneyTrail>,
) {
    val headline: String
        get() = when {
            criticalCount > 0 -> "Needs attention"
            warningCount > 0 -> "Review recommended"
            else -> "Books healthy"
        }
}

object LedgerAccountability {

    fun inspect(
        accounts: List<Account>,
        transactions: List<Transaction>,
        borrowers: List<Borrower>,
        debts: List<Debt>,
        investments: List<Investment>,
        payments: List<Payment>,
        debtPayments: List<DebtPayment>,
        syncStatus: SyncStatus,
        today: LocalDate = LocalDate.now(),
    ): LedgerAccountabilityReport {
        val issues = mutableListOf<LedgerIssue>()
        val trails = mutableListOf<LedgerMoneyTrail>()
        val accountNames = accounts.map { it.name }.toSet()
        val accountIds = accounts.map { it.id }.toSet()
        val txByRef = transactions.groupBy { it.ref }.filterKeys { it.isNotBlank() }
        val paymentsByLoan = payments.groupBy { it.loanId }
        val debtPaymentsByDebt = debtPayments.groupBy { it.debtId }

        fun addIssue(
            severity: LedgerIssueSeverity,
            title: String,
            detail: String,
            recordType: String = "",
            recordId: String = "",
        ) {
            issues += LedgerIssue(severity, title, detail, recordType, recordId)
        }

        transactions.forEach { txn ->
            val lowerType = txn.type.lowercase()
            if (txn.amount <= 0.0) {
                addIssue(
                    LedgerIssueSeverity.CRITICAL,
                    "Invalid transaction amount",
                    "${txn.category} on ${txn.date} has a non-positive amount.",
                    "transaction",
                    txn.id,
                )
            }
            if (lowerType == "expense" && txn.fromAcct.isBlank()) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Expense missing source", "${txn.category} on ${txn.date} has no paying account.", "transaction", txn.id)
            }
            if (lowerType == "income" && txn.toAcct.isBlank()) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Income missing destination", "${txn.category} on ${txn.date} has no receiving account.", "transaction", txn.id)
            }
            if (lowerType == "transfer" && (txn.fromAcct.isBlank() || txn.toAcct.isBlank())) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Transfer missing route", "${txn.category} on ${txn.date} needs both source and destination.", "transaction", txn.id)
            }
            if (lowerType == "transfer") {
                val sameAccountId = txn.fromAcctId.isNotBlank() && txn.fromAcctId == txn.toAcctId
                val sameAccountName = txn.fromAcctId.isBlank() &&
                    txn.toAcctId.isBlank() &&
                    txn.fromAcct.isNotBlank() &&
                    txn.fromAcct.equals(txn.toAcct, ignoreCase = true)
                if (sameAccountId || sameAccountName) {
                    addIssue(
                        LedgerIssueSeverity.CRITICAL,
                        "Transfer uses same account",
                        "${txn.category} on ${txn.date} must move between two different accounts.",
                        "transaction",
                        txn.id,
                    )
                }
            }
            if (txn.fromAcctId.isNotBlank() && txn.fromAcctId !in accountIds) {
                addIssue(LedgerIssueSeverity.WARNING, "Source account id not found", "${txn.category} references a missing source account id.", "transaction", txn.id)
            } else if (txn.fromAcctId.isBlank() && txn.fromAcct.isNotBlank() && txn.fromAcct !in accountNames) {
                addIssue(LedgerIssueSeverity.WARNING, "Source account name not found", "${txn.category} references ${txn.fromAcct}, which is not an active account.", "transaction", txn.id)
            }
            if (txn.toAcctId.isNotBlank() && txn.toAcctId !in accountIds) {
                addIssue(LedgerIssueSeverity.WARNING, "Destination account id not found", "${txn.category} references a missing destination account id.", "transaction", txn.id)
            } else if (txn.toAcctId.isBlank() && txn.toAcct.isNotBlank() && txn.toAcct !in accountNames && txn.category != "Investment") {
                addIssue(LedgerIssueSeverity.WARNING, "Destination account name not found", "${txn.category} references ${txn.toAcct}, which is not an active account.", "transaction", txn.id)
            }
        }

        borrowers.forEach { borrower ->
            val linked = txByRef[borrower.id].orEmpty()
            val fundingTxn = linked.firstOrNull { it.category.equals("Lending", true) || it.type.equals("Expense", true) }
            val borrowerPayments = paymentsByLoan[borrower.id].orEmpty()
            val interestBreakdown = app.fynlo.logic.InterestPolicy.borrowerBreakdown(borrower, borrowerPayments, today.toString())
            if (borrower.sourceAccount.isBlank() && fundingTxn == null) {
                addIssue(LedgerIssueSeverity.INFO, "Loan funding trace missing", "${borrower.name} was created before a funding account was linked. Future loans record this automatically.", "loan", borrower.id)
            }
            if (fundingTxn == null) {
                addIssue(LedgerIssueSeverity.INFO, "Loan disbursement trace missing", "${borrower.name} has no linked disbursement row. This is usually legacy/imported data.", "loan", borrower.id)
            }
            val paymentTotal = borrowerPayments.sumOf { borrowerPrincipalForPaidTotal(it) } +
                borrowerPayments.sumOf { borrowerInterestForPaidTotal(it) }
            if (abs(paymentTotal - borrower.paid) > 0.01) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Loan payment total mismatch", "${borrower.name} paid total does not match payment rows.", "loan", borrower.id)
            }
            val unclearBorrowerInterestPayments = borrowerPayments.filter {
                val interest = borrowerInterestForPaidTotal(it)
                InterestPolicy.isUnclearInterestPayment(it.interestAllocationType, interest) ||
                    InterestPolicy.isStaleCurrentPeriodInterest(it.interestAllocationType, interest, it.interestPeriodStartDate, borrower.date)
            }
            if (unclearBorrowerInterestPayments.isNotEmpty()) {
                addIssue(
                    LedgerIssueSeverity.WARNING,
                    interestReviewTitle(unclearBorrowerInterestPayments.size),
                    unclearBorrowerInterestDetail(borrower, unclearBorrowerInterestPayments),
                    "loan",
                    borrower.id,
                )
            }
            addCompletedBorrowerInterestPeriodIssue(
                borrower = borrower,
                payments = borrowerPayments,
                today = today,
                addIssue = ::addIssue,
            )
            addInterestWaiverIssue(
                interestWaived = borrower.interestWaived,
                accruedInterest = interestBreakdown.accrued,
                paidInterest = interestBreakdown.paid,
                title = "Loan interest waiver exceeds unpaid interest",
                detail = "${borrower.name} has more waived interest than its unpaid interest balance.",
                recordType = "loan",
                recordId = borrower.id,
                addIssue = ::addIssue,
            )
            addInterestPaidAheadIssue(
                paidInterest = interestBreakdown.paid,
                accruedInterest = interestBreakdown.accrued,
                title = "Extra interest already collected",
                detail = "${borrower.name} has collected more interest than is due today. Interest due is zero for now, and new interest will keep adding from the loan date.",
                recordType = "loan",
                recordId = borrower.id,
                addIssue = ::addIssue,
            )
            trails += LedgerMoneyTrail(
                recordType = "loan",
                recordId = borrower.id,
                title = borrower.name,
                amount = borrower.amount,
                route = "From ${borrower.sourceAccount.ifBlank { fundingTxn?.fromAcct ?: "Unknown account" }} to borrower",
                referenceId = borrowerRef(borrower),
            )
        }

        debts.forEach { debt ->
            val linked = txByRef[debt.id].orEmpty()
            val receivedTxn = linked.firstOrNull { it.category.equals("Debt Received", true) }
            val currentDebtPayments = debtPaymentsByDebt[debt.id].orEmpty()
            val interestBreakdown = app.fynlo.logic.InterestPolicy.debtBreakdown(debt, currentDebtPayments, today.toString())
            if (receivedTxn == null) {
                addIssue(LedgerIssueSeverity.INFO, "Debt receipt trace missing", "${debt.name} has no linked Debt Received row. Future debts record the destination account automatically.", "debt", debt.id)
            } else if (receivedTxn.toAcct.isBlank()) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Debt destination missing", "${debt.name} does not show where borrowed money was deposited.", "debt", debt.id)
            } else if (abs(receivedTxn.amount - debt.amount) > 0.01) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Debt receipt amount mismatch", "${debt.name} is ${CurrencyFormatter.detail(debt.amount)} but its received transaction is ${CurrencyFormatter.detail(receivedTxn.amount)}.", "debt", debt.id)
            }
            val paymentTotal = currentDebtPayments.sumOf { debtPrincipalForPaidTotal(it) } +
                currentDebtPayments.sumOf { debtInterestForPaidTotal(it) }
            if (abs(paymentTotal - debt.paid) > 0.01) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Debt payment total mismatch", "${debt.name} paid total does not match payment rows.", "debt", debt.id)
            }
            val unclearDebtInterestPayments = currentDebtPayments.filter {
                val interest = debtInterestForPaidTotal(it)
                InterestPolicy.isUnclearInterestPayment(it.interestAllocationType, interest) ||
                    InterestPolicy.isStaleCurrentPeriodInterest(it.interestAllocationType, interest, it.interestPeriodStartDate, debt.date)
            }
            if (unclearDebtInterestPayments.isNotEmpty()) {
                addIssue(
                    LedgerIssueSeverity.WARNING,
                    interestReviewTitle(unclearDebtInterestPayments.size),
                    unclearDebtInterestDetail(debt, unclearDebtInterestPayments),
                    "debt",
                    debt.id,
                )
            }
            addCompletedDebtInterestPeriodIssue(
                debt = debt,
                payments = currentDebtPayments,
                today = today,
                addIssue = ::addIssue,
            )
            addInterestWaiverIssue(
                interestWaived = debt.interestWaived,
                accruedInterest = interestBreakdown.accrued,
                paidInterest = interestBreakdown.paid,
                title = "Debt interest waiver exceeds unpaid interest",
                detail = "${debt.name} has more waived interest than its unpaid interest balance.",
                recordType = "debt",
                recordId = debt.id,
                addIssue = ::addIssue,
            )
            addInterestPaidAheadIssue(
                paidInterest = interestBreakdown.paid,
                accruedInterest = interestBreakdown.accrued,
                title = "Extra interest already paid",
                detail = "${debt.name} has paid more interest than is due today. Interest payable is zero for now, and new interest will keep adding from the debt date.",
                recordType = "debt",
                recordId = debt.id,
                addIssue = ::addIssue,
            )
            trails += LedgerMoneyTrail(
                recordType = "debt",
                recordId = debt.id,
                title = debt.name,
                amount = debt.amount,
                route = "From lender to ${receivedTxn?.toAcct?.ifBlank { "Unknown account" } ?: "Unknown account"}",
                referenceId = debtRef(debt),
            )
        }

        investments.forEach { investment ->
            val linked = txByRef[investment.id].orEmpty()
            val investmentRows = linked.filter { it.category.equals("Investment", true) }
            val exactInvestmentRows = investmentRows.filter { abs(it.amount - investment.invested) <= 0.01 }
            if (investment.fundingSource.isBlank()) {
                addIssue(LedgerIssueSeverity.INFO, "Investment funding trace missing", "${investment.name} was created before the funding source was captured. Future investments record this automatically.", "investment", investment.id)
            }
            if (investment.sourceType in setOf("existing_debt", "new_loan") && investment.linkedDebtId.isBlank()) {
                addIssue(LedgerIssueSeverity.WARNING, "Investment debt link missing", "${investment.name} is debt-funded but has no linked debt id.", "investment", investment.id)
            }
            if (investmentRows.isEmpty()) {
                addIssue(LedgerIssueSeverity.INFO, "Investment ledger trace missing", "${investment.name} has no linked investment row. This is usually legacy/imported data.", "investment", investment.id)
            } else if (exactInvestmentRows.isEmpty()) {
                addIssue(
                    LedgerIssueSeverity.WARNING,
                    "Investment ledger trace amount mismatch",
                    "${investment.name} is ${CurrencyFormatter.detail(investment.invested)} but its linked investment trace is ${CurrencyFormatter.detail(investmentRows.first().amount)}.",
                    "investment",
                    investment.id,
                )
            }
            if (investmentRows.size > 1) {
                addIssue(
                    LedgerIssueSeverity.WARNING,
                    "Investment ledger trace duplicate",
                    "${investment.name} has ${investmentRows.size} linked investment traces. It should have one clear source trace.",
                    "investment",
                    investment.id,
                )
            }
            if (investment.sourceType in setOf("existing_debt", "new_loan")) {
                val movingTrace = investmentRows.firstOrNull { row ->
                    !row.type.equals("Info", true) ||
                        !hasTag(row, "journal_only") ||
                        row.fromAcct.isNotBlank() ||
                        row.toAcct.isNotBlank() ||
                        row.fromAcctId.isNotBlank() ||
                        row.toAcctId.isNotBlank()
                }
                if (movingTrace != null) {
                    addIssue(
                        LedgerIssueSeverity.CRITICAL,
                        "Debt-funded investment moves account balance",
                        "${investment.name} has a debt-funded investment trace that is not journal-only.",
                        "investment",
                        investment.id,
                    )
                }
            }
            trails += LedgerMoneyTrail(
                recordType = "investment",
                recordId = investment.id,
                title = investment.name,
                amount = investment.invested,
                route = "${investmentFundingPrefix(investment.sourceType)} ${investment.fundingSource.ifBlank { "Unknown source" }} to ${investment.name}",
                referenceId = investmentRef(investment),
            )
        }

        payments.forEach { payment ->
            if (borrowers.none { it.id == payment.loanId }) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Orphan loan payment", "${payment.name} payment on ${payment.date} has no loan.", "payment", payment.id)
            }
        }
        debtPayments.forEach { payment ->
            if (debts.none { it.id == payment.debtId }) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Orphan debt payment", "${payment.name} payment on ${payment.date} has no debt.", "debt_payment", payment.id)
            }
        }

        val duplicates = transactions
            .groupBy { duplicateKey(it) }
            .filterValues { rows -> rows.size > 1 }
            .values
            .map { rows ->
                val first = rows.first()
                LedgerDuplicateCandidate(
                    title = "${first.category} ${first.date}",
                    detail = "${rows.size} similar entries for ${CurrencyFormatter.detail(first.amount)}",
                    transactionIds = rows.map { it.id },
                )
            }

        duplicates.take(10).forEach { duplicate ->
            addIssue(
                LedgerIssueSeverity.WARNING,
                "Possible duplicate transaction",
                duplicate.detail,
                "transaction",
                duplicate.transactionIds.firstOrNull().orEmpty(),
            )
        }

        val oldOpenRecords = borrowers.count { isOldOpen(it.date, today) && it.paid < it.amount } +
            debts.count { isOldOpen(it.date, today) && it.paid < it.amount }
        if (oldOpenRecords > 0) {
            addIssue(
                LedgerIssueSeverity.INFO,
                "Older open records",
                "$oldOpenRecords open loan/debt records are older than 90 days. Consider reviewing or locking closed periods.",
            )
        }

        val missingTrace = issues.count {
            it.title.contains("source", true) ||
                it.title.contains("destination", true) ||
                it.title.contains("link missing", true)
        }
        val critical = issues.count { it.severity == LedgerIssueSeverity.CRITICAL }
        val warnings = issues.count { it.severity == LedgerIssueSeverity.WARNING }
        val infos = issues.count { it.severity == LedgerIssueSeverity.INFO }
        val score = (100 - critical * 18 - warnings * 7 - duplicates.size * 4).coerceIn(0, 100)

        return LedgerAccountabilityReport(
            score = score,
            issueCount = issues.size,
            criticalCount = critical,
            warningCount = warnings,
            infoCount = infos,
            duplicateCount = duplicates.size,
            linkedRecords = trails.count { "Unknown" !in it.route },
            missingTraceCount = missingTrace,
            syncSummary = syncLabel(syncStatus),
            issues = issues.sortedWith(compareBy<LedgerIssue> {
                when (it.severity) {
                    LedgerIssueSeverity.CRITICAL -> 0
                    LedgerIssueSeverity.WARNING -> 1
                    LedgerIssueSeverity.INFO -> 2
                }
            }.thenBy { it.title }),
            duplicates = duplicates,
            trails = trails.sortedBy { it.title.lowercase() },
        )
    }

    fun transactionImpact(transaction: Transaction): List<String> =
        when (transaction.type.lowercase()) {
            "expense" -> listOf("${transaction.fromAcct.ifBlank { "Source account" }} -${CurrencyFormatter.detail(transaction.amount)}")
            "income" -> listOf("${transaction.toAcct.ifBlank { "Destination account" }} +${CurrencyFormatter.detail(transaction.amount)}")
            "transfer" -> listOf(
                "${transaction.fromAcct.ifBlank { "Source account" }} -${CurrencyFormatter.detail(transaction.amount)}",
                "${transaction.toAcct.ifBlank { "Destination account" }} +${CurrencyFormatter.detail(transaction.amount)}",
            )
            else -> emptyList()
        }

    fun borrowerRef(borrower: Borrower): String = "LOAN-${borrower.date.take(4)}-${borrower.id.takeLast(4).uppercase()}"
    fun debtRef(debt: Debt): String = "DEBT-${debt.date.take(4)}-${debt.id.takeLast(4).uppercase()}"
    fun investmentRef(investment: Investment): String = "INV-${investment.date.take(4)}-${investment.id.takeLast(4).uppercase()}"

    private fun duplicateKey(t: Transaction): String =
        listOf(
            t.projectId,
            t.date,
            t.type.lowercase(),
            t.amount.toString(),
            t.category.lowercase(),
            t.fromAcct.lowercase(),
            t.toAcct.lowercase(),
            t.desc.trim().lowercase(),
        ).joinToString("|")

    private fun hasTag(transaction: Transaction, tag: String): Boolean =
        transaction.tags.split(",").any { it.trim().equals(tag, ignoreCase = true) }

    private fun isOldOpen(date: String, today: LocalDate): Boolean =
        runCatching { LocalDate.parse(date).plusDays(90).isBefore(today) }.getOrDefault(false)

    private fun borrowerPrincipalForPaidTotal(payment: Payment): Double = when {
        payment.type.equals("Interest Only", ignoreCase = true) -> 0.0
        payment.principal > 0.0 -> payment.principal
        else -> payment.amount
    }

    private fun borrowerInterestForPaidTotal(payment: Payment): Double =
        InterestPolicy.paymentInterestAmount(payment)

    private fun debtPrincipalForPaidTotal(payment: DebtPayment): Double = when {
        payment.type.equals("Interest Only", ignoreCase = true) -> 0.0
        payment.principal > 0.0 -> payment.principal
        else -> payment.amount
    }

    private fun debtInterestForPaidTotal(payment: DebtPayment): Double =
        InterestPolicy.debtPaymentInterestAmount(payment)

    private fun interestReviewTitle(count: Int): String =
        if (count == 1) "Interest payment needs review" else "Interest payments need review"

    private fun unclearBorrowerInterestDetail(borrower: Borrower, payments: List<Payment>): String {
        val principal = CurrencyFormatter.detail(borrower.amount)
        if (payments.size == 1) {
            val payment = payments.first()
            val amount = CurrencyFormatter.detail(borrowerInterestForPaidTotal(payment))
            val date = DateUtils.formatToDisplay(payment.date)
            return "${borrower.name} has an interest payment of $amount on $date for loan principal $principal. Choose whether it belongs to older interest, this loan period, paid in advance, or an extra note."
        }
        val total = CurrencyFormatter.detail(payments.sumOf { borrowerInterestForPaidTotal(it) })
        val latestDate = payments.maxByOrNull { it.date }?.date?.let(DateUtils::formatToDisplay) ?: "an unknown date"
        return "${borrower.name} has ${payments.size} interest payments totaling $total for loan principal $principal. Latest payment is on $latestDate. Open this borrower, edit those payments, and choose older interest, this loan period, paid in advance, or extra note."
    }

    private fun unclearDebtInterestDetail(debt: Debt, payments: List<DebtPayment>): String {
        val principal = CurrencyFormatter.detail(debt.amount)
        if (payments.size == 1) {
            val payment = payments.first()
            val amount = CurrencyFormatter.detail(debtInterestForPaidTotal(payment))
            val date = DateUtils.formatToDisplay(payment.date)
            return "${debt.name} has an interest payment of $amount on $date for debt principal $principal. Choose whether it belongs to older interest, this debt period, paid in advance, or an extra note."
        }
        val total = CurrencyFormatter.detail(payments.sumOf { debtInterestForPaidTotal(it) })
        val latestDate = payments.maxByOrNull { it.date }?.date?.let(DateUtils::formatToDisplay) ?: "an unknown date"
        return "${debt.name} has ${payments.size} interest payments totaling $total for debt principal $principal. Latest payment is on $latestDate. Open this debt, edit those payments, and choose older interest, this debt period, paid in advance, or extra note."
    }

    private fun addCompletedBorrowerInterestPeriodIssue(
        borrower: Borrower,
        payments: List<Payment>,
        today: LocalDate,
        addIssue: (LedgerIssueSeverity, String, String, String, String) -> Unit,
    ) {
        if (borrower.due.isBlank() || borrower.rate <= 0.0) return
        val dueDate = runCatching { LocalDate.parse(borrower.due) }.getOrNull() ?: return
        if (dueDate.isAfter(today)) return
        val principalOutstanding = (borrower.amount - borrower.paidPrincipal).coerceAtLeast(0.0)
        if (principalOutstanding <= 0.01) return

        val accruedForPeriod = InterestEngine.calcIntAccrued(
            amount = borrower.amount,
            rate = borrower.rate,
            loanDate = borrower.date,
            intType = borrower.intType,
            dueDate = borrower.due,
            asOf = borrower.due,
        )
        if (accruedForPeriod <= 0.01) return

        val reviewPayments = payments.filter { payment ->
            val allocation = payment.interestAllocationType
            val interest = borrowerInterestForPaidTotal(payment)
            interest > 0.01 &&
                allocation != InterestPolicy.OLD_PERIOD_INTEREST &&
                allocation != InterestPolicy.EXTRA_INTEREST &&
                !InterestPolicy.isStaleCurrentPeriodInterest(allocation, interest, payment.interestPeriodStartDate, borrower.date)
        }
        val reviewInterest = reviewPayments.sumOf { borrowerInterestForPaidTotal(it) }
        if (reviewInterest + 0.01 < accruedForPeriod) return
        val latestPayment = reviewPayments.maxByOrNull { it.date }

        addIssue(
            LedgerIssueSeverity.WARNING,
            "Loan may need next interest start date",
            completedPeriodDetail(
                name = borrower.name,
                principal = borrower.amount,
                paymentAmount = reviewInterest,
                paymentDate = latestPayment?.date.orEmpty(),
                startDate = borrower.date,
                dueDate = borrower.due,
                isDebt = false,
            ),
            "loan",
            borrower.id,
        )
    }

    private fun addCompletedDebtInterestPeriodIssue(
        debt: Debt,
        payments: List<DebtPayment>,
        today: LocalDate,
        addIssue: (LedgerIssueSeverity, String, String, String, String) -> Unit,
    ) {
        if (debt.due.isBlank() || debt.rate <= 0.0) return
        val dueDate = runCatching { LocalDate.parse(debt.due) }.getOrNull() ?: return
        if (dueDate.isAfter(today)) return
        val principalOutstanding = (debt.amount - debt.paidPrincipal).coerceAtLeast(0.0)
        if (principalOutstanding <= 0.01) return

        val accruedForPeriod = InterestEngine.calcIntAccrued(
            amount = debt.amount,
            rate = debt.rate,
            loanDate = debt.date,
            intType = debt.intType,
            dueDate = debt.due,
            asOf = debt.due,
        )
        if (accruedForPeriod <= 0.01) return

        val reviewPayments = payments.filter { payment ->
            val allocation = payment.interestAllocationType
            val interest = debtInterestForPaidTotal(payment)
            interest > 0.01 &&
                allocation != InterestPolicy.OLD_PERIOD_INTEREST &&
                allocation != InterestPolicy.EXTRA_INTEREST &&
                !InterestPolicy.isStaleCurrentPeriodInterest(allocation, interest, payment.interestPeriodStartDate, debt.date)
        }
        val reviewInterest = reviewPayments.sumOf { debtInterestForPaidTotal(it) }
        if (reviewInterest + 0.01 < accruedForPeriod) return
        val latestPayment = reviewPayments.maxByOrNull { it.date }

        addIssue(
            LedgerIssueSeverity.WARNING,
            "Debt may need next interest start date",
            completedPeriodDetail(
                name = debt.name,
                principal = debt.amount,
                paymentAmount = reviewInterest,
                paymentDate = latestPayment?.date.orEmpty(),
                startDate = debt.date,
                dueDate = debt.due,
                isDebt = true,
            ),
            "debt",
            debt.id,
        )
    }

    private fun completedPeriodDetail(
        name: String,
        principal: Double,
        paymentAmount: Double,
        paymentDate: String,
        startDate: String,
        dueDate: String,
        isDebt: Boolean,
    ): String {
        val direction = if (isDebt) "debt" else "loan"
        val paymentLabel = if (isDebt) "paid" else "collected"
        val dateText = paymentDate.takeIf { it.isNotBlank() }?.let { DateUtils.formatToDisplay(it) } ?: "an unknown date"
        return "$name has ${CurrencyFormatter.detail(paymentAmount)} interest $paymentLabel on $dateText for $direction principal ${CurrencyFormatter.detail(principal)}. The due date has passed. Review whether future interest should start from the next day."
    }

    private fun investmentFundingPrefix(sourceType: String): String = when (sourceType) {
        "existing_debt" -> "Debt funds from"
        "new_loan" -> "New loan from"
        "account" -> "Account funds from"
        else -> "Funds from"
    }

    private fun addInterestWaiverIssue(
        interestWaived: Double,
        accruedInterest: Double,
        paidInterest: Double,
        title: String,
        detail: String,
        recordType: String,
        recordId: String,
        addIssue: (LedgerIssueSeverity, String, String, String, String) -> Unit,
    ) {
        if (interestWaived < -0.01) {
            addIssue(
                LedgerIssueSeverity.CRITICAL,
                "Negative interest waiver",
                "Interest waiver cannot be negative.",
                recordType,
                recordId,
            )
            return
        }
        if (interestWaived <= 0.01) return

        val maxWaivable = (accruedInterest - paidInterest).coerceAtLeast(0.0)
        if (interestWaived - maxWaivable > 0.01) {
            addIssue(
                LedgerIssueSeverity.WARNING,
                title,
                detail,
                recordType,
                recordId,
            )
        }
    }

    private fun addInterestPaidAheadIssue(
        paidInterest: Double,
        accruedInterest: Double,
        title: String,
        detail: String,
        recordType: String,
        recordId: String,
        addIssue: (LedgerIssueSeverity, String, String, String, String) -> Unit,
    ) {
        if (paidInterest - accruedInterest > 0.01) {
            addIssue(
                LedgerIssueSeverity.WARNING,
                title,
                detail,
                recordType,
                recordId,
            )
        }
    }

    private fun syncLabel(status: SyncStatus): String = when (status) {
        SyncStatus.Synced -> "Synced"
        SyncStatus.Syncing -> "Syncing"
        SyncStatus.Offline -> "Offline: local changes may be pending"
        SyncStatus.Initialising -> "Sync initialising"
        is SyncStatus.Error -> "Sync error: ${status.message}"
    }
}
