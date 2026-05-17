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

    suspend fun updateBorrowerPaidAmount(borrowerId: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateBorrowerPaidAmount") { dao.updateBorrowerPaidAmount(borrowerId, amount) }
    }

    suspend fun updateBorrowerPaidPrincipal(borrowerId: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateBorrowerPaidPrincipal") { dao.updateBorrowerPaidPrincipal(borrowerId, amount) }
    }

    suspend fun updateBorrowerPaidInterest(borrowerId: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateBorrowerPaidInterest") { dao.updateBorrowerPaidInterest(borrowerId, amount) }
    }

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

    suspend fun recalculateBorrowerPaid() = withContext(Dispatchers.IO) {
        recordOnFail("recalculateBorrowerPaid") { dao.recalculateBorrowerPaid() }
    }

    /** Wrapper for explicit "payment collected" call sites — counts as an analytics event. */
    suspend fun collectPayment(payment: Payment) = withContext(Dispatchers.IO) {
        recordOnFail("collectPayment") {
            val p = payment.copy(updatedAt = System.currentTimeMillis())
            dao.insertPayment(p)
            Analytics.paymentCollected()
        }
    }
}
