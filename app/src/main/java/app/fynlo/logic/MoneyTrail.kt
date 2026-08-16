package app.fynlo.logic

import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Transaction

data class BorrowerMoneyTrail(
    val disbursedFrom: String,
    val principalGiven: Double,
    val principalCollected: Double,
    val remainingPrincipal: Double,
    val interestCollected: Double,
    val linkedTransactionCount: Int,
    val paymentCount: Int,
)

data class DebtMoneyTrail(
    val receivedInto: String,
    val principalBorrowed: Double,
    val principalRepaid: Double,
    val remainingPrincipal: Double,
    val interestPaid: Double,
    val linkedTransactionCount: Int,
    val paymentCount: Int,
)

data class InvestmentMoneyTrail(
    val fundedFrom: String,
    val sourceStatus: String,
    val invested: Double,
    val currentValue: Double,
    val linkedTransactionCount: Int,
)

data class AccountMoneyTrail(
    val openingBalance: Double,
    val moneyIn: Double,
    val moneyOut: Double,
    val closingBalance: Double,
    val transactionCount: Int,
)

object MoneyTrail {
    fun borrower(
        borrower: Borrower,
        payments: List<Payment>,
        transactions: List<Transaction>,
        accountIdToName: Map<String, String>,
    ): BorrowerMoneyTrail {
        val linked = transactions.filter { it.ref == borrower.id }
        val fundingTxn = linked.firstOrNull {
            it.category.equals("Lending", ignoreCase = true) ||
                it.type.equals("Expense", ignoreCase = true)
        }
        val principalCollected = payments.sumOf(::borrowerPrincipal)
        val interestCollected = payments.sumOf(InterestPolicy::paymentInterestAmount)
        val disbursedFrom = borrower.sourceAccount.ifBlank {
            fundingTxn?.displayFromAcct(accountIdToName).orEmpty()
        }.ifBlank { "Unknown account" }

        return BorrowerMoneyTrail(
            disbursedFrom = disbursedFrom,
            principalGiven = borrower.amount,
            principalCollected = principalCollected,
            remainingPrincipal = (borrower.amount - principalCollected).coerceAtLeast(0.0),
            interestCollected = interestCollected,
            linkedTransactionCount = linked.size,
            paymentCount = payments.size,
        )
    }

    fun debt(
        debt: Debt,
        payments: List<DebtPayment>,
        transactions: List<Transaction>,
        accountIdToName: Map<String, String>,
    ): DebtMoneyTrail {
        val linked = transactions.filter { it.ref == debt.id }
        val receivedTxn = linked.firstOrNull { it.category.equals("Debt Received", ignoreCase = true) }
        val principalRepaid = payments.sumOf(::debtPrincipal)
        val interestPaid = payments.sumOf(InterestPolicy::debtPaymentInterestAmount)
        val receivedInto = receivedTxn?.displayToAcct(accountIdToName).orEmpty().ifBlank { "Unknown account" }

        return DebtMoneyTrail(
            receivedInto = receivedInto,
            principalBorrowed = debt.amount,
            principalRepaid = principalRepaid,
            remainingPrincipal = (debt.amount - principalRepaid).coerceAtLeast(0.0),
            interestPaid = interestPaid,
            linkedTransactionCount = linked.size,
            paymentCount = payments.size,
        )
    }

    fun investment(
        investment: Investment,
        debts: List<Debt>,
        transactions: List<Transaction>,
        accountIdToName: Map<String, String>,
    ): InvestmentMoneyTrail {
        val linked = transactions.filter { it.ref == investment.id }
        val linkedDebt = linkedDebtForInvestment(investment, debts)
        val fallbackSource = linked.firstOrNull()?.let { txn ->
            txn.displayFromAcct(accountIdToName).ifBlank { txn.displayToAcct(accountIdToName) }
        }.orEmpty()
        val sourceName = investment.fundingSource.ifBlank { fallbackSource }.ifBlank { "Unknown source" }
        val fundedFrom = when (investment.sourceType) {
            "existing_debt" -> "Funded by debt $sourceName"
            "new_loan" -> "Funded by new loan $sourceName"
            else -> "Funded from $sourceName"
        }
        val sourceStatus = when {
            investment.sourceType !in setOf("existing_debt", "new_loan") -> "Account-funded"
            linkedDebt == null -> "Source debt not found"
            (linkedDebt.amount - linkedDebt.paidPrincipal).coerceAtLeast(0.0) <= 0.01 -> "Source debt cleared"
            else -> "Source debt still payable ${CurrencyFormatter.detail((linkedDebt.amount - linkedDebt.paidPrincipal).coerceAtLeast(0.0))}"
        }

        return InvestmentMoneyTrail(
            fundedFrom = fundedFrom,
            sourceStatus = sourceStatus,
            invested = investment.invested,
            currentValue = investment.currentVal,
            linkedTransactionCount = linked.size,
        )
    }

    fun account(account: Account, transactions: List<Transaction>): AccountMoneyTrail {
        var moneyIn = 0.0
        var moneyOut = 0.0
        val matched = transactions.filter { it.matchesAccount(account.id, account.name) }
        matched.forEach { txn ->
            when (txn.type.lowercase()) {
                "income" -> if (txn.matchesToAccount(account)) moneyIn += txn.amount
                "expense" -> if (txn.matchesFromAccount(account)) moneyOut += txn.amount
                "transfer" -> {
                    if (txn.matchesToAccount(account)) moneyIn += txn.amount
                    if (txn.matchesFromAccount(account)) moneyOut += txn.amount
                }
            }
        }
        val opening = account.balance - moneyIn + moneyOut
        return AccountMoneyTrail(
            openingBalance = opening,
            moneyIn = moneyIn,
            moneyOut = moneyOut,
            closingBalance = account.balance,
            transactionCount = matched.size,
        )
    }

    fun linkedDebtForInvestment(investment: Investment, debts: List<Debt>): Debt? =
        investment.linkedDebtId.takeIf { it.isNotBlank() }?.let { debtId ->
            debts.firstOrNull { it.id == debtId }
        } ?: investment.fundingSource.takeIf { it.isNotBlank() }?.let { sourceName ->
            debts.firstOrNull { it.name.equals(sourceName, ignoreCase = true) }
        }

    private fun borrowerPrincipal(payment: Payment): Double = when {
        payment.type.equals("Interest Only", ignoreCase = true) -> 0.0
        payment.principal > 0.0 -> payment.principal
        else -> payment.amount
    }

    private fun debtPrincipal(payment: DebtPayment): Double = when {
        payment.type.equals("Interest Only", ignoreCase = true) -> 0.0
        payment.principal > 0.0 -> payment.principal
        else -> payment.amount
    }

    private fun Transaction.matchesFromAccount(account: Account): Boolean =
        fromAcctId == account.id || (fromAcctId.isBlank() && fromAcct.equals(account.name, ignoreCase = true))

    private fun Transaction.matchesToAccount(account: Account): Boolean =
        toAcctId == account.id || (toAcctId.isBlank() && toAcct.equals(account.name, ignoreCase = true))
}
