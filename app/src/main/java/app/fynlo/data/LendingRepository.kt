package app.fynlo.data

import android.util.Log
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class LendingRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allBorrowers: Flow<List<Borrower>> = dao.getAllBorrowers()
    val allPayments: Flow<List<Payment>> = dao.getAllPayments()

    private inline fun <T> recordOnFail(op: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Log.e("LendingRepo", "$op failed: ${e.message}", e)
        FirebaseCrashlytics.getInstance().apply {
            log("LendingRepository.$op failed")
            recordException(e)
        }
        throw e
    }

    suspend fun insertBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        recordOnFail("insertBorrower") {
            val b = borrower.copy(updatedAt = System.currentTimeMillis())
            dao.insertBorrower(b)
            Analytics.loanCreated(hasInterest = b.rate > 0.0)
        }
    }

    suspend fun updateBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        recordOnFail("updateBorrower") {
            val b = borrower.copy(updatedAt = System.currentTimeMillis())
            dao.insertBorrower(b)
        }
    }

    suspend fun deleteBorrowerRecord(borrower: Borrower) = withContext(Dispatchers.IO) {
        recordOnFail("deleteBorrowerRecord") { dao.deleteBorrower(borrower) }
    }

    suspend fun getBorrowerById(id: String): Borrower? = withContext(Dispatchers.IO) {
        recordOnFail("getBorrowerById") { dao.getBorrowerById(id) }
    }

    suspend fun insertPayment(payment: Payment) = withContext(Dispatchers.IO) {
        recordOnFail("insertPayment") {
            val p = payment.copy(updatedAt = System.currentTimeMillis())
            dao.insertPayment(p)
        }
    }

    suspend fun deletePayment(payment: Payment) = withContext(Dispatchers.IO) {
        recordOnFail("deletePayment") { dao.deletePayment(payment) }
    }

    suspend fun getPaymentsForLoanOnce(loanId: String): List<Payment> = withContext(Dispatchers.IO) {
        recordOnFail("getPaymentsForLoanOnce") { dao.getPaymentsForLoanOnce(loanId) }
    }

    fun getPaymentsForLoan(loanId: String): Flow<List<Payment>> = dao.getPaymentsForLoan(loanId)

    // updateBorrowerPaid{Amount,Principal,Interest} removed by C01 Sprint 1
    // Stage 2 (decisions/2026-05-26-c01-fix-strategy.md). `paid` and its
    // split are derived from the `payments` table — never written directly.
    // Callers must insertPayment / deletePayment and then call
    // rebuildBorrowerPaidFromPayments().

    suspend fun updateBorrowerDefaultStatus(id: String, status: String, defaultDate: String, frozenInterest: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateBorrowerDefaultStatus") {
            dao.updateBorrowerDefaultStatus(id, status, defaultDate, frozenInterest)
        }
    }

    suspend fun rebuildBorrowerPaidFromPayments() = withContext(Dispatchers.IO) {
        recordOnFail("rebuildBorrowerPaidFromPayments") { dao.rebuildBorrowerPaidFromPayments() }
    }

    suspend fun backfillBorrowerSourceAccount() = withContext(Dispatchers.IO) {
        recordOnFail("backfillBorrowerSourceAccount") { dao.backfillBorrowerSourceAccount() }
    }

    // recalculateBorrowerPaid removed by C01 Sprint 1 Stage 2 — the SQL it
    // wrapped (UPDATE borrowers SET paid = paidPrincipal + paidInterest) was
    // the destructive query that motivated the entire C01 ADR. Use
    // rebuildBorrowerPaidFromPayments() / FinanceRepository.recalculateAllBalances()
    // for the safe derive-from-truth pass.

    /** Wrapper for explicit "payment collected" call sites — counts as an analytics event. */
    suspend fun collectPayment(payment: Payment) = withContext(Dispatchers.IO) {
        recordOnFail("collectPayment") {
            val p = payment.copy(updatedAt = System.currentTimeMillis())
            dao.insertPayment(p)
            Analytics.paymentCollected()
        }
    }
}
