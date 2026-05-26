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

    // updateDebtPaid{Amount,Principal,Interest} and recalculateDebtPaid
    // removed by C01 Sprint 1 Stage 2
    // (decisions/2026-05-26-c01-fix-strategy.md). `paid` is derived from
    // the `debt_payments` table — never written directly. Use
    // rebuildDebtPaidFromDebtPayments() (kept) or
    // FinanceRepository.recalculateAllBalances() for a derive-from-truth pass.

    suspend fun rebuildDebtPaidFromDebtPayments() = withContext(Dispatchers.IO) {
        recordOnFail("rebuildDebtPaidFromDebtPayments") { dao.rebuildDebtPaidFromDebtPayments() }
    }
}
