package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.Investment
import app.fynlo.data.model.InvestmentValuation
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for investment tracking.
 * Owns: Investments, InvestmentValuations (market value history)
 */
@Singleton
class InvestmentRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allInvestments:  Flow<List<Investment>>          = dao.getAllInvestments()
    val allValuations:   Flow<List<InvestmentValuation>> = dao.getAllInvestmentValuations()

    suspend fun insertInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        dao.insertInvestment(investment)
        firestore.upsertInvestment(investment)
    }

    suspend fun updateInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        dao.updateInvestment(investment)
        firestore.upsertInvestment(investment)
    }

    suspend fun deleteInvestment(investment: Investment) = withContext(Dispatchers.IO) {
        dao.deleteInvestment(investment.id)
        firestore.deleteInvestment(investment.id)
    }

    suspend fun saveValuation(valuation: InvestmentValuation) = withContext(Dispatchers.IO) {
        dao.insertInvestmentValuation(valuation)
        firestore.upsertInvestmentValuation(valuation)
    }

    suspend fun withdraw(investment: Investment, amount: Double) = withContext(Dispatchers.IO) {
        val updated = investment.copy(
            withdrawn  = investment.withdrawn + amount,
            currentVal = (investment.currentVal - amount).coerceAtLeast(0.0)
        )
        dao.updateInvestment(updated)
        firestore.upsertInvestment(updated)
    }

    suspend fun getValuationsForInvestment(investmentId: String): List<InvestmentValuation> =
        withContext(Dispatchers.IO) { dao.getInvestmentValuations(investmentId) }
    /** Fund investment by debiting an account balance */
    suspend fun insertFundedByAccount(investment: Investment, accountName: String) =
        withContext(Dispatchers.IO) {
            dao.insertInvestment(investment)
            // Debit the source account
            val account = dao.getAccountByName(accountName)
            if (account != null) {
                dao.updateAccount(account.copy(balance = account.balance - investment.invested))
            }
            firestore.upsertInvestment(investment)
        }

    /** Link investment to an existing debt record */
    suspend fun insertFundedByExistingDebt(investment: Investment, debt: app.fynlo.data.model.Debt) =
        withContext(Dispatchers.IO) {
            dao.insertInvestment(investment)
            firestore.upsertInvestment(investment)
        }

    /** Create a new debt AND an investment funded by it */
    suspend fun insertFundedByNewLoan(investment: Investment, newDebt: app.fynlo.data.model.Debt) =
        withContext(Dispatchers.IO) {
            dao.insertDebt(newDebt)
            dao.insertInvestment(investment)
            firestore.upsertDebt(newDebt)
            firestore.upsertInvestment(investment)
        }

}
