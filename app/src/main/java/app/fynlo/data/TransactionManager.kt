package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.Transaction
import app.fynlo.data.model.Payment
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.RecurringTransaction
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.logic.resolveAccountIdsWith
import kotlinx.serialization.encodeToString

internal class TransactionManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper
) {
    private fun Transaction.auditSummary(): String =
        "${type}:${category}:${amount}:${fromAcct}->${toAcct}:${date}:${desc}"

    private suspend fun Transaction.withResolvedAccountIds(): Transaction {
        val fromId = if (fromAcct.isEmpty()) null else ctx.dao.getAccountByName(fromAcct)?.id
        val toId   = if (toAcct.isEmpty())   null else ctx.dao.getAccountByName(toAcct)?.id
        return resolveAccountIdsWith { name ->
            when (name) {
                fromAcct -> fromId
                toAcct   -> toId
                else     -> null
            }
        }
    }

    suspend fun insertTransaction(transaction: Transaction) {
        val sanitized = TransactionValidator.sanitize(transaction).withResolvedAccountIds()
        helper.requireOpenDate(sanitized.date, sanitized.projectId)
        val affectedAccounts = mutableListOf<String>()
        ctx.db.withTransaction {
            if (ctx.dao.getTransactionById(sanitized.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val t = sanitized.copy(updatedAt = now, createdAt = if (sanitized.createdAt == 0L) now else sanitized.createdAt)
            ctx.dao.insertTransaction(t)
            
            val srcTag = app.fynlo.logic.BalanceAuditLog.Source.MANUAL_TXN
            val note   = "Add ${transaction.type.lowercase()} \"${transaction.desc.take(28)}\""
            
            when (transaction.type.lowercase()) {
                "expense"  -> {
                    helper.applyAccountDelta(t.fromAcctId, t.fromAcct, -t.amount)
                    affectedAccounts += t.fromAcct
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.fromAcct, -t.amount, note)
                }
                "income"   -> {
                    helper.applyAccountDelta(t.toAcctId, t.toAcct, t.amount)
                    affectedAccounts += t.toAcct
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.toAcct, t.amount, note)
                }
                "transfer" -> {
                    helper.applyAccountDelta(t.fromAcctId, t.fromAcct, -t.amount)
                    helper.applyAccountDelta(t.toAcctId,   t.toAcct,    t.amount)
                    affectedAccounts += t.fromAcct
                    affectedAccounts += t.toAcct
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.fromAcct, -t.amount, "$note (out)")
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.toAcct,    t.amount, "$note (in)")
                }
            }
            helper.recordAudit(
                action = "CREATE",
                entityType = "transaction",
                entityId = t.id,
                title = "Transaction added: ${t.category}",
                afterValue = t.auditSummary(),
                amountDelta = when (t.type.lowercase()) {
                    "expense" -> -t.amount
                    "income" -> t.amount
                    else -> 0.0
                },
                accountName = listOf(t.fromAcct, t.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                projectId = t.projectId,
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "transaction",
                entityId = t.id,
                title = "Undo added ${t.category}",
                afterJson = helper.undoJson.encodeToString(t),
                projectId = t.projectId,
            )
            helper.sync { setTransaction(t) }
        }
        Analytics.transactionAdded(type = transaction.type, category = transaction.category)
        affectedAccounts.forEach { helper.syncAccountByName(it) }
    }

    suspend fun editTransaction(old: Transaction, newRaw: Transaction) {
        if (old.isGeneratedJournalEntry()) return
        val new = TransactionValidator.sanitize(newRaw).withResolvedAccountIds()
        helper.requireOpenDate(old.date, old.projectId)
        helper.requireOpenDate(new.date, new.projectId)
        ctx.db.withTransaction {
            val revTag = app.fynlo.logic.BalanceAuditLog.Source.EDIT_TXN_REVERSE
            val appTag = app.fynlo.logic.BalanceAuditLog.Source.EDIT_TXN_APPLY
            val oldDesc = old.desc.take(28)
            val newDesc = new.desc.take(28)

            when (old.type.lowercase()) {
                "expense"  -> {
                    helper.applyAccountDelta(old.fromAcctId, old.fromAcct,  old.amount)
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.fromAcct,  old.amount, "Reverse old expense \"$oldDesc\"")
                }
                "income"   -> {
                    helper.applyAccountDelta(old.toAcctId, old.toAcct,   -old.amount)
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.toAcct,   -old.amount, "Reverse old income \"$oldDesc\"")
                }
                "transfer" -> {
                    helper.applyAccountDelta(old.fromAcctId, old.fromAcct,  old.amount)
                    helper.applyAccountDelta(old.toAcctId,   old.toAcct,   -old.amount)
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.fromAcct,  old.amount, "Reverse old transfer out \"$oldDesc\"")
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.toAcct,   -old.amount, "Reverse old transfer in \"$oldDesc\"")
                }
            }

            when (new.type.lowercase()) {
                "expense"  -> {
                    helper.applyAccountDelta(new.fromAcctId, new.fromAcct, -new.amount)
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.fromAcct, -new.amount, "Apply new expense \"$newDesc\"")
                }
                "income"   -> {
                    helper.applyAccountDelta(new.toAcctId, new.toAcct,    new.amount)
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.toAcct,    new.amount, "Apply new income \"$newDesc\"")
                }
                "transfer" -> {
                    helper.applyAccountDelta(new.fromAcctId, new.fromAcct, -new.amount)
                    helper.applyAccountDelta(new.toAcctId,   new.toAcct,    new.amount)
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.fromAcct, -new.amount, "Apply new transfer out \"$newDesc\"")
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.toAcct,    new.amount, "Apply new transfer in \"$newDesc\"")
                }
            }

            val touchedBorrowers = mutableSetOf<String>()
            val touchedDebts     = mutableSetOf<String>()

            if (old.category == "Loan Repayment" && old.ref.isNotBlank()) {
                val matching = ctx.dao.getPaymentsForLoanOnce(old.ref)
                    .filter { it.amount == old.amount && it.date == old.date }
                    .maxByOrNull { it.updatedAt }
                if (matching != null) {
                    ctx.dao.deletePayment(matching)
                    helper.tombstoneRemoteDoc("payments", matching.id)
                    helper.sync { deletePayment(matching.id) }
                }
                touchedBorrowers += old.ref
            }
            if (new.category == "Loan Repayment" && new.ref.isNotBlank()) {
                val borrower = ctx.dao.getBorrowerById(new.ref)
                if (borrower != null) {
                    val now = System.currentTimeMillis()
                    val p = Payment(
                        id        = app.fynlo.logic.Ids.newId(),
                        loanId    = new.ref,
                        name      = borrower.name,
                        date      = new.date,
                        type      = "Both",
                        amount    = new.amount,
                        principal = 0.0,
                        interest  = 0.0,
                        mode      = "",
                        notes     = new.notes,
                        projectId = new.projectId,
                        updatedAt = now,
                        createdAt = now,
                    )
                    ctx.dao.insertPayment(p)
                    helper.sync { setPayment(p) }
                }
                touchedBorrowers += new.ref
            }
            if (old.category == "Debt Repayment" && old.ref.isNotBlank()) {
                val matching = ctx.dao.getDebtPaymentsForDebtOnce(old.ref)
                    .filter { it.amount == old.amount && it.date == old.date }
                    .maxByOrNull { it.updatedAt }
                if (matching != null) {
                    ctx.dao.deleteDebtPayment(matching)
                    helper.tombstoneRemoteDoc("debt_payments", matching.id)
                    helper.sync { deleteDebtPayment(matching.id) }
                }
                touchedDebts += old.ref
            }
            if (new.category == "Debt Repayment" && new.ref.isNotBlank()) {
                val debt = ctx.dao.getDebtById(new.ref)
                if (debt != null) {
                    val now = System.currentTimeMillis()
                    val p = DebtPayment(
                        id        = app.fynlo.logic.Ids.newId(),
                        debtId    = new.ref,
                        name      = debt.name,
                        date      = new.date,
                        type      = "Both",
                        amount    = new.amount,
                        principal = 0.0,
                        interest  = 0.0,
                        mode      = "",
                        notes     = new.notes,
                        projectId = new.projectId,
                        updatedAt = now,
                        createdAt = now,
                    )
                    ctx.dao.insertDebtPayment(p)
                    helper.sync { setDebtPayment(p) }
                }
                touchedDebts += new.ref
            }
            if (touchedBorrowers.isNotEmpty()) {
                ctx.dao.rebuildBorrowerPaidFromPayments()
                touchedBorrowers.forEach { id ->
                    ctx.dao.getBorrowerById(id)?.let { b -> helper.sync { setBorrower(b) } }
                }
            }
            if (touchedDebts.isNotEmpty()) {
                ctx.dao.rebuildDebtPaidFromDebtPayments()
                touchedDebts.forEach { id ->
                    ctx.dao.getDebtById(id)?.let { d -> helper.sync { setDebt(d) } }
                }
            }

            ctx.dao.insertTransaction(new)
            helper.recordAudit(
                action = "EDIT",
                entityType = "transaction",
                entityId = new.id,
                title = "Transaction edited: ${new.category}",
                beforeValue = old.auditSummary(),
                afterValue = new.auditSummary(),
                amountDelta = when (new.type.lowercase()) {
                    "expense" -> -new.amount
                    "income" -> new.amount
                    else -> 0.0
                } - when (old.type.lowercase()) {
                    "expense" -> -old.amount
                    "income" -> old.amount
                    else -> 0.0
                },
                accountName = listOf(new.fromAcct, new.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                projectId = new.projectId,
            )
            helper.recordUndo(
                action = "EDIT",
                entityType = "transaction",
                entityId = new.id,
                title = "Undo edited ${new.category}",
                beforeJson = helper.undoJson.encodeToString(old),
                afterJson = helper.undoJson.encodeToString(new),
                projectId = new.projectId,
            )
            helper.sync { setTransaction(new) }
        }
        val affected = mutableSetOf<String>()
        if (old.fromAcct.isNotBlank()) affected.add(old.fromAcct)
        if (old.toAcct.isNotBlank())   affected.add(old.toAcct)
        if (new.fromAcct.isNotBlank()) affected.add(new.fromAcct)
        if (new.toAcct.isNotBlank())   affected.add(new.toAcct)
        affected.forEach { helper.syncAccountByName(it) }
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        helper.requireOpenDate(transaction.date, transaction.projectId)
        var deleted: Transaction?= null
        ctx.db.withTransaction {
            val current = ctx.dao.getTransactionById(transaction.id) ?: return@withTransaction
            if (current.isGeneratedJournalEntry()) return@withTransaction
            val delTag = app.fynlo.logic.BalanceAuditLog.Source.DELETE_TXN
            val delNote = "Delete ${current.type.lowercase()} \"${current.desc.take(28)}\""

            when (current.type.lowercase()) {
                "expense"  -> if (current.fromAcct.isNotBlank()) {
                    helper.applyAccountDelta(current.fromAcctId, current.fromAcct,  current.amount)
                    app.fynlo.logic.BalanceAuditLog.record(delTag, current.fromAcct,  current.amount, delNote)
                }
                "income"   -> if (current.toAcct.isNotBlank()) {
                    helper.applyAccountDelta(current.toAcctId, current.toAcct,   -current.amount)
                    app.fynlo.logic.BalanceAuditLog.record(delTag, current.toAcct,   -current.amount, delNote)
                }
                "transfer" -> {
                    if (current.fromAcct.isNotBlank()) {
                        helper.applyAccountDelta(current.fromAcctId, current.fromAcct,  current.amount)
                        app.fynlo.logic.BalanceAuditLog.record(delTag, current.fromAcct,  current.amount, "$delNote (out)")
                    }
                    if (current.toAcct.isNotBlank()) {
                        helper.applyAccountDelta(current.toAcctId, current.toAcct,   -current.amount)
                        app.fynlo.logic.BalanceAuditLog.record(delTag, current.toAcct,   -current.amount, "$delNote (in)")
                    }
                }
            }

            if (current.category == "Loan Repayment" && current.ref.isNotBlank()) {
                val matchingPayment = ctx.dao.getPaymentsForLoanOnce(current.ref)
                    .filter { it.amount == current.amount && it.date == current.date }
                    .maxByOrNull { it.updatedAt }
                if (matchingPayment != null) {
                    ctx.dao.deletePayment(matchingPayment)
                    helper.tombstoneRemoteDoc("payments", matchingPayment.id)
                    helper.sync { deletePayment(matchingPayment.id) }
                }
                ctx.dao.rebuildBorrowerPaidFromPayments()
                val b = ctx.dao.getBorrowerById(current.ref)
                helper.sync { b?.let { setBorrower(it) } }
            } else if (current.category == "Debt Repayment" && current.ref.isNotBlank()) {
                val matchingPayment = ctx.dao.getDebtPaymentsForDebtOnce(current.ref)
                    .filter { it.amount == current.amount && it.date == current.date }
                    .maxByOrNull { it.updatedAt }
                if (matchingPayment != null) {
                    ctx.dao.deleteDebtPayment(matchingPayment)
                    helper.tombstoneRemoteDoc("debt_payments", matchingPayment.id)
                    helper.sync { deleteDebtPayment(matchingPayment.id) }
                }
                ctx.dao.rebuildDebtPaidFromDebtPayments()
                val d = ctx.dao.getDebtById(current.ref)
                helper.sync { d?.let { setDebt(it) } }
            }

            ctx.dao.deleteTransaction(current)
            helper.tombstoneRemoteDoc("transactions", current.id)
            helper.recordAudit(
                action = "DELETE",
                entityType = "transaction",
                entityId = current.id,
                title = "Transaction deleted: ${current.category}",
                beforeValue = current.auditSummary(),
                amountDelta = when (current.type.lowercase()) {
                    "expense" -> current.amount
                    "income" -> -current.amount
                    else -> 0.0
                },
                accountName = listOf(current.fromAcct, current.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                projectId = current.projectId,
            )
            helper.recordUndo(
                action = "DELETE",
                entityType = "transaction",
                entityId = current.id,
                title = "Undo deleted ${current.category}",
                beforeJson = helper.undoJson.encodeToString(current),
                projectId = current.projectId,
            )
            deleted = current
        }
        val current = deleted ?: return
        when (current.type.lowercase()) {
            "expense"  -> if (current.fromAcct.isNotBlank()) helper.syncAccountByName(current.fromAcct)
            "income"   -> if (current.toAcct.isNotBlank())   helper.syncAccountByName(current.toAcct)
            "transfer" -> {
                if (current.fromAcct.isNotBlank()) helper.syncAccountByName(current.fromAcct)
                if (current.toAcct.isNotBlank())   helper.syncAccountByName(current.toAcct)
            }
        }
        helper.sync { deleteTransaction(current.id) }
    }

    suspend fun insertRecurringTransaction(r: RecurringTransaction) {
        val fromId = if (r.fromAcct.isEmpty()) null else ctx.dao.getAccountByName(r.fromAcct)?.id
        val toId   = if (r.toAcct.isEmpty())   null else ctx.dao.getAccountByName(r.toAcct)?.id
        val resolved = r.resolveAccountIdsWith { name ->
            when (name) {
                r.fromAcct -> fromId
                r.toAcct   -> toId
                else       -> null
            }
        }
        val rec = resolved.copy(updatedAt = System.currentTimeMillis())
        ctx.dao.insertRecurringTransaction(rec); helper.sync { setRecurring(rec) }
    }

    suspend fun deleteRecurringTransaction(r: RecurringTransaction) {
        ctx.dao.deleteRecurringTransaction(r); helper.sync { deleteRecurring(r.id) }
    }
}
