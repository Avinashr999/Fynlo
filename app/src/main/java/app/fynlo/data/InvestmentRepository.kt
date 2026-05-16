package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class InvestmentRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allInvestments: Flow<List<Investment>> = dao.getAllInvestments()
    val allValuations: Flow<List<InvestmentValuation>>
        get() = throw UnsupportedOperationException("Use getValuationsForInvestment(id) instead")

    suspend fun insertInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        val i = investment.copy(updatedAt = System.currentTimeMillis())
        dao.insertInvestment(i)
    }

    suspend fun deleteInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        dao.deleteInvestment(investment)
    }

    suspend fun insertValuation(valuation: InvestmentValuation) = withContext(Dispatchers.IO) {
        dao.insertValuation(valuation)
    }

    fun getValuationsForInvestment(invId: String): Flow<List<InvestmentValuation>> =
        dao.getValuationsForInvestment(invId)

    suspend fun deleteValuationsForInvestment(invId: String) = withContext(Dispatchers.IO) {
        dao.deleteValuationsForInvestment(invId)
    }
}
