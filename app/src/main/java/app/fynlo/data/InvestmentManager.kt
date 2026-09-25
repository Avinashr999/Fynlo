package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Transaction
import app.fynlo.data.model.InvestmentValuation
import app.fynlo.logic.CurrencyFormatter
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.first

internal class InvestmentManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper
) {
    fun isInvestmentJournalTrace(txn: Transaction): Boolean =
        txn.category.equals("Investment", ignoreCase = true) &&
            txn.type.equals("Info", ignoreCase = true) &&
            txn.tags.split(",").any { it.trim().equals("journal_only", ignoreCase = true) }

    private fun addTag(existing: String, tag: String): String =
        (existing.split(",") + tag)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(",")

    private fun removeTag(existing: String, tag: String): String =
        existing.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
            .joinToString(",")

    suspend fun insertInvestmentFundedByAccount(investment: Investment, accountName: String, projectId: String = investment.projectId, accountId: String = "") {
        helper.requireOpenDate(investment.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getInvestmentById(investment.id) != null) return@withTransaction
            val resolvedAccountId = accountId.ifBlank { ctx.dao.getAccountByName(accountName)?.id ?: "" }
            val i = investment.copy(sourceType = "account", fundingSource = accountName, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            ctx.dao.insertInvestment(i)
            helper.applyAccountDelta(resolvedAccountId, accountName, -investment.invested)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Expense", investment.invested,
                fromAcct = accountName, fromAcctId = resolvedAccountId, category = "Investment",
                desc = "Invested in ${investment.name}", ref = i.id,
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            ctx.dao.insertTransaction(t)
            helper.recordAudit(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Investment created: ${i.name}",
                afterValue = "invested=${i.invested}:current=${i.currentVal}:fundedBy=$accountName:txn=${t.id}",
                amountDelta = -i.invested,
                accountName = accountName,
                projectId = projectId,
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Create investment: ${i.name}",
                afterJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(investment = i, transactions = listOf(t))
                ),
                projectId = projectId,
            )
            helper.sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
        helper.syncAccountByName(accountName)
    }

    suspend fun insertInvestmentFundedByExistingDebt(investment: Investment, debt: Debt, projectId: String = investment.projectId) {
        helper.requireOpenDate(investment.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getInvestmentById(investment.id) != null) return@withTransaction
            val i = investment.copy(sourceType = "existing_debt", fundingSource = debt.name, linkedDebtId = debt.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            ctx.dao.insertInvestment(i)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Info", investment.invested,
                category = "Investment",
                desc = "Invested in ${investment.name} using ${debt.name} loan funds",
                ref = i.id, notes = investment.notes, tags = "journal_only",
                projectId = projectId, updatedAt = System.currentTimeMillis())
            ctx.dao.insertTransaction(t)
            helper.recordAudit(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Investment created from debt: ${i.name}",
                afterValue = "invested=${i.invested}:debt=${debt.name}:debtId=${debt.id}:txn=${t.id}",
                amountDelta = i.invested,
                accountName = debt.name,
                projectId = projectId,
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Create investment: ${i.name}",
                afterJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(investment = i, transactions = listOf(t))
                ),
                projectId = projectId,
            )
            helper.sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
    }

    suspend fun insertInvestmentFundedByNewLoan(investment: Investment, newDebt: Debt, projectId: String = investment.projectId) {
        helper.requireOpenDate(investment.date, projectId)
        helper.requireOpenDate(newDebt.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getInvestmentById(investment.id) != null) return@withTransaction
            val d = newDebt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            ctx.dao.insertDebt(d)
            val i = investment.copy(sourceType = "new_loan", fundingSource = d.name, linkedDebtId = d.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            ctx.dao.insertInvestment(i)
            val projectCurrency = ctx.dao.getProjectById(projectId)?.currency ?: "INR"
            val investedText = app.fynlo.logic.CurrencyFormatter.detail(investment.invested, projectCurrency)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Info", investment.invested,
                category = "Investment",
                desc = "Invested $investedText in ${investment.name} via ${d.name} loan",
                ref = i.id, notes = investment.notes, tags = "journal_only",
                projectId = projectId, updatedAt = System.currentTimeMillis())
            ctx.dao.insertTransaction(t)
            helper.recordAudit(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Investment created from new loan: ${i.name}",
                afterValue = "invested=${i.invested}:newDebt=${d.name}:debtId=${d.id}:txn=${t.id}",
                amountDelta = i.invested,
                accountName = d.name,
                projectId = projectId,
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Create investment: ${i.name}",
                afterJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(investment = i, transactions = listOf(t), linkedDebt = d)
                ),
                projectId = projectId,
            )
            helper.sync { setDebt(d); setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
    }

    suspend fun insertInvestmentWithSource(investment: Investment, sourceAccount: String, projectId: String = investment.projectId) {
        insertInvestmentFundedByAccount(investment, sourceAccount, projectId)
    }

    suspend fun deleteInvestmentOnly(investment: Investment) {
        helper.requireOpenDate(investment.date, investment.projectId)
        var didDelete = false
        ctx.db.withTransaction {
            val current = ctx.dao.getInvestmentById(investment.id) ?: return@withTransaction
            val invTxns = ctx.dao.getTransactionsByRef(current.id)
                .filter { it.category == "Investment" }
            helper.recordUndo(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Delete investment: ${current.name}",
                beforeJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = current,
                        transactions = invTxns,
                        replayBalancesOnRestore = false,
                    )
                ),
                projectId = current.projectId,
            )
            invTxns.forEach { invTxn ->
                ctx.dao.deleteTransaction(invTxn)
                helper.tombstoneRemoteDoc("transactions", invTxn.id)
                helper.sync { deleteTransaction(invTxn.id) }
            }
            ctx.dao.deleteInvestment(current)
            helper.tombstoneRemoteDoc("investments", current.id)
            helper.recordAudit(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Investment deleted: ${current.name}",
                beforeValue = "invested=${current.invested}:current=${current.currentVal}:source=${current.fundingSource}",
                amountDelta = -current.currentVal,
                accountName = current.fundingSource,
                projectId = current.projectId,
                reason = "Delete investment only",
            )
            didDelete = true
        }
        if (didDelete) helper.sync { deleteInvestment(investment.id) }
    }

    suspend fun deleteInvestmentAndReverseAccount(investment: Investment) {
        helper.requireOpenDate(investment.date, investment.projectId)
        var didDelete = false
        var fundingSourceToSync = ""
        ctx.db.withTransaction {
            val current = ctx.dao.getInvestmentById(investment.id) ?: return@withTransaction
            val invTxns = ctx.dao.getTransactionsByRef(current.id)
                .filter { it.category == "Investment" }
                .ifEmpty {
                    ctx.dao.getTransactionsByDesc("Invested in ${current.name}")
                        .filter { it.category == "Investment" && it.ref.isBlank() }
                        .take(1)
                }
            helper.recordUndo(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Delete investment: ${current.name}",
                beforeJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = current,
                        transactions = invTxns,
                        replayBalancesOnRestore = true,
                    )
                ),
                projectId = current.projectId,
            )
            invTxns.forEach { invTxn ->
                helper.applyAccountDelta(invTxn.fromAcctId, invTxn.fromAcct, invTxn.amount)
                fundingSourceToSync = invTxn.fromAcct
                ctx.dao.deleteTransaction(invTxn)
                helper.tombstoneRemoteDoc("transactions", invTxn.id)
                helper.sync { deleteTransaction(invTxn.id) }
            }
            ctx.dao.deleteInvestment(current)
            helper.tombstoneRemoteDoc("investments", current.id)
            helper.recordAudit(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Investment deleted and funding reversed: ${current.name}",
                beforeValue = "invested=${current.invested}:current=${current.currentVal}:source=${current.fundingSource}",
                amountDelta = current.invested,
                accountName = fundingSourceToSync.ifBlank { current.fundingSource },
                projectId = current.projectId,
                reason = "Investment funding reversed to source account",
            )
            didDelete = true
        }
        if (didDelete) {
            helper.sync { deleteInvestment(investment.id) }
            if (fundingSourceToSync.isNotBlank()) helper.syncAccountByName(fundingSourceToSync)
        }
    }

    suspend fun deleteInvestmentAndLinkedLoan(investment: Investment) {
        helper.requireOpenDate(investment.date, investment.projectId)
        var deletedInvestment: Investment?= null
        ctx.db.withTransaction {
            val current = ctx.dao.getInvestmentById(investment.id) ?: return@withTransaction
            val invTxns = ctx.dao.getTransactionsByRef(current.id)
                .filter { it.category == "Investment" }
            val linkedDebt = current.linkedDebtId.takeIf { it.isNotEmpty() }?.let { ctx.dao.getDebtById(it) }
            val linkedDebtTxns = current.linkedDebtId.takeIf { it.isNotEmpty() }
                ?.let { ctx.dao.getTransactionsByRef(it) }
                .orEmpty()
            helper.recordUndo(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Delete investment: ${current.name}",
                beforeJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = current,
                        transactions = invTxns + linkedDebtTxns,
                        linkedDebt = linkedDebt,
                        replayBalancesOnRestore = true,
                    )
                ),
                projectId = current.projectId,
            )
            invTxns.forEach { invTxn ->
                ctx.dao.deleteTransaction(invTxn)
                helper.tombstoneRemoteDoc("transactions", invTxn.id)
                helper.sync { deleteTransaction(invTxn.id) }
            }
            if (current.linkedDebtId.isNotEmpty()) {
                if (linkedDebt != null) {
                    val debtTxn = linkedDebtTxns
                        .firstOrNull { it.type.equals("Income", ignoreCase = true) }
                    if (debtTxn != null) {
                        helper.applyAccountDelta(debtTxn.toAcctId, debtTxn.toAcct, -linkedDebt.amount)
                        ctx.dao.deleteTransaction(debtTxn)
                        helper.tombstoneRemoteDoc("transactions", debtTxn.id)
                        helper.sync { deleteTransaction(debtTxn.id) }
                    }
                }
                ctx.dao.deleteDebtById(current.linkedDebtId)
            }
            ctx.dao.deleteInvestment(current)
            helper.tombstoneRemoteDoc("investments", current.id)
            helper.recordAudit(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Investment and linked loan deleted: ${current.name}",
                beforeValue = "invested=${current.invested}:linkedDebt=${current.linkedDebtId}:source=${current.fundingSource}",
                amountDelta = -current.currentVal,
                accountName = current.fundingSource,
                projectId = current.projectId,
                reason = "Delete investment with linked debt",
            )
            deletedInvestment = current
        }
        val current = deletedInvestment ?: return
        helper.sync {
            if (current.linkedDebtId.isNotEmpty()) deleteDebt(current.linkedDebtId)
            deleteInvestment(current.id)
        }
    }

    suspend fun updateInvestmentValue(investment: Investment, newCurrentVal: Double) {
        val updated = investment.copy(currentVal = newCurrentVal, updatedAt = System.currentTimeMillis())
        ctx.dao.insertInvestment(updated)
        helper.sync { setInvestment(updated) }
    }

    suspend fun updateInvestment(investment: Investment) {
        val updated = investment.copy(updatedAt = System.currentTimeMillis())
        ctx.dao.insertInvestment(updated)
        helper.sync { setInvestment(updated) }
    }

    suspend fun updateInvestmentFundedByAccount(
        investment: Investment,
        accountName: String,
        projectId: String = investment.projectId,
        accountId: String = "",
    ) {
        helper.requireOpenDate(investment.date, projectId)
        val before = ctx.dao.getInvestmentById(investment.id)
        val now = System.currentTimeMillis()
        var oldSource = ""
        var newSource = accountName
        var updatedInvestment: Investment?= null
        var updatedFundingTxn: Transaction?= null
        ctx.db.withTransaction {
            val linkedTxns = ctx.dao.getTransactionsByRef(investment.id)
            val fundingTxn = linkedTxns
                .firstOrNull { it.category == "Investment" && it.type.equals("Expense", ignoreCase = true) }
            val journalTxn = linkedTxns
                .firstOrNull { isInvestmentJournalTrace(it) }
            oldSource = fundingTxn?.fromAcct ?: before?.fundingSource.orEmpty()
            newSource = accountName.ifBlank { oldSource }
            val resolvedAccountId = accountId.ifBlank { ctx.dao.getAccountByName(newSource)?.id ?: "" }
            val i = investment.copy(
                sourceType = "account",
                fundingSource = newSource,
                projectId = projectId,
                updatedAt = now,
                createdAt = if (investment.createdAt == 0L) before?.createdAt ?: now else investment.createdAt,
            )
            ctx.dao.insertInvestment(i)
            updatedInvestment = i
            if (before != null) {
                if (fundingTxn != null) {
                    helper.applyAccountDelta(fundingTxn.fromAcctId, fundingTxn.fromAcct, fundingTxn.amount)
                }
                helper.applyAccountDelta(resolvedAccountId, newSource, -i.invested)
                val fundingUpdate = (fundingTxn ?: journalTxn)?.copy(
                    date = i.date,
                    type = "Expense",
                    amount = i.invested,
                    fromAcct = newSource,
                    fromAcctId = resolvedAccountId,
                    toAcct = "",
                    toAcctId = "",
                    category = "Investment",
                    desc = "Invested in ${i.name}",
                    notes = i.notes,
                    tags = removeTag((fundingTxn ?: journalTxn)?.tags.orEmpty(), "journal_only"),
                    projectId = projectId,
                    updatedAt = now,
                ) ?: Transaction(
                    id = app.fynlo.logic.Ids.newId(),
                    date = i.date,
                    type = "Expense",
                    amount = i.invested,
                    fromAcct = newSource,
                    fromAcctId = resolvedAccountId,
                    category = "Investment",
                    desc = "Invested in ${i.name}",
                    ref = i.id,
                    notes = i.notes,
                    projectId = projectId,
                    updatedAt = now,
                    createdAt = now,
                )
                updatedFundingTxn = fundingUpdate
                ctx.dao.insertTransaction(fundingUpdate)
            }
            helper.recordAudit(
                action = "EDIT",
                entityType = "investment",
                entityId = i.id,
                title = "Investment edited: ${i.name}",
                beforeValue = before?.let { "${it.name}:${it.invested}:current=${it.currentVal}:source=${it.fundingSource}" } ?: "",
                afterValue = "${i.name}:${i.invested}:current=${i.currentVal}:source=${i.fundingSource}",
                amountDelta = i.invested - (before?.invested ?: i.invested),
                accountName = i.fundingSource,
                projectId = projectId,
            )
        }
        helper.sync {
            updatedInvestment?.let { setInvestment(it) }
            updatedFundingTxn?.let { setTransaction(it) }
        }
        if (oldSource.isNotBlank()) helper.syncAccountByName(oldSource)
        if (newSource.isNotBlank() && newSource != oldSource) helper.syncAccountByName(newSource)
    }

    suspend fun updateInvestmentFundedByExistingDebt(
        investment: Investment,
        debt: Debt,
        projectId: String = investment.projectId,
    ) {
        val before = ctx.dao.getInvestmentById(investment.id)
        val now = System.currentTimeMillis()
        val remoteTransactions = mutableListOf<Transaction>()
        val touchedAccounts = mutableSetOf<String>()
        var updatedInvestment: Investment?= null
        ctx.db.withTransaction {
            val linkedTxns = ctx.dao.getTransactionsByRef(investment.id)
            val oldFundingTxn = linkedTxns.firstOrNull {
                it.category == "Investment" && it.type.equals("Expense", ignoreCase = true)
            }
            val journalTxn = linkedTxns.firstOrNull { isInvestmentJournalTrace(it) }

            if (oldFundingTxn != null) {
                helper.applyAccountDelta(oldFundingTxn.fromAcctId, oldFundingTxn.fromAcct, oldFundingTxn.amount)
                touchedAccounts += oldFundingTxn.fromAcct
            }

            val i = investment.copy(
                sourceType = "existing_debt",
                fundingSource = debt.name,
                linkedDebtId = debt.id,
                projectId = projectId,
                updatedAt = now,
                createdAt = if (investment.createdAt == 0L) before?.createdAt ?: now else investment.createdAt,
            )
            ctx.dao.insertInvestment(i)
            updatedInvestment = i

            val trace = (journalTxn ?: oldFundingTxn)?.copy(
                date = i.date,
                type = "Info",
                amount = i.invested,
                fromAcct = "",
                toAcct = "",
                fromAcctId = "",
                toAcctId = "",
                category = "Investment",
                desc = "Invested in ${i.name} using ${debt.name} loan funds",
                ref = i.id,
                notes = i.notes,
                tags = addTag((journalTxn ?: oldFundingTxn)?.tags.orEmpty(), "journal_only"),
                projectId = projectId,
                updatedAt = now,
            ) ?: Transaction(
                id = app.fynlo.logic.Ids.newId(),
                date = i.date,
                type = "Info",
                amount = i.invested,
                category = "Investment",
                desc = "Invested in ${i.name} using ${debt.name} loan funds",
                ref = i.id,
                notes = i.notes,
                tags = "journal_only",
                projectId = projectId,
                updatedAt = now,
                createdAt = now,
            )
            ctx.dao.insertTransaction(trace)
            remoteTransactions += trace
            helper.recordAudit(
                action = "EDIT",
                entityType = "investment",
                entityId = i.id,
                title = "Investment edited: ${i.name}",
                beforeValue = before?.let { "${it.name}:${it.invested}:current=${it.currentVal}:source=${it.fundingSource}" } ?: "",
                afterValue = "${i.name}:${i.invested}:current=${i.currentVal}:source=${i.fundingSource}",
                amountDelta = i.invested - (before?.invested ?: i.invested),
                accountName = i.fundingSource,
                projectId = projectId,
            )
        }
        helper.sync {
            updatedInvestment?.let { setInvestment(it) }
            remoteTransactions.forEach { setTransaction(it) }
        }
        touchedAccounts.filter { it.isNotBlank() }.forEach { helper.syncAccountByName(it) }
    }

    suspend fun executeLinkedInvestment(
        investment: Investment,
        fundingSourceType: String,
        sourceName: String,
        debtDetails: Debt?= null
    ) {
        helper.requireOpenDate(investment.date, investment.projectId)
        debtDetails?.let { helper.requireOpenDate(it.date, it.projectId.ifBlank { investment.projectId }) }
        val createdTransactions = mutableListOf<Transaction>()
        var createdDebt: Debt?= null
        ctx.db.withTransaction {
            val pid = investment.projectId
            
            ctx.dao.insertInvestment(investment)
            
            when (fundingSourceType) {
                "Account" -> {
                    ctx.dao.updateAccountBalance(sourceName, -investment.invested)
                    val t = Transaction(
                        id = app.fynlo.logic.Ids.newId(),
                        date = investment.date,
                        type = "Expense",
                        amount = investment.invested,
                        fromAcct = sourceName,
                        category = "Investment",
                        desc = "Invested in ${investment.name}",
                        ref = investment.id,
                        projectId = pid,
                        updatedAt = System.currentTimeMillis()
                    )
                    ctx.dao.insertTransaction(t)
                    createdTransactions += t
                    helper.sync { setTransaction(t) }
                    helper.syncAccountByName(sourceName)
                }
                "Debt" -> {
                    debtDetails?.let {
                        val d = it.copy(updatedAt = System.currentTimeMillis())
                        ctx.dao.insertDebt(d)
                        createdDebt = d
                        val t = Transaction(
                            id = app.fynlo.logic.Ids.newId(),
                            date = investment.date,
                            type = "Info",
                            amount = investment.invested,
                            category = "Debt",
                            desc = "Investment funded by loan from ${d.name}",
                            ref = d.id,
                            tags = "journal_only",
                            projectId = pid,
                            updatedAt = System.currentTimeMillis()
                        )
                        ctx.dao.insertTransaction(t)
                        createdTransactions += t
                        helper.sync { 
                            setDebt(d)
                            setTransaction(t) 
                        }
                    }
                }
                "Already Settled" -> {
                    val t = Transaction(
                        id = app.fynlo.logic.Ids.newId(),
                        date = investment.date,
                        type = "Info", 
                        amount = investment.invested,
                        category = "Historical Record",
                        desc = "Asset established long back",
                        ref = investment.id,
                        projectId = pid,
                        updatedAt = System.currentTimeMillis()
                    )
                    ctx.dao.insertTransaction(t)
                    createdTransactions += t
                    helper.sync { setTransaction(t) }
                }
            }
            
            val v = InvestmentValuation(
                id = app.fynlo.logic.Ids.newId(),
                investmentId = investment.id,
                date = investment.date,
                value = investment.invested,
                notes = "Initial purchase"
            )
            ctx.dao.insertValuation(v)
            
            helper.sync { 
                setInvestment(investment)
                setValuation(v)
            }
            helper.recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = investment.id,
                title = "Create investment: ${investment.name}",
                afterJson = helper.undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = investment,
                        transactions = createdTransactions,
                        linkedDebt = createdDebt,
                    )
                ),
                projectId = pid,
            )
        }
    }

    suspend fun addValuation(v: InvestmentValuation) {
        ctx.db.withTransaction {
            ctx.dao.insertValuation(v)
            val inv = ctx.dao.getAllInvestments().first().find { it.id == v.investmentId }
            inv?.let {
                val updated = it.copy(currentVal = v.value, updatedAt = System.currentTimeMillis())
                ctx.dao.insertInvestment(updated)
                helper.sync { setInvestment(updated) }
            }
            helper.sync { setValuation(v) }
        }
    }

    fun getValuationsForInvestment(invId: String) = ctx.dao.getValuationsForInvestment(invId)

    suspend fun withdrawFromInvestment(investment: Investment, withdrawAmount: Double, toAccount: String): Double {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        helper.requireOpenDate(today, investment.projectId)
        val proportionWithdrawn = if (investment.currentVal > 0)
            (withdrawAmount / investment.currentVal).coerceIn(0.0, 1.0) else 0.0
        val costBasis   = investment.invested * proportionWithdrawn
        val gainLoss    = withdrawAmount - costBasis

        val currencyCode = ctx.dao.getProjectById(investment.projectId)?.currency ?: "INR"

        ctx.db.withTransaction {
            val newCurrentVal  = (investment.currentVal  - withdrawAmount).coerceAtLeast(0.0)
            val newWithdrawn   = investment.withdrawn + withdrawAmount
            val newRealized    = investment.realized + gainLoss
            val updated = investment.copy(
                currentVal = newCurrentVal,
                withdrawn  = newWithdrawn,
                realized   = newRealized,
                updatedAt  = System.currentTimeMillis()
            )
            ctx.dao.insertInvestment(updated)

            ctx.dao.updateAccountBalance(toAccount, withdrawAmount)

            val t = Transaction(
                id        = app.fynlo.logic.Ids.newId(),
                date      = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                type      = "Income",
                amount    = withdrawAmount,
                toAcct    = toAccount,
                category  = "Investment Returns",
                desc      = "Withdrawal from ${investment.name}",
                ref       = investment.id,
                notes     = if (gainLoss >= 0) "Gain: ${CurrencyFormatter.detail(gainLoss, currencyCode)}"
                            else "Loss: ${CurrencyFormatter.detail(-gainLoss, currencyCode)}",
                projectId = investment.projectId,
                updatedAt = System.currentTimeMillis()
            )
            ctx.dao.insertTransaction(t)
            helper.recordUndo(
                action = "EDIT",
                entityType = "investment",
                entityId = investment.id,
                title = "Investment withdrawal: ${investment.name}",
                beforeJson = helper.undoJson.encodeToString(
                    InvestmentEditUndoBundle(before = investment, createdTransactions = listOf(t))
                ),
                projectId = investment.projectId,
            )
            helper.sync { setInvestment(updated); setTransaction(t) }
        }
        helper.syncAccountByName(toAccount)
        return gainLoss
    }

    suspend fun deleteInvestment(investment: Investment) {
        deleteInvestmentOnly(investment)
    }
}
