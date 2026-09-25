package app.fynlo.data

import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@kotlinx.serialization.Serializable
internal data class LoanUndoBundle(
    val borrower: Borrower,
    val transactions: List<Transaction> = emptyList(),
    val payments: List<Payment> = emptyList(),
)

@kotlinx.serialization.Serializable
internal data class DebtUndoBundle(
    val debt: Debt,
    val transactions: List<Transaction> = emptyList(),
    val payments: List<DebtPayment> = emptyList(),
)

@kotlinx.serialization.Serializable
internal data class PaymentUndoBundle(
    val payment: Payment,
    val transactions: List<Transaction> = emptyList(),
    val borrowerBefore: Borrower?= null,
)

@kotlinx.serialization.Serializable
internal data class DebtPaymentUndoBundle(
    val payment: DebtPayment,
    val transactions: List<Transaction> = emptyList(),
    val debtBefore: Debt?= null,
)

@kotlinx.serialization.Serializable
internal data class InvestmentUndoBundle(
    val investment: Investment,
    val transactions: List<Transaction> = emptyList(),
    val linkedDebt: Debt?= null,
    val replayBalancesOnRestore: Boolean = true,
)

@kotlinx.serialization.Serializable
internal data class InvestmentEditUndoBundle(
    val before: Investment,
    val createdTransactions: List<Transaction> = emptyList(),
)

internal class RepositoryHelper(private val ctx: RepositoryContext) {

    val undoJson = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }

    class ClosedPeriodException(month: String) : IllegalStateException(
        "This period is closed ($month). Reopen the month before changing past entries."
    )

    fun sync(block: suspend FirestoreRepository.() -> Unit) {
        if (ctx.syncManager.userId.isEmpty()) return
        ctx.ioScope.launch {
            ctx.syncManager.setSyncing()
            runCatching { ctx.firestoreRepo.block() }
                .onFailure { e ->
                    android.util.Log.e("FynloSync", "Firestore write failed: ${e.message}")
                }
            ctx.syncManager.setSynced()
        }
    }

    suspend fun syncAccountByName(name: String) {
        if (name.isBlank()) return
        runCatching {
            kotlinx.coroutines.delay(200)
            val account = ctx.dao.getAccountByName(name) ?: return
            val uid     = ctx.syncManager.userId
            if (uid.isEmpty()) return

            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            if (account.id.isNotBlank()) {
                fs.collection("users")
                    .document(uid)
                    .collection("accounts")
                    .document(account.id)
                    .update("balance", account.balance, "updatedAt", System.currentTimeMillis())
                    .await()
            }
        }
    }

    suspend fun applyAccountDelta(idOrEmpty: String, nameFallback: String, delta: Double) {
        if (idOrEmpty.isNotEmpty()) {
            ctx.dao.updateAccountBalanceById(idOrEmpty, delta)
        } else if (nameFallback.isNotBlank()) {
            ctx.dao.updateAccountBalance(nameFallback, delta)
        }
    }

    suspend fun applyTransactionBalance(txn: Transaction) {
        if (txn.tags.contains("journal_only", ignoreCase = true)) return
        when (txn.type.lowercase()) {
            "expense" -> applyAccountDelta(txn.fromAcctId, txn.fromAcct, -txn.amount)
            "income" -> applyAccountDelta(txn.toAcctId, txn.toAcct, txn.amount)
            "transfer" -> {
                applyAccountDelta(txn.fromAcctId, txn.fromAcct, -txn.amount)
                applyAccountDelta(txn.toAcctId, txn.toAcct, txn.amount)
            }
        }
    }

    suspend fun reverseTransactionBalance(txn: Transaction) {
        if (txn.tags.contains("journal_only", ignoreCase = true)) return
        when (txn.type.lowercase()) {
            "expense" -> applyAccountDelta(txn.fromAcctId, txn.fromAcct, txn.amount)
            "income" -> applyAccountDelta(txn.toAcctId, txn.toAcct, -txn.amount)
            "transfer" -> {
                applyAccountDelta(txn.fromAcctId, txn.fromAcct, txn.amount)
                applyAccountDelta(txn.toAcctId, txn.toAcct, -txn.amount)
            }
        }
    }

    suspend fun syncTouchedAccounts(transactions: List<Transaction>) {
        transactions.flatMap { listOf(it.fromAcct, it.toAcct) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { syncAccountByName(it) }
    }

    suspend fun tombstoneRemoteDoc(collection: String, id: String) {
        if (id.isNotBlank()) {
            ctx.dao.insertDeletedRemoteDoc(DeletedRemoteDoc(collection, id))
        }
    }

    private fun monthKey(date: String): String = date.take(7)

    suspend fun requireOpenDate(date: String, projectId: String) {
        val month = monthKey(date)
        if (month.length == 7 && ctx.dao.getClosedMonth(projectId.ifBlank { "personal" }, month) != null) {
            throw ClosedPeriodException(month)
        }
    }

    suspend fun recordAudit(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        beforeValue: String = "",
        afterValue: String = "",
        amountDelta: Double = 0.0,
        accountName: String = "",
        projectId: String = "personal",
        reason: String = "",
    ) {
        val event = AuditEvent(
            id = app.fynlo.logic.Ids.newId(),
            timestamp = System.currentTimeMillis(),
            action = action,
            entityType = entityType,
            entityId = entityId,
            title = title.take(120),
            beforeValue = beforeValue.take(600),
            afterValue = afterValue.take(600),
            amountDelta = amountDelta,
            accountName = accountName.take(120),
            projectId = projectId.ifBlank { "personal" },
            reason = reason.take(300),
        )
        ctx.dao.insertAuditEvent(event)
        sync { setAuditEvent(event) }
    }

    suspend fun recordUndo(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        beforeJson: String = "",
        afterJson: String = "",
        projectId: String = "personal",
    ) {
        val now = System.currentTimeMillis()
        ctx.dao.pruneUndoActions(now)
        ctx.dao.insertUndoAction(
            UndoAction(
                id = app.fynlo.logic.Ids.newId(),
                action = action,
                entityType = entityType,
                entityId = entityId,
                title = title.take(120),
                beforeJson = beforeJson,
                afterJson = afterJson,
                projectId = projectId.ifBlank { "personal" },
                expiresAt = now + 10 * 60 * 1000L,
                updatedAt = now,
                createdAt = now,
            )
        )
    }

    suspend fun findOrCreatePersonId(name: String, phone: String, projectId: String): String {
        if (phone.isBlank()) return ""
        ctx.dao.getPersonByPhone(phone)?.let { return it.id }
        val now = System.currentTimeMillis()
        val newPerson = Person(
            id        = app.fynlo.logic.Ids.newId(),
            name      = name,
            phone     = phone,
            type      = "Individual",
            notes     = "",
            projectId = projectId,
            updatedAt = now,
            createdAt = now,
        )
        ctx.dao.insertPerson(newPerson)
        sync { setPerson(newPerson) }
        return newPerson.id
    }
}
