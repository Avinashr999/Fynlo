package app.fynlo.data

import android.util.Log
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class InvestmentRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allInvestments: Flow<List<Investment>> = dao.getAllInvestments()
    val allValuations: Flow<List<InvestmentValuation>> = dao.getAllValuations()

    private inline fun <T> recordOnFail(op: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Log.e("InvestmentRepo", "$op failed: ${e.message}", e)
        FirebaseCrashlytics.getInstance().apply {
            log("InvestmentRepository.$op failed")
            recordException(e)
        }
        throw e
    }

    suspend fun insertInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        recordOnFail("insertInvestment") {
            val i = investment.copy(updatedAt = System.currentTimeMillis())
            dao.insertInvestment(i)
        }
    }

    suspend fun deleteInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        recordOnFail("deleteInvestment") { dao.deleteInvestment(investment) }
    }

    suspend fun insertValuation(valuation: InvestmentValuation) = withContext(Dispatchers.IO) {
        recordOnFail("insertValuation") { dao.insertValuation(valuation) }
    }

    fun getValuationsForInvestment(invId: String): Flow<List<InvestmentValuation>> =
        dao.getValuationsForInvestment(invId)

    suspend fun deleteValuationsForInvestment(invId: String) = withContext(Dispatchers.IO) {
        recordOnFail("deleteValuationsForInvestment") { dao.deleteValuationsForInvestment(invId) }
    }
}
