package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.Account
import app.fynlo.data.model.Transaction
import app.fynlo.data.model.AuditEvent
import kotlinx.coroutines.flow.first

internal class MaintenanceManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper,
    private val investmentManager: InvestmentManager,
) {
    suspend fun repairDeletedAuditResidue(): Int {
        val latestDeleteByEntity = ctx.dao.getAllAuditEventsOnce()
            .filter { it.action == "DELETE" && it.entityId.isNotBlank() }
            .groupBy { it.entityType to it.entityId }
            .mapValues { (_, events) -> events.maxOf { it.timestamp } }

        val deletedLoanIds = latestDeleteByEntity.keys
            .filter { it.first == "loan" }
            .map { it.second }
            .toSet()
        val deletedDebtIds = latestDeleteByEntity.keys
            .filter { it.first == "debt" }
            .map { it.second }
            .toSet()
        val deletedTransactionIds = latestDeleteByEntity.keys
            .filter { it.first == "transaction" }
            .map { it.second }
            .toSet()

        val remoteTransactions = mutableSetOf<String>()
        val remotePayments = mutableSetOf<String>()
        val remoteDebtPayments = mutableSetOf<String>()
        val remoteBorrowers = mutableSetOf<String>()
        val remoteDebts = mutableSetOf<String>()
        var repaired = 0

        ctx.db.withTransaction {
            deletedTransactionIds.forEach { id ->
                val deletedAt = latestDeleteByEntity["transaction" to id] ?: return@forEach
                val txn = ctx.dao.getTransactionById(id)
                if (txn != null && txn.updatedAt <= deletedAt) {
                    ctx.dao.deleteTransaction(txn)
                    repaired++
                }
                helper.tombstoneRemoteDoc("transactions", id)
                remoteTransactions += id
            }

            deletedLoanIds.forEach { id ->
                val deletedAt = latestDeleteByEntity["loan" to id] ?: return@forEach
                ctx.dao.getTransactionsByRef(id).forEach { txn ->
                    if (txn.updatedAt <= deletedAt) {
                        ctx.dao.deleteTransaction(txn)
                        helper.tombstoneRemoteDoc("transactions", txn.id)
                        remoteTransactions += txn.id
                        repaired++
                    }
                }
                ctx.dao.getPaymentsForLoanOnce(id).forEach { payment ->
                    if (payment.updatedAt <= deletedAt) {
                        ctx.dao.deletePayment(payment)
                        helper.tombstoneRemoteDoc("payments", payment.id)
                        remotePayments += payment.id
                        repaired++
                    }
                }
                ctx.dao.getBorrowerById(id)?.let { borrower ->
                    if (borrower.updatedAt <= deletedAt) {
                        ctx.dao.deleteBorrower(borrower)
                        repaired++
                    }
                }
                helper.tombstoneRemoteDoc("borrowers", id)
                remoteBorrowers += id
            }

            deletedDebtIds.forEach { id ->
                val deletedAt = latestDeleteByEntity["debt" to id] ?: return@forEach
                ctx.dao.getTransactionsByRef(id).forEach { txn ->
                    if (txn.updatedAt <= deletedAt) {
                        ctx.dao.deleteTransaction(txn)
                        helper.tombstoneRemoteDoc("transactions", txn.id)
                        remoteTransactions += txn.id
                        repaired++
                    }
                }
                ctx.dao.getDebtPaymentsForDebtOnce(id).forEach { payment ->
                    if (payment.updatedAt <= deletedAt) {
                        ctx.dao.deleteDebtPayment(payment)
                        helper.tombstoneRemoteDoc("debt_payments", payment.id)
                        remoteDebtPayments += payment.id
                        repaired++
                    }
                }
                ctx.dao.getDebtById(id)?.let { debt ->
                    if (debt.updatedAt <= deletedAt) {
                        ctx.dao.deleteDebt(debt)
                        repaired++
                    }
                }
                helper.tombstoneRemoteDoc("debts", id)
                remoteDebts += id
            }

            ctx.dao.rebuildBorrowerPaidFromPayments()
            ctx.dao.rebuildDebtPaidFromDebtPayments()
        }

        remoteTransactions.forEach { helper.sync { deleteTransaction(it) } }
        remotePayments.forEach { helper.sync { deletePayment(it) } }
        remoteDebtPayments.forEach { helper.sync { deleteDebtPayment(it) } }
        remoteBorrowers.forEach { helper.sync { deleteBorrower(it) } }
        remoteDebts.forEach { helper.sync { deleteDebt(it) } }
        return repaired
    }

    suspend fun repairDebtFundedInvestmentTransferTraces(): Int {
        val accounts = ctx.dao.getAllAccountsList()
        val accountNames = accounts.map { it.name }.toSet()
        val accountIds = accounts.map { it.id }.toSet()
        val investments = ctx.dao.getAllInvestments().first()
            .filter { it.sourceType in setOf("existing_debt", "new_loan") && it.linkedDebtId.isNotBlank() }
        val transactions = ctx.dao.getAllTransactionsList()
        val remoteTransactions = mutableListOf<Transaction>()
        val touchedAccounts = mutableSetOf<String>()
        var repaired = 0

        ctx.db.withTransaction {
            investments.forEach { investment ->
                val candidates = transactions.filter { txn ->
                    txn.category.equals("Investment", ignoreCase = true) &&
                        txn.type.equals("Transfer", ignoreCase = true) &&
                        (txn.ref == investment.id ||
                            (txn.desc.contains(investment.name, ignoreCase = true) &&
                                (investment.fundingSource.isBlank() ||
                                    txn.desc.contains(investment.fundingSource, ignoreCase = true))))
                }

                candidates.forEach { txn ->
                    if (txn.fromAcct in accountNames || txn.fromAcctId in accountIds) {
                        helper.applyAccountDelta(txn.fromAcctId, txn.fromAcct, txn.amount)
                        touchedAccounts += txn.fromAcct.takeIf { it.isNotBlank() }
                            ?: accounts.firstOrNull { it.id == txn.fromAcctId }?.name.orEmpty()
                    }
                    if (txn.toAcct in accountNames || txn.toAcctId in accountIds) {
                        helper.applyAccountDelta(txn.toAcctId, txn.toAcct, -txn.amount)
                        touchedAccounts += txn.toAcct.takeIf { it.isNotBlank() }
                            ?: accounts.firstOrNull { it.id == txn.toAcctId }?.name.orEmpty()
                    }
                    val repairedTags = (txn.tags.split(",") + "journal_only")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(",")
                    val repairedTxn = txn.copy(
                        type = "Info",
                        fromAcct = "",
                        toAcct = "",
                        fromAcctId = "",
                        toAcctId = "",
                        ref = investment.id,
                        tags = repairedTags,
                        updatedAt = System.currentTimeMillis(),
                    )
                    ctx.dao.insertTransaction(repairedTxn)
                    remoteTransactions += repairedTxn
                    helper.recordAudit(
                        action = "REPAIR",
                        entityType = "transaction",
                        entityId = repairedTxn.id,
                        title = "Debt-funded investment trace neutralized: ${investment.name}",
                        beforeValue = txn.desc,
                        afterValue = repairedTxn.desc,
                        amountDelta = 0.0,
                        accountName = listOf(txn.fromAcct, txn.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                        projectId = repairedTxn.projectId,
                        reason = "Debt-funded investment trace must not move account balances",
                    )
                    repaired++
                }
            }
        }

        remoteTransactions.forEach { txn -> helper.sync { setTransaction(txn) } }
        touchedAccounts.filter { it.isNotBlank() }.forEach { helper.syncAccountByName(it) }
        return repaired
    }

    suspend fun repairDebtFundedInvestmentJournalTraceRefs(): Int {
        val investments = ctx.dao.getAllInvestments().first()
            .filter { it.sourceType in setOf("existing_debt", "new_loan") && it.linkedDebtId.isNotBlank() }
        val remoteTransactions = mutableListOf<Transaction>()
        var repaired = 0

        ctx.db.withTransaction {
            val txCache = ctx.dao.getAllTransactionsList().toMutableList()

            fun replaceCached(updated: Transaction) {
                val index = txCache.indexOfFirst { it.id == updated.id }
                if (index >= 0) txCache[index] = updated else txCache += updated
            }

            fun hasExactTrace(investment: app.fynlo.data.model.Investment): Boolean =
                txCache.any { txn ->
                    txn.ref == investment.id &&
                        investmentManager.isInvestmentJournalTrace(txn) &&
                        kotlin.math.abs(txn.amount - investment.invested) <= 0.005
                }

            investments.forEach { investment ->
                val linked = txCache.filter { txn ->
                    txn.ref == investment.id && investmentManager.isInvestmentJournalTrace(txn)
                }
                val wrongLinked = linked.filter { txn ->
                    kotlin.math.abs(txn.amount - investment.invested) > 0.005
                }

                wrongLinked.forEach { txn ->
                    val target = investments.firstOrNull { other ->
                        other.id != investment.id &&
                            !hasExactTrace(other) &&
                            other.name.equals(investment.name, ignoreCase = true) &&
                            other.fundingSource.equals(investment.fundingSource, ignoreCase = true) &&
                            kotlin.math.abs(other.invested - txn.amount) <= 0.005
                    }
                    if (target != null) {
                        val relinked = txn.copy(
                            ref = target.id,
                            updatedAt = System.currentTimeMillis(),
                        )
                        ctx.dao.insertTransaction(relinked)
                        replaceCached(relinked)
                        remoteTransactions += relinked
                        helper.recordAudit(
                            action = "REPAIR",
                            entityType = "transaction",
                            entityId = relinked.id,
                            title = "Investment trace relinked: ${target.name}",
                            beforeValue = txn.desc,
                            afterValue = relinked.desc,
                            amountDelta = 0.0,
                            accountName = target.fundingSource,
                            projectId = relinked.projectId,
                            reason = "Debt-funded investment journal trace must point to the matching investment row",
                        )
                        repaired++
                    }
                }

                if (hasExactTrace(investment)) return@forEach

                val candidate = txCache.firstOrNull { txn ->
                    txn.ref != investment.id &&
                        investmentManager.isInvestmentJournalTrace(txn) &&
                        kotlin.math.abs(txn.amount - investment.invested) <= 0.005 &&
                        txn.desc.contains(investment.name, ignoreCase = true) &&
                        (investment.fundingSource.isBlank() ||
                            txn.desc.contains(investment.fundingSource, ignoreCase = true))
                }

                if (candidate != null) {
                    val relinked = candidate.copy(
                        ref = investment.id,
                        updatedAt = System.currentTimeMillis(),
                    )
                    ctx.dao.insertTransaction(relinked)
                    replaceCached(relinked)
                    remoteTransactions += relinked
                    helper.recordAudit(
                        action = "REPAIR",
                        entityType = "transaction",
                        entityId = relinked.id,
                        title = "Investment trace relinked: ${investment.name}",
                        beforeValue = candidate.desc,
                        afterValue = relinked.desc,
                        amountDelta = 0.0,
                        accountName = investment.fundingSource,
                        projectId = relinked.projectId,
                        reason = "Debt-funded investment journal trace must point to the matching investment row",
                    )
                    repaired++
                } else {
                    val created = Transaction(
                        id = app.fynlo.logic.Ids.newId(),
                        date = investment.date,
                        type = "Info",
                        amount = investment.invested,
                        category = "Investment",
                        desc = "Invested in ${investment.name} using ${investment.fundingSource} loan funds",
                        ref = investment.id,
                        notes = investment.notes,
                        tags = "journal_only",
                        projectId = investment.projectId,
                        updatedAt = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis(),
                    )
                    ctx.dao.insertTransaction(created)
                    replaceCached(created)
                    remoteTransactions += created
                    helper.recordAudit(
                        action = "REPAIR",
                        entityType = "transaction",
                        entityId = created.id,
                        title = "Investment trace created: ${investment.name}",
                        afterValue = created.desc,
                        amountDelta = 0.0,
                        accountName = investment.fundingSource,
                        projectId = created.projectId,
                        reason = "Debt-funded investment must have an exact journal-only source trace",
                    )
                    repaired++
                }
            }
        }

        remoteTransactions.forEach { txn -> helper.sync { setTransaction(txn) } }
        return repaired
    }

    suspend fun repairDebtReceiptAmountMismatches(): Int {
        val debts = ctx.dao.getAllDebts().first()
        val transactions = ctx.dao.getAllTransactionsList()
        val remoteTransactions = mutableListOf<Transaction>()
        val touchedAccounts = mutableSetOf<String>()
        var repaired = 0

        ctx.db.withTransaction {
            debts.forEach { debt ->
                val receipt = transactions.firstOrNull { txn ->
                    txn.ref == debt.id && txn.category.equals("Debt Received", ignoreCase = true)
                } ?: return@forEach
                val delta = debt.amount - receipt.amount
                if (kotlin.math.abs(delta) <= 0.005) return@forEach

                val destinationId = receipt.toAcctId.ifBlank {
                    ctx.dao.getAccountByName(receipt.toAcct)?.id.orEmpty()
                }
                val destinationName = receipt.toAcct.ifBlank {
                    ctx.dao.getAccountById(destinationId)?.name.orEmpty()
                }
                helper.applyAccountDelta(destinationId, destinationName, delta)
                val repairedReceipt = receipt.copy(
                    amount = debt.amount,
                    toAcct = destinationName,
                    toAcctId = destinationId,
                    updatedAt = System.currentTimeMillis(),
                )
                ctx.dao.insertTransaction(repairedReceipt)
                remoteTransactions += repairedReceipt
                if (destinationName.isNotBlank()) touchedAccounts += destinationName
                helper.recordAudit(
                    action = "REPAIR",
                    entityType = "transaction",
                    entityId = repairedReceipt.id,
                    title = "Debt receipt amount repaired: ${debt.name}",
                    beforeValue = receipt.desc,
                    afterValue = repairedReceipt.desc,
                    amountDelta = delta,
                    accountName = destinationName,
                    projectId = repairedReceipt.projectId,
                    reason = "Debt principal and linked Debt Received transaction must match",
                )
                repaired++
            }
        }

        remoteTransactions.forEach { txn -> helper.sync { setTransaction(txn) } }
        touchedAccounts.filter { it.isNotBlank() }.forEach { helper.syncAccountByName(it) }
        return repaired
    }

    suspend fun repairTransactionAccountIds(): Int {
        val accounts = ctx.dao.getAllAccountsList()
        val idByName = accounts.associate { it.name to it.id }
        if (idByName.isEmpty()) return 0

        val repairedTransactions = ctx.dao.getAllTransactionsList().mapNotNull { txn ->
            val resolvedFrom = if (txn.fromAcctId.isBlank() && txn.fromAcct.isNotBlank()) {
                idByName[txn.fromAcct].orEmpty()
            } else {
                txn.fromAcctId
            }
            val resolvedTo = if (txn.toAcctId.isBlank() && txn.toAcct.isNotBlank()) {
                idByName[txn.toAcct].orEmpty()
            } else {
                txn.toAcctId
            }

            if (resolvedFrom == txn.fromAcctId && resolvedTo == txn.toAcctId) {
                null
            } else {
                txn.copy(
                    fromAcctId = resolvedFrom,
                    toAcctId = resolvedTo,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        if (repairedTransactions.isEmpty()) return 0

        ctx.db.withTransaction {
            repairedTransactions.forEach { ctx.dao.insertTransaction(it) }
        }
        repairedTransactions.forEach { txn -> helper.sync { setTransaction(txn) } }
        return repairedTransactions.size
    }

    suspend fun repairAccountBalanceDriftFromLedger(): Int {
        val accounts = ctx.dao.getAllAccountsList()
        val transactions = ctx.dao.getAllTransactionsList()
        val auditEvents = ctx.dao.getAllAuditEventsOnce()
        val repairedAccounts = mutableListOf<Account>()
        var repaired = 0

        ctx.db.withTransaction {
            accounts.forEach { account ->
                val openingAudit = auditEvents
                    .filter { event ->
                        event.action == "CREATE" &&
                            event.entityType == "account" &&
                            (event.entityId == account.id || event.accountName == account.name)
                    }
                    .minByOrNull { it.timestamp }
                    ?: return@forEach
                val expected = expectedBalanceFromLedger(
                    account = account,
                    openingBalance = openingAudit.amountDelta,
                    transactions = transactions,
                    auditEvents = auditEvents,
                )
                val delta = expected - account.balance
                if (kotlin.math.abs(delta) <= 0.005) return@forEach

                val repairedAccount = account.copy(
                    balance = expected,
                    updatedAt = System.currentTimeMillis(),
                )
                ctx.dao.insertAccount(repairedAccount)
                repairedAccounts += repairedAccount
                helper.recordAudit(
                    action = "REPAIR",
                    entityType = "account",
                    entityId = account.id,
                    title = "Account balance reconciled: ${account.name}",
                    beforeValue = account.balance.toString(),
                    afterValue = expected.toString(),
                    amountDelta = delta,
                    accountName = account.name,
                    projectId = account.projectId,
                    reason = "Stored account balance must match opening balance plus transaction ledger",
                )
                repaired++
            }
        }

        repairedAccounts.forEach { account -> helper.sync { setAccount(account) } }
        return repaired
    }

    private fun expectedBalanceFromLedger(
        account: Account,
        openingBalance: Double,
        transactions: List<Transaction>,
        auditEvents: List<AuditEvent>,
    ): Double {
        var balance = openingBalance + auditEvents
            .filter { event ->
                event.action == "EDIT" &&
                    event.entityType == "account" &&
                    event.title.startsWith("Account edited:") &&
                    (event.entityId == account.id || event.accountName == account.name)
            }
            .sumOf { it.amountDelta }
        transactions.forEach { txn ->
            if (txn.type.equals("Info", ignoreCase = true)) return@forEach
            if (txn.tags.split(",").any { it.trim().equals("journal_only", ignoreCase = true) }) return@forEach

            val fromMatches = accountMatches(account, txn.fromAcctId, txn.fromAcct)
            val toMatches = accountMatches(account, txn.toAcctId, txn.toAcct)
            when (txn.type.lowercase()) {
                "expense" -> if (fromMatches) balance -= txn.amount
                "income" -> if (toMatches) balance += txn.amount
                "transfer" -> {
                    if (fromMatches) balance -= txn.amount
                    if (toMatches) balance += txn.amount
                }
            }
        }
        return balance
    }

    private fun accountMatches(account: Account, id: String, name: String): Boolean =
        (id.isNotBlank() && id == account.id) || (name.isNotBlank() && name == account.name)

    suspend fun recalculateAllBalances() {
        ctx.dao.backfillBorrowerSourceAccount()
        val borrowersBefore = ctx.dao.getAllBorrowers().first().associate { it.id to (it.name to it.paid) }
        val debtsBefore     = ctx.dao.getAllDebts().first().associate     { it.id to (it.name to it.paid) }
        ctx.dao.rebuildBorrowerPaidFromPayments()
        ctx.dao.rebuildDebtPaidFromDebtPayments()
        val borrowersAfter = ctx.dao.getAllBorrowers().first().associate { it.id to it.paid }
        val debtsAfter     = ctx.dao.getAllDebts().first().associate     { it.id to it.paid }
        for ((id, namePaid) in borrowersBefore) {
            val (name, oldPaid) = namePaid
            val newPaid = borrowersAfter[id] ?: continue
            val delta = newPaid - oldPaid
            if (kotlin.math.abs(delta) > 0.005) {
                app.fynlo.logic.BalanceAuditLog.record(
                    source  = app.fynlo.logic.BalanceAuditLog.Source.RECALC_BORROWER_PAID,
                    account = "Loan: $name",
                    delta   = delta,
                    note    = "Recalc rebuilt borrower.paid from payments ($oldPaid → $newPaid)",
                )
                helper.recordAudit(
                    action = "RECALC",
                    entityType = "loan",
                    entityId = id,
                    title = "Loan paid total rebuilt: $name",
                    beforeValue = oldPaid.toString(),
                    afterValue = newPaid.toString(),
                    amountDelta = delta,
                )
            }
        }
        for ((id, namePaid) in debtsBefore) {
            val (name, oldPaid) = namePaid
            val newPaid = debtsAfter[id] ?: continue
            val delta = newPaid - oldPaid
            if (kotlin.math.abs(delta) > 0.005) {
                app.fynlo.logic.BalanceAuditLog.record(
                    source  = app.fynlo.logic.BalanceAuditLog.Source.RECALC_DEBT_PAID,
                    account = "Debt: $name",
                    delta   = delta,
                    note    = "Recalc rebuilt debt.paid from debt_payments ($oldPaid → $newPaid)",
                )
                helper.recordAudit(
                    action = "RECALC",
                    entityType = "debt",
                    entityId = id,
                    title = "Debt paid total rebuilt: $name",
                    beforeValue = oldPaid.toString(),
                    afterValue = newPaid.toString(),
                    amountDelta = delta,
                )
            }
        }
    }
}
