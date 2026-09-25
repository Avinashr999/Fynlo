package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.*
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.resolveAccountIdsWith
import kotlinx.serialization.decodeFromString

internal class AuditUndoManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper,
    private val transactionManager: TransactionManager,
    private val lendingManager: LendingManager,
    private val debtManager: DebtManager,
) {
    suspend fun closeMonth(projectId: String, month: String, note: String = "") {
        val now = System.currentTimeMillis()
        val close = MonthlyClose(
            id = "${projectId.ifBlank { "personal" }}:$month",
            projectId = projectId.ifBlank { "personal" },
            month = month,
            status = "Closed",
            note = note,
            closedAt = now,
            updatedAt = now,
            createdAt = now,
        )
        ctx.dao.insertMonthlyClose(close)
        helper.recordAudit("CLOSE", "monthly_close", close.id, "Month closed: $month", afterValue = note, projectId = close.projectId)
        helper.sync { setMonthlyClose(close) }
    }

    suspend fun reopenMonth(projectId: String, month: String, note: String = "") {
        val now = System.currentTimeMillis()
        val close = MonthlyClose(
            id = "${projectId.ifBlank { "personal" }}:$month",
            projectId = projectId.ifBlank { "personal" },
            month = month,
            status = "Reopened",
            note = note,
            closedAt = 0L,
            reopenedAt = now,
            updatedAt = now,
            createdAt = now,
        )
        ctx.dao.insertMonthlyClose(close)
        helper.recordAudit("REOPEN", "monthly_close", close.id, "Month reopened: $month", afterValue = note, projectId = close.projectId)
        helper.sync { setMonthlyClose(close) }
    }

    suspend fun addProofAttachment(attachment: ProofAttachment) {
        val now = System.currentTimeMillis()
        val saved = attachment.copy(updatedAt = now, createdAt = if (attachment.createdAt == 0L) now else attachment.createdAt)
        ctx.dao.insertProofAttachment(saved)
        helper.recordAudit("ATTACH", saved.ownerType, saved.ownerId, "Proof attached: ${saved.displayName}", afterValue = saved.localUri, projectId = saved.projectId)
        helper.sync { setProofAttachment(saved) }
    }

    suspend fun deleteProofAttachment(id: String) {
        ctx.dao.deleteProofAttachmentById(id)
        helper.sync { deleteProofAttachment(id) }
    }

    suspend fun resolveSyncConflict(id: String, resolution: String) {
        val now = System.currentTimeMillis()
        val conflict = ctx.dao.getSyncConflictById(id)
        if (conflict != null) {
            val selectedJson = when (resolution) {
                "KeepPhone" -> conflict.localJson
                "KeepCloud" -> conflict.remoteJson
                else -> ""
            }
            if (selectedJson.isNotBlank()) {
                applySyncConflictSnapshot(conflict, selectedJson, now)
            }
        }
        val label = when (resolution) {
            "KeepPhone" -> "Kept phone copy"
            "KeepCloud" -> "Kept cloud copy"
            else -> resolution
        }
        ctx.dao.resolveSyncConflict(id, label, now)
    }

    private suspend fun applySyncConflictSnapshot(conflict: SyncConflict, snapshotJson: String, now: Long) {
        when (conflict.collection) {
            "accounts" -> {
                val account = runCatching { helper.undoJson.decodeFromString<Account>(snapshotJson) }.getOrNull() ?: return
                val saved = account.copy(updatedAt = now)
                ctx.dao.insertAccount(saved)
                helper.recordAudit(
                    action = "SYNC_RESOLVE",
                    entityType = "account",
                    entityId = saved.id,
                    title = "Sync conflict resolved: ${saved.name}",
                    afterValue = "${saved.name} ${CurrencyFormatter.detail(saved.balance)}",
                    reason = "Applied ${conflict.collection} ${conflict.entityId}",
                    projectId = saved.projectId,
                )
                helper.sync { setAccount(saved) }
            }
            "transactions" -> {
                val txn = runCatching { helper.undoJson.decodeFromString<Transaction>(snapshotJson) }.getOrNull() ?: return
                // Need to resolve account IDs? The original code did:
                // val saved = txn.copy(updatedAt = now).withResolvedAccountIds()
                // For simplicity, we just insert it. Or better, we can manually resolve it:
                val fromId = if (txn.fromAcct.isEmpty()) null else ctx.dao.getAccountByName(txn.fromAcct)?.id
                val toId   = if (txn.toAcct.isEmpty())   null else ctx.dao.getAccountByName(txn.toAcct)?.id
                val saved = txn.resolveAccountIdsWith { name ->
                    when (name) {
                        txn.fromAcct -> fromId
                        txn.toAcct   -> toId
                        else         -> null
                    }
                }.copy(updatedAt = now)

                ctx.dao.insertTransaction(saved)
                // We'll skip the repairAccountBalanceDriftFromLedger call here or assume it runs later,
                // actually wait, original code called `repairAccountBalanceDriftFromLedger()`.
                // This means we might need a reference to MaintenanceManager.
                // Or we can just launch a side effect if needed, but since MaintenanceManager
                // would be cyclic, we can inject a provider or delegate to FinanceRepository.
                // Let's pass a repair callback if needed, or just leave it.
                // To keep it identical, let's pass a function:
                // We will add var onAccountDriftRepairNeeded: suspend () -> Unit = {}
                onAccountDriftRepairNeeded()

                helper.recordAudit(
                    action = "SYNC_RESOLVE",
                    entityType = "transaction",
                    entityId = saved.id,
                    title = "Sync conflict resolved: ${saved.desc.ifBlank { saved.category }}",
                    afterValue = "${saved.type} ${CurrencyFormatter.detail(saved.amount)}",
                    reason = "Applied ${conflict.collection} ${conflict.entityId}",
                    projectId = saved.projectId,
                )
                helper.sync { setTransaction(saved) }
            }
        }
    }

    var onAccountDriftRepairNeeded: suspend () -> Unit = {}

    suspend fun undoLastMoneyAction(): Boolean {
        val now = System.currentTimeMillis()
        val action = ctx.dao.getLatestUndoAction(now) ?: return false
        when (action.entityType) {
            "transaction" -> when (action.action) {
                "CREATE" -> ctx.dao.getTransactionById(action.entityId)?.let { transactionManager.deleteTransaction(it) }
                "DELETE" -> {
                    val restored = helper.undoJson.decodeFromString<Transaction>(action.beforeJson)
                    transactionManager.insertTransaction(restored.copy(updatedAt = now))
                }
                "EDIT" -> {
                    val before = helper.undoJson.decodeFromString<Transaction>(action.beforeJson)
                    val current = ctx.dao.getTransactionById(action.entityId) ?: before
                    transactionManager.editTransaction(current, before.copy(updatedAt = now))
                }
            }
            "loan" -> when (action.action) {
                "CREATE" -> ctx.dao.getBorrowerById(action.entityId)?.let { lendingManager.deleteBorrower(it) }
                "DELETE" -> restoreLoanBundle(helper.undoJson.decodeFromString(action.beforeJson))
                else -> return false
            }
            "debt" -> when (action.action) {
                "CREATE" -> ctx.dao.getDebtById(action.entityId)?.let { debtManager.deleteDebt(it) }
                "DELETE" -> restoreDebtBundle(helper.undoJson.decodeFromString(action.beforeJson))
                else -> return false
            }
            "payment" -> when (action.action) {
                "CREATE" -> undoPaymentCreate(helper.undoJson.decodeFromString(action.afterJson))
                else -> return false
            }
            "debt_payment" -> when (action.action) {
                "CREATE" -> undoDebtPaymentCreate(helper.undoJson.decodeFromString(action.afterJson))
                else -> return false
            }
            "investment" -> when (action.action) {
                "CREATE" -> undoInvestmentCreate(helper.undoJson.decodeFromString(action.afterJson))
                "DELETE" -> restoreInvestmentBundle(helper.undoJson.decodeFromString(action.beforeJson))
                "EDIT" -> undoInvestmentEdit(helper.undoJson.decodeFromString(action.beforeJson))
                else -> return false
            }
            else -> return false
        }
        ctx.dao.markUndoConsumed(action.id, now)
        helper.recordAudit("UNDO", action.entityType, action.entityId, "Undo: ${action.title}", projectId = action.projectId)
        return true
    }

    private suspend fun restoreLoanBundle(bundle: LoanUndoBundle) {
        ctx.db.withTransaction {
            ctx.dao.insertBorrower(bundle.borrower.copy(updatedAt = System.currentTimeMillis()))
            bundle.transactions.forEach {
                helper.applyTransactionBalance(it)
                ctx.dao.insertTransaction(it.copy(updatedAt = System.currentTimeMillis()))
            }
            bundle.payments.forEach { ctx.dao.insertPayment(it.copy(updatedAt = System.currentTimeMillis())) }
            ctx.dao.rebuildBorrowerPaidFromPayments()
        }
        helper.sync { setBorrower(bundle.borrower) }
        bundle.transactions.forEach { helper.sync { setTransaction(it) } }
        bundle.payments.forEach { helper.sync { setPayment(it) } }
        helper.syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun restoreDebtBundle(bundle: DebtUndoBundle) {
        ctx.db.withTransaction {
            ctx.dao.insertDebt(bundle.debt.copy(updatedAt = System.currentTimeMillis()))
            bundle.transactions.forEach {
                helper.applyTransactionBalance(it)
                ctx.dao.insertTransaction(it.copy(updatedAt = System.currentTimeMillis()))
            }
            bundle.payments.forEach { ctx.dao.insertDebtPayment(it.copy(updatedAt = System.currentTimeMillis())) }
            ctx.dao.rebuildDebtPaidFromDebtPayments()
        }
        helper.sync { setDebt(bundle.debt) }
        bundle.transactions.forEach { helper.sync { setTransaction(it) } }
        bundle.payments.forEach { helper.sync { setDebtPayment(it) } }
        helper.syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoPaymentCreate(bundle: PaymentUndoBundle) {
        ctx.db.withTransaction {
            bundle.transactions.forEach {
                helper.reverseTransactionBalance(it)
                ctx.dao.deleteTransaction(it)
                helper.tombstoneRemoteDoc("transactions", it.id)
            }
            ctx.dao.deletePayment(bundle.payment)
            helper.tombstoneRemoteDoc("payments", bundle.payment.id)
            ctx.dao.rebuildBorrowerPaidFromPayments()
            bundle.borrowerBefore?.let { ctx.dao.insertBorrower(it.copy(updatedAt = System.currentTimeMillis())) }
        }
        bundle.transactions.forEach { helper.sync { deleteTransaction(it.id) } }
        helper.sync { deletePayment(bundle.payment.id) }
        bundle.borrowerBefore?.let { helper.sync { setBorrower(it) } }
        helper.syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoDebtPaymentCreate(bundle: DebtPaymentUndoBundle) {
        ctx.db.withTransaction {
            bundle.transactions.forEach {
                helper.reverseTransactionBalance(it)
                ctx.dao.deleteTransaction(it)
                helper.tombstoneRemoteDoc("transactions", it.id)
            }
            ctx.dao.deleteDebtPayment(bundle.payment)
            helper.tombstoneRemoteDoc("debt_payments", bundle.payment.id)
            ctx.dao.rebuildDebtPaidFromDebtPayments()
            bundle.debtBefore?.let { ctx.dao.insertDebt(it.copy(updatedAt = System.currentTimeMillis())) }
        }
        bundle.transactions.forEach { helper.sync { deleteTransaction(it.id) } }
        helper.sync { deleteDebtPayment(bundle.payment.id) }
        bundle.debtBefore?.let { helper.sync { setDebt(it) } }
        helper.syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoInvestmentCreate(bundle: InvestmentUndoBundle) {
        ctx.db.withTransaction {
            bundle.transactions.forEach {
                helper.reverseTransactionBalance(it)
                ctx.dao.deleteTransaction(it)
                helper.tombstoneRemoteDoc("transactions", it.id)
            }
            bundle.linkedDebt?.let {
                ctx.dao.deleteDebt(it)
                helper.tombstoneRemoteDoc("debts", it.id)
            }
            ctx.dao.deleteInvestment(bundle.investment)
            helper.tombstoneRemoteDoc("investments", bundle.investment.id)
            ctx.dao.rebuildDebtPaidFromDebtPayments()
        }
        bundle.transactions.forEach { helper.sync { deleteTransaction(it.id) } }
        bundle.linkedDebt?.let { helper.sync { deleteDebt(it.id) } }
        helper.sync { deleteInvestment(bundle.investment.id) }
        helper.syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun restoreInvestmentBundle(bundle: InvestmentUndoBundle) {
        ctx.db.withTransaction {
            bundle.linkedDebt?.let { ctx.dao.insertDebt(it.copy(updatedAt = System.currentTimeMillis())) }
            ctx.dao.insertInvestment(bundle.investment.copy(updatedAt = System.currentTimeMillis()))
            bundle.transactions.forEach {
                if (bundle.replayBalancesOnRestore) helper.applyTransactionBalance(it)
                ctx.dao.insertTransaction(it.copy(updatedAt = System.currentTimeMillis()))
            }
            ctx.dao.rebuildDebtPaidFromDebtPayments()
        }
        bundle.linkedDebt?.let { helper.sync { setDebt(it) } }
        helper.sync { setInvestment(bundle.investment) }
        bundle.transactions.forEach { helper.sync { setTransaction(it) } }
        helper.syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoInvestmentEdit(bundle: InvestmentEditUndoBundle) {
        ctx.db.withTransaction {
            bundle.createdTransactions.forEach {
                helper.reverseTransactionBalance(it)
                ctx.dao.deleteTransaction(it)
                helper.tombstoneRemoteDoc("transactions", it.id)
            }
            ctx.dao.insertInvestment(bundle.before.copy(updatedAt = System.currentTimeMillis()))
        }
        bundle.createdTransactions.forEach { helper.sync { deleteTransaction(it.id) } }
        helper.sync { setInvestment(bundle.before) }
        helper.syncTouchedAccounts(bundle.createdTransactions)
    }
}
