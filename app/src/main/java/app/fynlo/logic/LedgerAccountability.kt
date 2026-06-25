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
            if (borrower.sourceAccount.isBlank() && fundingTxn == null) {
                addIssue(LedgerIssueSeverity.INFO, "Loan funding trace missing", "${borrower.name} was created before a funding account was linked. Future loans record this automatically.", "loan", borrower.id)
            }
            if (fundingTxn == null) {
                addIssue(LedgerIssueSeverity.INFO, "Loan disbursement trace missing", "${borrower.name} has no linked disbursement row. This is usually legacy/imported data.", "loan", borrower.id)
            }
            val paymentTotal = paymentsByLoan[borrower.id].orEmpty().sumOf { it.amount }
            if (abs(paymentTotal - borrower.paid) > 0.01) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Loan payment total mismatch", "${borrower.name} paid total does not match payment rows.", "loan", borrower.id)
            }
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
            if (receivedTxn == null) {
                addIssue(LedgerIssueSeverity.INFO, "Debt receipt trace missing", "${debt.name} has no linked Debt Received row. Future debts record the destination account automatically.", "debt", debt.id)
            } else if (receivedTxn.toAcct.isBlank()) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Debt destination missing", "${debt.name} does not show where borrowed money was deposited.", "debt", debt.id)
            } else if (abs(receivedTxn.amount - debt.amount) > 0.01) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Debt receipt amount mismatch", "${debt.name} is ${CurrencyFormatter.detail(debt.amount)} but its received transaction is ${CurrencyFormatter.detail(receivedTxn.amount)}.", "debt", debt.id)
            }
            val paymentTotal = debtPaymentsByDebt[debt.id].orEmpty().sumOf { it.amount }
            if (abs(paymentTotal - debt.paid) > 0.01) {
                addIssue(LedgerIssueSeverity.CRITICAL, "Debt payment total mismatch", "${debt.name} paid total does not match payment rows.", "debt", debt.id)
            }
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
            if (investment.fundingSource.isBlank()) {
                addIssue(LedgerIssueSeverity.INFO, "Investment funding trace missing", "${investment.name} was created before the funding source was captured. Future investments record this automatically.", "investment", investment.id)
            }
            if (investment.sourceType in setOf("existing_debt", "new_loan") && investment.linkedDebtId.isBlank()) {
                addIssue(LedgerIssueSeverity.WARNING, "Investment debt link missing", "${investment.name} is debt-funded but has no linked debt id.", "investment", investment.id)
            }
            if (linked.none { it.category.equals("Investment", true) }) {
                addIssue(LedgerIssueSeverity.INFO, "Investment ledger trace missing", "${investment.name} has no linked investment row. This is usually legacy/imported data.", "investment", investment.id)
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

    private fun isOldOpen(date: String, today: LocalDate): Boolean =
        runCatching { LocalDate.parse(date).plusDays(90).isBefore(today) }.getOrDefault(false)

    private fun investmentFundingPrefix(sourceType: String): String = when (sourceType) {
        "existing_debt" -> "Debt funds from"
        "new_loan" -> "New loan from"
        "account" -> "Account funds from"
        else -> "Funds from"
    }

    private fun syncLabel(status: SyncStatus): String = when (status) {
        SyncStatus.Synced -> "Synced"
        SyncStatus.Syncing -> "Syncing"
        SyncStatus.Offline -> "Offline: local changes may be pending"
        SyncStatus.Initialising -> "Sync initialising"
        is SyncStatus.Error -> "Sync error: ${status.message}"
    }
}
