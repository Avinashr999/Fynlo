package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Transaction
import app.fynlo.logic.CurrencyFormatter
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.first

internal class LendingManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper
) {
    suspend fun insertBorrower(borrower: Borrower) = insertBorrowerWithSource(borrower, "Personal Cash")

    suspend fun updateBorrower(borrower: Borrower) {
        updateBorrowerWithSource(borrower, borrower.sourceAccount)
    }

    suspend fun updateBorrowerWithSource(borrower: Borrower, sourceAccount: String) {
        helper.requireOpenDate(borrower.date, borrower.projectId)
        val before = ctx.dao.getBorrowerById(borrower.id)
        val now = System.currentTimeMillis()
        var oldSource = ""
        var newSource = ""
        var updatedBorrower: Borrower?= null
        var updatedFundingTxn: Transaction?= null
        var paymentsNeedingReview: List<Payment> = emptyList()
        ctx.db.withTransaction {
            val fundingTxn = ctx.dao.getTransactionsByRef(borrower.id)
                .firstOrNull { it.category == "Lending" && it.type.equals("Expense", ignoreCase = true) }
            oldSource = fundingTxn?.fromAcct ?: before?.sourceAccount.orEmpty()
            newSource = sourceAccount.ifBlank { oldSource.ifBlank { before?.sourceAccount.orEmpty() } }
            val newSourceId = ctx.dao.getAccountByName(newSource)?.id ?: ""
            val b = borrower.copy(sourceAccount = newSource, updatedAt = now)
            ctx.dao.insertBorrower(b)
            if (before != null && fundingTxn != null) {
                helper.applyAccountDelta(fundingTxn.fromAcctId, fundingTxn.fromAcct, fundingTxn.amount)
                helper.applyAccountDelta(newSourceId, newSource, -b.amount)
                val fundingUpdate = fundingTxn.copy(
                    date = b.date,
                    amount = b.amount,
                    fromAcct = newSource,
                    fromAcctId = newSourceId,
                    category = "Lending",
                    desc = "Lent to ${b.name}",
                    notes = b.notes,
                    projectId = b.projectId,
                    updatedAt = now,
                )
                updatedFundingTxn = fundingUpdate
                ctx.dao.insertTransaction(fundingUpdate)
            }
            helper.recordAudit(
                action = "EDIT",
                entityType = "loan",
                entityId = b.id,
                title = "Loan edited: ${b.name}",
                beforeValue = before?.let { "${it.name}:${it.amount}:paid=${it.paid}:source=${it.sourceAccount}:status=${it.status}" } ?: "",
                afterValue = "${b.name}:${b.amount}:paid=${b.paid}:source=${b.sourceAccount}:status=${b.status}",
                amountDelta = b.amount - (before?.amount ?: b.amount),
                accountName = b.sourceAccount,
                projectId = b.projectId,
            )
            if (before != null && before.date != b.date) {
                paymentsNeedingReview = ctx.dao.getPaymentsForLoanOnce(b.id)
                    .filter { payment ->
                        val interest = app.fynlo.logic.InterestPolicy.paymentInterestAmount(payment)
                        app.fynlo.logic.InterestPolicy.isStaleCurrentPeriodInterest(
                            allocationType = payment.interestAllocationType,
                            interestAmount = interest,
                            periodStartDate = payment.interestPeriodStartDate,
                            currentStartDate = b.date,
                        )
                    }
                    .map { payment ->
                        payment.copy(
                            interestAllocationType = app.fynlo.logic.InterestPolicy.UNKNOWN_REVIEW,
                            updatedAt = now,
                        )
                    }
                paymentsNeedingReview.forEach { ctx.dao.insertPayment(it) }
            }
            ctx.dao.rebuildBorrowerPaidFromPayments()
            updatedBorrower = ctx.dao.getBorrowerById(b.id) ?: b
        }
        helper.sync {
            updatedBorrower?.let { setBorrower(it) }
            updatedFundingTxn?.let { setTransaction(it) }
            paymentsNeedingReview.forEach { setPayment(it) }
        }
        if (oldSource.isNotBlank()) helper.syncAccountByName(oldSource)
        if (newSource.isNotBlank() && newSource != oldSource) helper.syncAccountByName(newSource)
    }

    suspend fun insertBorrowerWithSource(borrower: Borrower, sourceAccount: String, projectId: String = borrower.projectId) {
        helper.requireOpenDate(borrower.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getBorrowerById(borrower.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val resolvedPeopleId =
                if (borrower.peopleId.isNotEmpty()) borrower.peopleId
                else helper.findOrCreatePersonId(borrower.name, borrower.phone, projectId)
            val b = borrower.copy(
                projectId = projectId,
                peopleId  = resolvedPeopleId,
                updatedAt = now,
                createdAt = if (borrower.createdAt == 0L) now else borrower.createdAt,
            )
            val bWithSource = b.copy(sourceAccount = sourceAccount)
            ctx.dao.insertBorrower(bWithSource)
            val sourceAccountId = ctx.dao.getAccountByName(sourceAccount)?.id ?: ""
            helper.applyAccountDelta(sourceAccountId, sourceAccount, -borrower.amount)
            val t = Transaction(app.fynlo.logic.Ids.newId(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, fromAcctId = sourceAccountId, category = "Lending", desc = "Lent to ${borrower.name}", ref = borrower.id, notes = borrower.notes, projectId = projectId, updatedAt = now, createdAt = now)
            ctx.dao.insertTransaction(t)
            helper.recordAudit(
                action = "CREATE",
                entityType = "loan",
                entityId = bWithSource.id,
                title = "Loan created: ${bWithSource.name}",
                afterValue = "lent=${bWithSource.amount}:from=$sourceAccount:txn=${t.id}",
                amountDelta = -bWithSource.amount,
                accountName = sourceAccount,
                projectId = projectId,
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "loan",
                entityId = bWithSource.id,
                title = "Create loan: ${bWithSource.name}",
                afterJson = helper.undoJson.encodeToString(LoanUndoBundle(borrower = bWithSource, transactions = listOf(t))),
                projectId = projectId,
            )
            helper.sync { setBorrower(bWithSource); setTransaction(t) }
        }
        Analytics.loanCreated(hasInterest = borrower.rate > 0.0)
        helper.syncAccountByName(sourceAccount)
    }

    suspend fun deleteBorrower(borrower: Borrower) {
        helper.requireOpenDate(borrower.date, borrower.projectId)
        val byRef  = ctx.dao.getTransactionsByRef(borrower.id)
        val byDesc = ctx.dao.getTransactionsByDesc("Lent to ${borrower.name}")
            .filter { it.ref.isBlank() && it.category == "Lending" }
        val linkedTxns = (byRef + byDesc).distinctBy { it.id }
        var linkedPayments = emptyList<Payment>()

        ctx.db.withTransaction {
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
            linkedPayments = ctx.dao.getPaymentsForLoanOnce(borrower.id)
            linkedPayments.forEach { p ->
                ctx.dao.deletePayment(p)
                helper.tombstoneRemoteDoc("payments", p.id)
            }
            helper.recordUndo(
                action = "DELETE",
                entityType = "loan",
                entityId = borrower.id,
                title = "Delete loan: ${borrower.name}",
                beforeJson = helper.undoJson.encodeToString(
                    LoanUndoBundle(
                        borrower = borrower,
                        transactions = linkedTxns,
                        payments = linkedPayments,
                    )
                ),
                projectId = borrower.projectId,
            )
            ctx.dao.deleteBorrower(borrower)
            helper.tombstoneRemoteDoc("borrowers", borrower.id)
            helper.recordAudit(
                action = "DELETE",
                entityType = "loan",
                entityId = borrower.id,
                title = "Loan deleted: ${borrower.name}",
                beforeValue = "lent=${borrower.amount}:paid=${borrower.paid}:source=${borrower.sourceAccount}",
                amountDelta = borrower.amount,
                accountName = borrower.sourceAccount,
                projectId = borrower.projectId,
            )
        }
        linkedTxns.forEach { helper.sync { deleteTransaction(it.id) } }
        linkedPayments.forEach { helper.sync { deletePayment(it.id) } }
        helper.sync { deleteBorrower(borrower.id) }
        linkedTxns.map { it.fromAcct }.filter { it.isNotBlank() }.distinct().forEach { helper.syncAccountByName(it) }
        linkedTxns.map { it.toAcct   }.filter { it.isNotBlank() }.distinct().forEach { helper.syncAccountByName(it) }
    }

    suspend fun fixPaidDoubleCount() {
        ctx.dao.backfillBorrowerSourceAccount()
        ctx.dao.rebuildBorrowerPaidFromPayments()
        ctx.dao.rebuildDebtPaidFromDebtPayments()
    }

    suspend fun insertPaymentWithDest(payment: Payment, destinationAccount: String, projectId: String = payment.projectId) {
        helper.requireOpenDate(payment.date, projectId)
        ctx.db.withTransaction {
            if (ctx.dao.getPaymentById(payment.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val borrowerBefore = ctx.dao.getBorrowerById(payment.loanId)
            val p = payment.copy(projectId = projectId, updatedAt = now, createdAt = if (payment.createdAt == 0L) now else payment.createdAt)
            ctx.dao.insertPayment(p)
            Analytics.paymentCollected()

            ctx.dao.updateAccountBalance(destinationAccount, payment.amount)
            ctx.dao.rebuildBorrowerPaidFromPayments()

            val t = Transaction(
                id = app.fynlo.logic.Ids.newId(),
                date = payment.date,
                type = "Income",
                amount = payment.amount,
                toAcct = destinationAccount,
                category = "Loan Repayment",
                desc = "Received from ${payment.name}",
                ref = payment.loanId,
                notes = payment.notes,
                projectId = projectId,
                updatedAt = now,
                createdAt = now
            )
            ctx.dao.insertTransaction(t)

            val updatedBorrower = ctx.dao.getBorrowerById(payment.loanId)
            helper.recordAudit(
                action = "PAYMENT",
                entityType = "loan",
                entityId = payment.loanId,
                title = "Loan payment received: ${payment.name}",
                afterValue = "amount=${p.amount}:to=$destinationAccount:payment=${p.id}:txn=${t.id}",
                amountDelta = p.amount,
                accountName = destinationAccount,
                projectId = projectId,
                reason = "Interest payments are tracked separately; loan date remains the accrual start.",
            )
            helper.recordUndo(
                action = "CREATE",
                entityType = "payment",
                entityId = p.id,
                title = "Loan payment: ${p.name}",
                afterJson = helper.undoJson.encodeToString(
                    PaymentUndoBundle(payment = p, transactions = listOf(t), borrowerBefore = borrowerBefore)
                ),
                projectId = projectId,
            )
            helper.sync {
                setPayment(p)
                setTransaction(t)
                updatedBorrower?.let { setBorrower(it) }
            }
        }
        helper.syncAccountByName(destinationAccount)
    }

    fun getPaymentsForLoan(loanId: String) = ctx.dao.getPaymentsForLoan(loanId)

    suspend fun waiveBorrowerInterest(borrower: Borrower, amount: Double, reason: String) {
        helper.requireOpenDate(
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            borrower.projectId,
        )
        val targetId = borrower.id
        var updated: Borrower?= null
        ctx.db.withTransaction {
            val current = ctx.dao.getBorrowerById(targetId) ?: return@withTransaction
            val remainingInterest = app.fynlo.logic.InterestPolicy.borrowerInterestOutstanding(current)
            val waiver = amount.coerceIn(0.0, remainingInterest)
            if (waiver <= 0.0) return@withTransaction

            val next = current.copy(
                interestWaived = current.interestWaived + waiver,
                updatedAt = System.currentTimeMillis(),
            )
            ctx.dao.insertBorrower(next)
            helper.recordAudit(
                action = "WAIVE_INTEREST",
                entityType = "loan",
                entityId = next.id,
                title = "Interest waived: ${next.name}",
                beforeValue = "waived=${current.interestWaived}:interestOutstanding=$remainingInterest",
                afterValue = "waived=${next.interestWaived}:interestOutstanding=${(remainingInterest - waiver).coerceAtLeast(0.0)}",
                amountDelta = 0.0,
                projectId = next.projectId,
                reason = reason.ifBlank { "Interest grace/waiver" },
            )
            updated = next
        }
        updated?.let { helper.sync { setBorrower(it) } }
    }

    suspend fun restoreBorrowerToActive(borrower: Borrower) {
        val updated = borrower.copy(
            status        = "Active",
            defaultDate   = "",
            frozenInterest = 0.0,
            updatedAt     = System.currentTimeMillis()
        )
        ctx.dao.insertBorrower(updated)
        helper.sync { setBorrower(updated) }
    }

    suspend fun markBorrowerDefaulted(borrower: Borrower) {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val frozenInterest = app.fynlo.logic.InterestPolicy.accruedForBorrower(borrower, today)
        val updated = borrower.copy(
            status        = "Defaulted",
            defaultDate   = today,
            frozenInterest = frozenInterest,
            updatedAt     = System.currentTimeMillis()
        )
        ctx.dao.updateBorrowerDefaultStatus(borrower.id, "Defaulted", today, frozenInterest)
        helper.sync { setBorrower(updated) }
    }

    suspend fun writeOffBorrower(borrower: Borrower, fromAccount: String = "Personal Cash") {
        val outstanding = if (borrower.status == "Defaulted" && borrower.frozenInterest > 0) {
            (borrower.amount - borrower.paidPrincipal) + maxOf(0.0, borrower.frozenInterest - borrower.paidInterest - borrower.interestWaived)
        } else {
            val interest = app.fynlo.logic.InterestPolicy.borrowerInterestOutstanding(borrower)
            (borrower.amount - borrower.paidPrincipal) + interest
        }

        val currencyCode = ctx.dao.getProjectById(borrower.projectId)?.currency ?: "INR"

        ctx.db.withTransaction {
            val updated = borrower.copy(status = "WrittenOff", updatedAt = System.currentTimeMillis())
            ctx.dao.insertBorrower(updated)

            val t = Transaction(
                id        = app.fynlo.logic.Ids.newId(),
                date      = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                type      = "Expense",
                amount    = outstanding,
                fromAcct  = "",
                category  = "Bad Debt",
                desc      = "Write-off: ${borrower.name} — outstanding ${CurrencyFormatter.detail(outstanding, currencyCode)}",
                ref       = borrower.id,
                tags      = "journal_only",
                projectId = borrower.projectId,
                updatedAt = System.currentTimeMillis()
            )
            ctx.dao.insertTransaction(t)
            helper.sync { setBorrower(updated); setTransaction(t) }
        }
    }
}
