package app.fynlo.delegate

import app.fynlo.data.model.ProofAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.fynlo.logic.Ids
import app.fynlo.data.model.FinancialSummary

@androidx.compose.runtime.Immutable
data class RecalcDelta(
    val before: FinancialSummary,
    val after: FinancialSummary,
) {
    val netWorthChange: Double get() = after.netWorth - before.netWorth
    val receivablesChange: Double get() = after.totalReceivables - before.totalReceivables
    val cashChange: Double get() = after.totalCash - before.totalCash
    val investmentsChange: Double get() = after.totalInvestments - before.totalInvestments

    val isNoOp: Boolean get() =
        kotlin.math.abs(netWorthChange) < 0.5 &&
        kotlin.math.abs(receivablesChange) < 0.5 &&
        kotlin.math.abs(cashChange) < 0.5 &&
        kotlin.math.abs(investmentsChange) < 0.5
}

@androidx.compose.runtime.Immutable
data class BookRepairResult(
    val deletedResidue: Int = 0,
    val debtFundedTransferTraces: Int = 0,
    val debtFundedJournalRefs: Int = 0,
    val debtReceiptMismatches: Int = 0,
    val transactionAccountIds: Int = 0,
    val accountBalanceDrift: Int = 0,
    val recalcDelta: RecalcDelta?= null,
    val errorMessage: String?= null,
) {
    val repairedItemCount: Int
        get() = deletedResidue +
            debtFundedTransferTraces +
            debtFundedJournalRefs +
            debtReceiptMismatches +
            transactionAccountIds +
            accountBalanceDrift

    val isClean: Boolean
        get() = errorMessage == null && repairedItemCount == 0 && (recalcDelta?.isNoOp != false)
}

class BookRepairDelegate(
    private val ctx: ViewModelContext,
    private val getFinancialSummary: () -> FinancialSummary,
    private val financialSummaryFlow: kotlinx.coroutines.flow.StateFlow<FinancialSummary>
) {

    fun closeMonth(month: String, note: String = "") {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.closeMonth(ctx.currentProjectId(), month, note)
            ctx.showFeedback("Month closed")
        }
    }

    fun reopenMonth(month: String, note: String = "") {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.reopenMonth(ctx.currentProjectId(), month, note)
            ctx.showFeedback("Month reopened")
        }
    }

    fun undoLastMoneyAction() {
        ctx.scope.launch(Dispatchers.IO) {
            val undone = ctx.repository.undoLastMoneyAction()
            ctx.showFeedback(if (undone) "Last action undone" else "No recent action to undo")
        }
    }

    fun resolveSyncConflict(id: String, resolution: String) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.resolveSyncConflict(id, resolution)
            val message = when (resolution) {
                "KeepPhone" -> "Phone copy kept and synced"
                "KeepCloud" -> "Cloud copy applied to this phone"
                else -> "Conflict marked as reviewed"
            }
            ctx.showFeedback(message)
        }
    }

    fun addProofAttachment(
        ownerType: String,
        ownerId: String,
        displayName: String,
        mimeType: String,
        localUri: String,
        note: String = "",
    ) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.addProofAttachment(
                ProofAttachment(
                    id = Ids.newId(),
                    ownerType = ownerType,
                    ownerId = ownerId,
                    displayName = displayName.ifBlank { "Proof attachment" },
                    mimeType = mimeType,
                    localUri = localUri,
                    note = note,
                    projectId = ctx.currentProjectId(),
                )
            )
            ctx.showFeedback("Proof attached")
        }
    }

    fun deleteProofAttachment(id: String) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.deleteProofAttachment(id)
            ctx.showFeedback("Proof removed")
        }
    }

    fun recalculateAllBalances() {
        ctx.scope.launch(Dispatchers.IO) { ctx.recalcCoordinator.runAndStamp() }
    }

    suspend fun recalculateAllBalancesCapturingDelta(): RecalcDelta {
        val pre = getFinancialSummary()
        ctx.recalcCoordinator.runAndStamp()
        val post = kotlinx.coroutines.withTimeoutOrNull(500L) {
            financialSummaryFlow.first { it != pre }
        } ?: pre
        return RecalcDelta(pre, post)
    }

    fun runSafeBookRepair(onComplete: (BookRepairResult) -> Unit) {
        ctx.scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val deletedResidue = ctx.repository.repairDeletedAuditResidue()
                val debtFundedTransferTraces = ctx.repository.repairDebtFundedInvestmentTransferTraces()
                val debtFundedJournalRefs = ctx.repository.repairDebtFundedInvestmentJournalTraceRefs()
                val debtReceiptMismatches = ctx.repository.repairDebtReceiptAmountMismatches()
                val transactionAccountIds = ctx.repository.repairTransactionAccountIds()
                val accountBalanceDrift = ctx.repository.repairAccountBalanceDriftFromLedger()
                ctx.repository.fixPaidDoubleCount()
                val delta = recalculateAllBalancesCapturingDelta()

                BookRepairResult(
                    deletedResidue = deletedResidue,
                    debtFundedTransferTraces = debtFundedTransferTraces,
                    debtFundedJournalRefs = debtFundedJournalRefs,
                    debtReceiptMismatches = debtReceiptMismatches,
                    transactionAccountIds = transactionAccountIds,
                    accountBalanceDrift = accountBalanceDrift,
                    recalcDelta = delta,
                )
            }.getOrElse { error ->
                BookRepairResult(errorMessage = error.message ?: "Repair could not finish")
            }
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }
}
