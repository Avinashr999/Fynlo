package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class DebtRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allDebts: Flow<List<Debt>> = dao.getAllDebts()
    val allDebtPayments: Flow<List<DebtPayment>> = dao.getAllDebtPayments()

    suspend fun insertDebt(debt: Debt) = withContext(Dispatchers.IO) {
        val d = debt.copy(updatedAt = System.currentTimeMillis())
        dao.insertDebt(d)
    }

    suspend fun updateDebt(debt: Debt) = withContext(Dispatchers.IO) {
        val d = debt.copy(updatedAt = System.currentTimeMillis())
        dao.insertDebt(d)
    }

    suspend fun deleteDebtRecord(debt: Debt) = withContext(Dispatchers.IO) {
        dao.deleteDebt(debt)
    }

    suspend fun deleteDebtById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteDebtById(id)
    }

    suspend fun getDebtById(id: String): Debt? = withContext(Dispatchers.IO) {
        dao.getDebtById(id)
    }

    suspend fun insertDebtPayment(payment: DebtPayment) = withContext(Dispatchers.IO) {
        val p = payment.copy(updatedAt = System.currentTimeMillis())
        dao.insertDebtPayment(p)
    }

    suspend fun deleteDebtPayment(payment: DebtPayment) = withContext(Dispatchers.IO) {
        dao.deleteDebtPayment(payment)
    }

    suspend fun getDebtPaymentsForDebtOnce(debtId: String): List<DebtPayment> = withContext(Dispatchers.IO) {
        dao.getDebtPaymentsForDebtOnce(debtId)
    }

    suspend fun updateDebtPaidAmount(debtId: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateDebtPaidAmount(debtId, amount)
    }

    suspend fun updateDebtPaidPrincipal(debtId: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateDebtPaidPrincipal(debtId, amount)
    }

    suspend fun updateDebtPaidInterest(debtId: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateDebtPaidInterest(debtId, amount)
    }

    suspend fun rebuildDebtPaidFromDebtPayments() = withContext(Dispatchers.IO) {
        dao.rebuildDebtPaidFromDebtPayments()
    }

    suspend fun recalculateDebtPaid() = withContext(Dispatchers.IO) {
        dao.recalculateDebtPaid()
    }
}
