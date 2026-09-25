package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.Account
import app.fynlo.data.model.Transaction
import app.fynlo.logic.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class AccountManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper
) {
    suspend fun insertAccount(account: Account) {
        val now = System.currentTimeMillis()
        val a = account.copy(updatedAt = now, createdAt = if (account.createdAt == 0L) now else account.createdAt)
        ctx.dao.insertAccount(a)
        helper.recordAudit(
            action = "CREATE",
            entityType = "account",
            entityId = a.id,
            title = "Account created: ${a.name}",
            afterValue = "${a.name}:${a.type}:${a.balance}",
            amountDelta = a.balance,
            accountName = a.name,
            projectId = a.projectId,
        )
        helper.sync { setAccount(a) }
    }

    suspend fun upsertAccount(account: Account) {
        val before = ctx.dao.getAccountById(account.id)
        ctx.dao.insertAccount(account)
        helper.recordAudit(
            action = if (before == null) "CREATE" else "EDIT",
            entityType = "account",
            entityId = account.id,
            title = if (before == null) "Account created: ${account.name}" else "Account edited: ${account.name}",
            beforeValue = before?.let { "${it.name}:${it.type}:${it.balance}" } ?: "",
            afterValue = "${account.name}:${account.type}:${account.balance}",
            amountDelta = account.balance - (before?.balance ?: 0.0),
            accountName = account.name,
            projectId = account.projectId,
        )
        helper.sync { setAccount(account) }
    }

    suspend fun deleteUnusedAccount(account: Account) {
        ctx.dao.deleteAccountById(account.id)
        helper.recordAudit(
            action = "DELETE",
            entityType = "account",
            entityId = account.id,
            title = "Account deleted: ${account.name}",
            beforeValue = "${account.name}:${account.type}:${account.balance}",
            amountDelta = -account.balance,
            accountName = account.name,
            projectId = account.projectId,
        )
        helper.sync { deleteAccount(account.id) }
    }

    suspend fun quickEditBalance(
        accountName: String,
        newBalance: Double,
        oldBalance: Double,
        accountId: String = "",
    ) {
        ctx.db.withTransaction {
            val diff = newBalance - oldBalance
            helper.applyAccountDelta(accountId, accountName, diff)
            // 3.2.72 — manual balance edit audit entry.
            app.fynlo.logic.BalanceAuditLog.record(
                source  = app.fynlo.logic.BalanceAuditLog.Source.QUICK_EDIT_BALANCE,
                account = accountName,
                delta   = diff,
                note    = "Manual balance edit: $oldBalance → $newBalance",
            )
            val t = Transaction(
                id       = app.fynlo.logic.Ids.newId(),
                date     = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                type     = if (diff >= 0) "Income" else "Expense",
                amount   = Math.abs(diff),
                category = "Balance Correction",
                desc     = "Manual balance adjustment",
                toAcct   = if (diff >= 0) accountName else "",
                toAcctId = if (diff >= 0) accountId else "",
                fromAcct = if (diff < 0) accountName else "",
                fromAcctId = if (diff < 0) accountId else "",
                projectId = "personal",
                updatedAt = System.currentTimeMillis()
            )
            ctx.dao.insertTransaction(t)
            helper.recordAudit(
                action = "EDIT",
                entityType = "account",
                entityId = accountName,
                title = "Account balance corrected: $accountName",
                beforeValue = oldBalance.toString(),
                afterValue = newBalance.toString(),
                amountDelta = diff,
                accountName = accountName,
                projectId = t.projectId,
                reason = "Manual balance correction",
            )
            helper.sync { setTransaction(t) }
        }
        // Push updated account
        helper.syncAccountByName(accountName)
    }
}
