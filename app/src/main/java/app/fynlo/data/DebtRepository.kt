package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for debt management.
 * Owns: Debts (loans taken), DebtPayments (repayments made to lenders)
 */
@Singleton
class DebtRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allDebts:        Flow<List<Debt>>        = dao.getAllDebts()
    val allDebtPayments: Flow<List<DebtPayment>> = dao.getAllDebtPayments()

    suspend fun insertDebt(debt: Debt) = withContext(Dispatchers.IO) {
        dao.insertDebt(debt)
        firestore.upsertDebt(debt)
    }

    suspend fun updateDebt(debt: Debt) = withContext(Dispatchers.IO) {
        dao.updateDebt(debt)
        firestore.upsertDebt(debt)
    }

    suspend fun deleteDebt(debt: Debt) = withContext(Dispatchers.IO) {
        dao.deleteDebt(debt.id)
        firestore.deleteDebt(debt.id)
    }

    suspend fun payInstalment(
        debt: Debt,
        principal: Double,
        interest: Double,
        debtPayment: DebtPayment
    ) = withContext(Dispatchers.IO) {
        dao.insertDebtPayment(debtPayment)
        dao.addDebtPayment(debt.id, principal, interest)
        val updated = debt.copy(
            paid          = debt.paid + principal + interest,
            paidPrincipal = debt.paidPrincipal + principal,
            paidInterest  = debt.paidInterest  + interest
        )
        dao.updateDebt(updated)
        firestore.upsertDebt(updated)
        firestore.upsertDebtPayment(debtPayment)
    }

    suspend fun getDebtPaymentsForDebt(debtId: String): List<DebtPayment> =
        withContext(Dispatchers.IO) { dao.getDebtPaymentsForDebt(debtId) }

    suspend fun fixPaidDoubleCount() = withContext(Dispatchers.IO) {
        dao.fixDebtPaidDoubleCount()
    }
}
