package app.fynlo.data

import android.util.Log
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class DebtRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allDebts: Flow<List<Debt>> = dao.getAllDebts()
    val allDebtPayments: Flow<List<DebtPayment>> = dao.getAllDebtPayments()

    private inline fun <T> recordOnFail(op: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Log.e("DebtRepo", "$op failed: ${e.message}", e)
        FirebaseCrashlytics.getInstance().apply {
            log("DebtRepository.$op failed")
            recordException(e)
        }
        throw e
    }

    suspend fun insertDebt(debt: Debt) = withContext(Dispatchers.IO) {
        recordOnFail("insertDebt") {
            val d = debt.copy(updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
        }
    }

    suspend fun updateDebt(debt: Debt) = withContext(Dispatchers.IO) {
        recordOnFail("updateDebt") {
            val d = debt.copy(updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
        }
    }

    suspend fun deleteDebtRecord(debt: Debt) = withContext(Dispatchers.IO) {
        recordOnFail("deleteDebtRecord") { dao.deleteDebt(debt) }
    }

    suspend fun deleteDebtById(id: String) = withContext(Dispatchers.IO) {
        recordOnFail("deleteDebtById") { dao.deleteDebtById(id) }
    }

    suspend fun getDebtById(id: String): Debt? = withContext(Dispatchers.IO) {
        recordOnFail("getDebtById") { dao.getDebtById(id) }
    }

    suspend fun insertDebtPayment(payment: DebtPayment) = withContext(Dispatchers.IO) {
        recordOnFail("insertDebtPayment") {
            val p = payment.copy(updatedAt = System.currentTimeMillis())
            dao.insertDebtPayment(p)
        }
    }

    suspend fun deleteDebtPayment(payment: DebtPayment) = withContext(Dispatchers.IO) {
        recordOnFail("deleteDebtPayment") { dao.deleteDebtPayment(payment) }
    }

    suspend fun getDebtPaymentsForDebtOnce(debtId: String): List<DebtPayment> = withContext(Dispatchers.IO) {
        recordOnFail("getDebtPaymentsForDebtOnce") { dao.getDebtPaymentsForDebtOnce(debtId) }
    }

    suspend fun updateDebtPaidAmount(debtId: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateDebtPaidAmount") { dao.updateDebtPaidAmount(debtId, amount) }
    }

    suspend fun updateDebtPaidPrincipal(debtId: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateDebtPaidPrincipal") { dao.updateDebtPaidPrincipal(debtId, amount) }
    }

    suspend fun updateDebtPaidInterest(debtId: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateDebtPaidInterest") { dao.updateDebtPaidInterest(debtId, amount) }
    }

    suspend fun rebuildDebtPaidFromDebtPayments() = withContext(Dispatchers.IO) {
        recordOnFail("rebuildDebtPaidFromDebtPayments") { dao.rebuildDebtPaidFromDebtPayments() }
    }

    suspend fun recalculateDebtPaid() = withContext(Dispatchers.IO) {
        recordOnFail("recalculateDebtPaid") { dao.recalculateDebtPaid() }
    }
}
