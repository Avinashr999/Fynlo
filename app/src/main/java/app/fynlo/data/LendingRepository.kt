package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for all lending/borrower operations.
 *
 * Owns: Borrowers, Payments (loan repayments)
 * Split from the monolithic FinanceRepository for maintainability.
 */
@Singleton
class LendingRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    // ── Observe ───────────────────────────────────────────────────────────────
    val allBorrowers: Flow<List<Borrower>> = dao.getAllBorrowers()
    val allPayments:  Flow<List<Payment>>  = dao.getAllPayments()

    // ── Write ─────────────────────────────────────────────────────────────────
    suspend fun insertBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        dao.insertBorrower(borrower)
        firestore.upsertBorrower(borrower)
    }

    suspend fun updateBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        dao.updateBorrower(borrower)
        firestore.upsertBorrower(borrower)
    }

    suspend fun deleteBorrower(borrower: Borrower) = withContext(Dispatchers.IO) {
        dao.deleteBorrower(borrower.id)
        firestore.deleteBorrower(borrower.id)
    }

    suspend fun collectPayment(
        borrower: Borrower,
        amount: Double,
        principalPortion: Double,
        interestPortion: Double,
        payment: Payment
    ) = withContext(Dispatchers.IO) {
        dao.insertPayment(payment)
        dao.addPaymentToBorrower(borrower.id, principalPortion, interestPortion)
        val updated = borrower.copy(
            paid           = borrower.paid + amount,
            paidPrincipal  = borrower.paidPrincipal + principalPortion,
            paidInterest   = borrower.paidInterest + interestPortion
        )
        dao.updateBorrower(updated)
        firestore.upsertBorrower(updated)
        firestore.upsertPayment(payment)
    }

    suspend fun markDefaulted(
        borrowerId: String,
        frozenInterest: Double,
        defaultDate: String
    ) = withContext(Dispatchers.IO) {
        dao.setBorrowerStatus(borrowerId, "Defaulted", defaultDate, frozenInterest)
        dao.getBorrowerById(borrowerId)?.let { firestore.upsertBorrower(it) }
    }

    suspend fun getPaymentsForLoan(loanId: String): List<Payment> =
        withContext(Dispatchers.IO) { dao.getPaymentsForLoan(loanId) }

    suspend fun fixPaidDoubleCount() = withContext(Dispatchers.IO) {
        dao.fixBorrowerPaidDoubleCount()
    }
}
