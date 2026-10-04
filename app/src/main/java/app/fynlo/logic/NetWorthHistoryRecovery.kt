package app.fynlo.logic

import app.fynlo.data.model.BackupData
import app.fynlo.data.model.NetWorthSnapshot
import app.fynlo.data.ProjectScope
import java.time.LocalDate
import kotlin.math.abs

/** Offline reconstruction only. No phone ledger is used to invent past balances. */
object NetWorthHistoryRecovery {
    data class Result(val snapshot: NetWorthSnapshot?, val reasons: List<String>)

    fun reconstruct(ledger: BackupData, captureDate: String, sourceSha256: String,
                    projectId: String, capturedAt: Long): Result {
        require(LocalDate.parse(captureDate).toString() == captureDate)
        require(sourceSha256.matches(Regex("[a-f0-9]{64}")))
        require(capturedAt > 0)
        fun belongs(id: String) = ProjectScope.belongsToSelectedProject(id, projectId)
        val loans = ledger.borrowers.filter { belongs(it.projectId) }
        val debts = ledger.debts.filter { belongs(it.projectId) }
        val payments = ledger.payments.filter { p -> loans.any { it.id == p.loanId } }
        val debtPayments = ledger.debtPayments.filter { p -> debts.any { it.id == p.debtId } }
        val reasons = mutableListOf<String>()
        if (ledger.payments.any { belongs(it.projectId) && pMissing(it.loanId, ledger.borrowers.map { b -> b.id }) } ||
            ledger.debtPayments.any { belongs(it.projectId) && pMissing(it.debtId, ledger.debts.map { d -> d.id }) }) {
            reasons += "Payment has no matching loan or debt in this backup"
        }
        fun checkPayment(type: String, amount: Double, principal: Double, interest: Double,
                         penalty: Long, rounding: Long, allocation: String, paymentDate: String) {
            if (paymentDate > captureDate) return
            if (type == "Interest Only" && principal != 0.0) reasons += "Interest-only receipt reduces principal"
            if (principal < 0 || interest < 0 || !amount.isFinite() ||
                abs(amount - principal - interest - penalty / 100.0 - rounding / 100.0) > 0.011) {
                reasons += "Payment components do not match its saved amount"
            }
            if (interest > 0 && allocation == "UNKNOWN_REVIEW") reasons += "Historical interest period is unresolved"
        }
        payments.forEach { checkPayment(it.type, it.amount, it.principal, it.interest, it.penaltyPaise, it.roundingPaise, it.interestAllocationType, it.date) }
        debtPayments.forEach { checkPayment(it.type, it.amount, it.principal, it.interest, it.penaltyPaise, it.roundingPaise, it.interestAllocationType, it.date) }
        val totals = NetWorthTotals.calculate(ledger.accounts.filter { belongs(it.projectId) },
            ledger.investments.filter { belongs(it.projectId) }, loans, debts, payments, debtPayments, captureDate)
        val row = NetWorthSnapshot(captureDate, totals.netWorth, totals.totalAssets,
            totals.totalDebtPrincipal + totals.totalDebtInterest, projectId, capturedAt,
            NetWorthHistoryPolicy.RECOVERED, "Dated backup $captureDate; SHA-256 $sourceSha256; complete-ledger policy v1")
        if (!NetWorthHistoryPolicy.isTrusted(row)) reasons += "Balance sheet is not finite or balanced"
        return Result(row.takeIf { reasons.isEmpty() }, reasons.distinct())
    }

    private fun pMissing(id: String, ids: List<String>) = id !in ids
}
