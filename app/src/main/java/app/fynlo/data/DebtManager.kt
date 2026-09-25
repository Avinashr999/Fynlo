package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Transaction
import kotlinx.serialization.encodeToString

internal class DebtManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper
) {
    suspend fun insertDebtWithDestination(debt: Debt, destinationAccount: String, projectId: String = debt.projectId) {
        helper.requireOpenDate(debt.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getDebtById(debt.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val resolvedPeopleId =
                if (debt.peopleId.isNotEmpty()) debt.peopleId
                else helper.findOrCreatePersonId(debt.name, debt.phone, projectId)
            val d = debt.copy(
                projectId = projectId,
                peopleId  = resolvedPeopleId,
                updatedAt = now,
                createdAt = if (debt.createdAt == 0L) now else debt.createdAt,
            )
            ctx.dao.insertDebt(d)
            val destinationAccountId = ctx.dao.getAccountByName(destinationAccount)?.id ?: ""
            helper.applyAccountDelta(destinationAccountId, destinationAccount, debt.amount)
            val t = Transaction(app.fynlo.logic.Ids.newId(), debt.date, "Income", debt.amount, toAcct = destinationAccount, toAcctId = destinationAccountId, category = "Debt Received", desc = "Loan received from ${debt.name}", ref = d.id, notes = debt.notes, projectId = projectId, updatedAt = now, createdAt = now)
            ctx.dao.insertTransaction(t)
            helper.recordAudit(
                action = "CREATE",
                entityType = "debt",
                entityId = d.id,
                title = "Debt created: ${d.name}",
                afterValue = "borrowed=${d.amount}:to=$destinationAccount:txn=${t.id}",
                amountDelta = d.amount,
                accountName = destinationAccount,
                projectId = projectId,
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "debt",
                entityId = d.id,
                title = "Create debt: ${d.name}",
                afterJson = helper.undoJson.encodeToString(DebtUndoBundle(debt = d, transactions = listOf(t))),
                projectId = projectId,
            )
            helper.sync { setDebt(d); setTransaction(t) }
        }
        helper.syncAccountByName(destinationAccount)
    }

    suspend fun updateDebt(debt: Debt) {
        val existingDestination = ctx.dao.getTransactionsByRef(debt.id)
            .firstOrNull { it.category == "Debt Received" && it.type.equals("Income", ignoreCase = true) }
            ?.toAcct.orEmpty()
        updateDebtWithDestination(debt, existingDestination)
    }

    suspend fun updateDebtWithDestination(debt: Debt, requestedDestinationAccount: String) {
        helper.requireOpenDate(debt.date, debt.projectId)
        var destinationAccount = ""
        var oldDestinationAccount = ""
        var updatedFundingTxn: Transaction?= null
        var debtPaymentsNeedingReview: List<DebtPayment> = emptyList()
        val before = ctx.dao.getDebtById(debt.id)
        val now = System.currentTimeMillis()
        val d = debt.copy(updatedAt = now)
        ctx.db.withTransaction {
            val fundingTxn = ctx.dao.getTransactionsByRef(d.id)
                .firstOrNull { it.category == "Debt Received" && it.type.equals("Income", ignoreCase = true) }
            oldDestinationAccount = fundingTxn?.toAcct.orEmpty()
            destinationAccount = requestedDestinationAccount.ifBlank { oldDestinationAccount }
            val destinationAccountId = ctx.dao.getAccountByName(destinationAccount)?.id ?: ""
            ctx.dao.insertDebt(d)
            if (fundingTxn != null) {
                if (before != null) {
                    helper.applyAccountDelta(fundingTxn.toAcctId, fundingTxn.toAcct, -fundingTxn.amount)
                    helper.applyAccountDelta(destinationAccountId, destinationAccount, d.amount)
                }
                val fundingUpdate = fundingTxn.copy(
                    date = d.date,
                    amount = d.amount,
                    toAcct = destinationAccount,
                    toAcctId = destinationAccountId,
                    category = "Debt Received",
                    desc = "Loan received from ${d.name}",
                    notes = d.notes,
                    projectId = d.projectId,
                    updatedAt = now,
                )
                updatedFundingTxn = fundingUpdate
                ctx.dao.insertTransaction(fundingUpdate)
            }
            helper.recordAudit(
                action = "EDIT",
                entityType = "debt",
                entityId = d.id,
                title = "Debt edited: ${d.name}",
                beforeValue = before?.let { "${it.name}:${it.amount}:paid=${it.paid}:status=${it.status}" } ?: "",
                afterValue = "${d.name}:${d.amount}:paid=${d.paid}:status=${d.status}:to=$destinationAccount",
                amountDelta = d.amount - (before?.amount ?: d.amount),
                accountName = destinationAccount,
                projectId = d.projectId,
            )
            if (before != null && before.date != d.date) {
                debtPaymentsNeedingReview = ctx.dao.getDebtPaymentsForDebtOnce(d.id)
                    .filter { payment ->
                        val interest = app.fynlo.logic.InterestPolicy.debtPaymentInterestAmount(payment)
                        app.fynlo.logic.InterestPolicy.isStaleCurrentPeriodInterest(
                            allocationType = payment.interestAllocationType,
                            interestAmount = interest,
                            periodStartDate = payment.interestPeriodStartDate,
                            currentStartDate = d.date,
                        )
                    }
                    .map { payment ->
                        payment.copy(
                            interestAllocationType = app.fynlo.logic.InterestPolicy.UNKNOWN_REVIEW,
                            updatedAt = now,
                        )
                    }
                debtPaymentsNeedingReview.forEach { ctx.dao.insertDebtPayment(it) }
            }
            ctx.dao.rebuildDebtPaidFromDebtPayments()
        }
        val updatedDebt = ctx.dao.getDebtById(d.id) ?: d
        helper.sync {
            setDebt(updatedDebt)
            updatedFundingTxn?.let { setTransaction(it) }
            debtPaymentsNeedingReview.forEach { setDebtPayment(it) }
        }
        if (oldDestinationAccount.isNotBlank()) helper.syncAccountByName(oldDestinationAccount)
        if (destinationAccount.isNotBlank()) helper.syncAccountByName(destinationAccount)
    }

    suspend fun deleteDebt(debt: Debt) {
        helper.requireOpenDate(debt.date, debt.projectId)
        var linkedTxns = emptyList<Transaction>()
        var linkedPayments = emptyList<DebtPayment>()
        var didDelete = false
        ctx.db.withTransaction {
            val current = ctx.dao.getDebtById(debt.id) ?: return@withTransaction
            linkedTxns = ctx.dao.getTransactionsByRef(current.id)
            linkedTxns.forEach { txn ->
                when (txn.type.lowercase()) {
                    "expense"  -> helper.applyAccountDelta(txn.fromAcctId, txn.fromAcct,  txn.amount)
                    "income"   -> helper.applyAccountDelta(txn.toAcctId,   txn.toAcct,   -txn.amount)
                    "transfer" -> {
                        helper.applyAccountDelta(txn.fromAcctId, txn.fromAcct,  txn.amount)
                        helper.applyAccountDelta(txn.toAcctId,   txn.toAcct,   -txn.amount)
                    }
                }
                ctx.dao.deleteTransaction(txn)
                helper.tombstoneRemoteDoc("transactions", txn.id)
            }
            linkedPayments = ctx.dao.getDebtPaymentsForDebtOnce(current.id)
            linkedPayments.forEach { p ->
                ctx.dao.deleteDebtPayment(p)
                helper.tombstoneRemoteDoc("debt_payments", p.id)
            }
            helper.recordUndo(
                action = "DELETE",
                entityType = "debt",
                entityId = current.id,
                title = "Delete debt: ${current.name}",
                beforeJson = helper.undoJson.encodeToString(
                    DebtUndoBundle(
                        debt = current,
                        transactions = linkedTxns,
                        payments = linkedPayments,
                    )
                ),
                projectId = current.projectId,
            )
            ctx.dao.deleteDebt(current)
            helper.tombstoneRemoteDoc("debts", current.id)
            helper.recordAudit(
                action = "DELETE",
                entityType = "debt",
                entityId = current.id,
                title = "Debt deleted: ${current.name}",
                beforeValue = "borrowed=${current.amount}:paid=${current.paid}",
                amountDelta = -current.amount,
                accountName = linkedTxns.firstOrNull { it.type.equals("Income", ignoreCase = true) }?.toAcct ?: "",
                projectId = current.projectId,
            )
            didDelete = true
        }
        if (!didDelete) return
        linkedTxns.forEach { helper.sync { deleteTransaction(it.id) } }
        linkedPayments.forEach { helper.sync { deleteDebtPayment(it.id) } }
        helper.sync { deleteDebt(debt.id) }
        linkedTxns.map { it.fromAcct }.filter { it.isNotBlank() }.distinct().forEach { helper.syncAccountByName(it) }
        linkedTxns.map { it.toAcct }.filter { it.isNotBlank() }.distinct().forEach { helper.syncAccountByName(it) }
    }

    suspend fun insertDebtPaymentWithSource(payment: DebtPayment, sourceAccount: String, projectId: String = payment.projectId) {
        helper.requireOpenDate(payment.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getDebtPaymentById(payment.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val debtBefore = ctx.dao.getDebtById(payment.debtId)
            val p = payment.copy(projectId = projectId, updatedAt = now, createdAt = if (payment.createdAt == 0L) now else payment.createdAt)
            ctx.dao.insertDebtPayment(p)

            ctx.dao.updateAccountBalance(sourceAccount, -payment.amount)
            ctx.dao.rebuildDebtPaidFromDebtPayments()

            val interestPaid = payment.interest.coerceAtLeast(0.0)

            val t = Transaction(
                id = app.fynlo.logic.Ids.newId(),
                date = payment.date,
                type = "Expense",
                amount = payment.amount,
                fromAcct = sourceAccount,
                category = "Debt Repayment",
                desc = "EMI/payment for ${payment.name}",
                ref = payment.debtId,
                notes = payment.notes,
                projectId = projectId,
                updatedAt = now,
                createdAt = now
            )
            ctx.dao.insertTransaction(t)
            val paymentTransactions = mutableListOf(t)

            if (interestPaid > 0.01) {
                val intTxn = Transaction(
                    id = app.fynlo.logic.Ids.newId(),
                    date = payment.date,
                    type = "Expense",
                    amount = interestPaid,
                    fromAcct = sourceAccount,
                    category = "Interest Expense",
                    desc = "Interest paid on ${payment.name}",
                    ref = payment.debtId,
                    notes = "Auto-split from debt payment",
                    projectId = projectId,
                    updatedAt = now,
                    createdAt = now
                )
                val journalTxn = intTxn.copy(tags = "journal_only")
                ctx.dao.insertTransaction(journalTxn)
                paymentTransactions += journalTxn
                helper.sync { setTransaction(journalTxn) }
            }

            val updatedDebt = ctx.dao.getDebtById(payment.debtId)
            helper.recordAudit(
                action = "PAYMENT",
                entityType = "debt",
                entityId = payment.debtId,
                title = "Debt payment made: ${payment.name}",
                afterValue = "amount=${p.amount}:from=$sourceAccount:payment=${p.id}:txn=${t.id}:interest=$interestPaid",
                amountDelta = -p.amount,
                accountName = sourceAccount,
                projectId = projectId,
                reason = "Interest payments are tracked separately; debt date remains the accrual start.",
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "debt_payment",
                entityId = p.id,
                title = "Debt payment: ${p.name}",
                afterJson = helper.undoJson.encodeToString(
                    DebtPaymentUndoBundle(payment = p, transactions = paymentTransactions, debtBefore = debtBefore)
                ),
                projectId = projectId,
            )
            helper.sync {
                setDebtPayment(p)
                setTransaction(t)
                updatedDebt?.let { setDebt(it) }
            }
        }
        helper.syncAccountByName(sourceAccount)
    }

    suspend fun waiveDebtInterest(debt: Debt, amount: Double, reason: String) {
        helper.requireOpenDate(
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            debt.projectId,
        )
        val targetId = debt.id
        var updated: Debt?= null
        ctx.db.withTransaction {
            val current = ctx.dao.getDebtById(targetId) ?: return@withTransaction
            val remainingInterest = app.fynlo.logic.InterestPolicy.debtInterestOutstanding(current)
            val waiver = amount.coerceIn(0.0, remainingInterest)
            if (waiver <= 0.0) return@withTransaction

            val next = current.copy(
                interestWaived = current.interestWaived + waiver,
                updatedAt = System.currentTimeMillis(),
            )
            ctx.dao.insertDebt(next)
            helper.recordAudit(
                action = "WAIVE_INTEREST",
                entityType = "debt",
                entityId = next.id,
                title = "Debt interest waived: ${next.name}",
                beforeValue = "waived=${current.interestWaived}:interestOutstanding=$remainingInterest",
                afterValue = "waived=${next.interestWaived}:interestOutstanding=${(remainingInterest - waiver).coerceAtLeast(0.0)}",
                amountDelta = 0.0,
                projectId = next.projectId,
                reason = reason.ifBlank { "Interest grace/waiver" },
            )
            updated = next
        }
        updated?.let { helper.sync { setDebt(it) } }
    }
}
