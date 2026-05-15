package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LendingRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allBorrowers: Flow<List<Borrower>> = dao.getAllBorrowers()
    val allPayments: Flow<List<Payment>> = dao.getAllPayments()

    suspend fun insertBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        val b = borrower.copy(updatedAt = System.currentTimeMillis())
        dao.insertBorrower(b)
    }

    suspend fun updateBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        val b = borrower.copy(updatedAt = System.currentTimeMillis())
        dao.insertBorrower(b)
    }

    suspend fun deleteBorrowerRecord(borrower: Borrower) = withContext(Dispatchers.IO) {
        dao.deleteBorrower(borrower)
    }

    suspend fun getBorrowerById(id: String): Borrower? = withContext(Dispatchers.IO) {
        dao.getBorrowerById(id)
    }

    suspend fun insertPayment(payment: Payment) = withContext(Dispatchers.IO) {
        val p = payment.copy(updatedAt = System.currentTimeMillis())
        dao.insertPayment(p)
    }

    suspend fun deletePayment(payment: Payment) = withContext(Dispatchers.IO) {
        dao.deletePayment(payment)
    }

    suspend fun getPaymentsForLoanOnce(loanId: String): List<Payment> = withContext(Dispatchers.IO) {
        dao.getPaymentsForLoanOnce(loanId)
    }

    fun getPaymentsForLoan(loanId: String): Flow<List<Payment>> = dao.getPaymentsForLoan(loanId)

    suspend fun updateBorrowerPaidAmount(borrowerId: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateBorrowerPaidAmount(borrowerId, amount)
    }

    suspend fun updateBorrowerPaidPrincipal(borrowerId: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateBorrowerPaidPrincipal(borrowerId, amount)
    }

    suspend fun updateBorrowerPaidInterest(borrowerId: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateBorrowerPaidInterest(borrowerId, amount)
    }

    suspend fun updateBorrowerDefaultStatus(id: String, status: String, defaultDate: String, frozenInterest: Double) = withContext(Dispatchers.IO) {
        dao.updateBorrowerDefaultStatus(id, status, defaultDate, frozenInterest)
    }

    suspend fun rebuildBorrowerPaidFromPayments() = withContext(Dispatchers.IO) {
        dao.rebuildBorrowerPaidFromPayments()
    }

    suspend fun backfillBorrowerSourceAccount() = withContext(Dispatchers.IO) {
        dao.backfillBorrowerSourceAccount()
    }

    suspend fun recalculateBorrowerPaid() = withContext(Dispatchers.IO) {
        dao.recalculateBorrowerPaid()
    }
}
