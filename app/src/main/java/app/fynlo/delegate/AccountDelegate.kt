package app.fynlo.delegate

import app.fynlo.data.model.Account
import app.fynlo.data.model.Transaction
import app.fynlo.data.Categories
import app.fynlo.logic.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountDelegate(private val ctx: ViewModelContext) {

    fun quickEditBalance(accountName: String, newBalance: Double, oldBalance: Double, accountId: String = "") {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.quickEditBalance(accountName, newBalance, oldBalance, accountId)
        }
    }

    fun saveAccountFromDialog(previous: Account?, account: Account) {
        ctx.scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (previous == null) {
                ctx.repository.upsertAccount(
                    account.copy(
                        projectId = ctx.currentProjectId(),
                        updatedAt = now,
                        createdAt = if (account.createdAt > 0L) account.createdAt else now,
                    )
                )
                return@launch
            }

            val metadataAccount = account.copy(
                balance = previous.balance,
                projectId = ctx.currentProjectId(),
                updatedAt = now,
                createdAt = if (account.createdAt > 0L) account.createdAt else previous.createdAt,
            )
            ctx.repository.upsertAccount(metadataAccount)

            if (kotlin.math.abs(account.balance - previous.balance) > 0.005) {
                ctx.repository.quickEditBalance(
                    accountName = account.name,
                    newBalance = account.balance,
                    oldBalance = previous.balance,
                    accountId = account.id,
                )
            }
        }
    }

    fun saveAccount(account: Account) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.upsertAccount(
                account.copy(
                    projectId = ctx.currentProjectId(),
                    updatedAt = System.currentTimeMillis(),
                    createdAt = if (account.createdAt > 0L) account.createdAt else System.currentTimeMillis(),
                )
            )
        }
    }

    fun transferBetweenAccounts(from: Account, to: Account, amount: Double) {
        if (amount <= 0.0 || from.id == to.id) return
        ctx.scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            ctx.repository.insertTransaction(
                Transaction(
                    id = Ids.newId(),
                    date = ctx.today(),
                    type = "Transfer",
                    amount = amount,
                    fromAcct = from.name,
                    toAcct = to.name,
                    fromAcctId = from.id,
                    toAcctId = to.id,
                    category = Categories.ACCOUNT_TRANSFER,
                    desc = "Transfer from ${from.name} to ${to.name}",
                    projectId = ctx.currentProjectId(),
                    updatedAt = now,
                    createdAt = now,
                )
            )
        }
    }

    fun closeAccount(account: Account) {
        if (kotlin.math.abs(account.balance) > 0.005) return
        val marker = "[fynlo:closed-account]"
        val notes = if (account.notes.contains(marker)) account.notes else listOf(account.notes, marker)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        saveAccount(account.copy(notes = notes))
    }

    fun saveAndCloseAccountFromDialog(previous: Account?, account: Account) {
        if (previous == null || kotlin.math.abs(account.balance) > 0.005) return
        ctx.scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val normalized = account.copy(
                projectId = ctx.currentProjectId(),
                updatedAt = now,
                createdAt = if (account.createdAt > 0L) account.createdAt else previous.createdAt,
            )
            val metadataAccount = normalized.copy(balance = previous.balance)
            ctx.repository.upsertAccount(metadataAccount)

            if (kotlin.math.abs(normalized.balance - previous.balance) > 0.005) {
                ctx.repository.quickEditBalance(
                    accountName = normalized.name,
                    newBalance = normalized.balance,
                    oldBalance = previous.balance,
                    accountId = normalized.id,
                )
            }

            val marker = "[fynlo:closed-account]"
            val notes = if (normalized.notes.contains(marker)) normalized.notes else listOf(normalized.notes, marker)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            ctx.repository.upsertAccount(normalized.copy(notes = notes, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteUnusedAccount(account: Account) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.deleteUnusedAccount(account)
        }
    }
}
