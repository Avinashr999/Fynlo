package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.Account
import app.fynlo.data.model.NetWorthSnapshot
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for account and net worth management.
 * Owns: Accounts, NetWorthSnapshots
 */
@Singleton
class AccountRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allAccounts:      Flow<List<Account>>          = dao.getAllAccounts()
    val allSnapshots:     Flow<List<NetWorthSnapshot>> = dao.getNetWorthSnapshots()

    suspend fun insertAccount(account: Account) = withContext(Dispatchers.IO) {
        dao.insertAccount(account)
        firestore.upsertAccount(account)
    }

    suspend fun updateAccount(account: Account) = withContext(Dispatchers.IO) {
        dao.updateAccount(account)
        firestore.upsertAccount(account)
    }

    suspend fun deleteAccount(account: Account) = withContext(Dispatchers.IO) {
        dao.deleteAccount(account.id)
        firestore.deleteAccount(account.id)
    }

    suspend fun adjustBalance(
        accountName: String,
        newBalance: Double,
        oldBalance: Double
    ) = withContext(Dispatchers.IO) {
        dao.quickEditBalance(accountName, newBalance, oldBalance)
        dao.getAccountByName(accountName)?.let { firestore.upsertAccount(it) }
    }

    suspend fun saveNetWorthSnapshot(snapshot: NetWorthSnapshot) = withContext(Dispatchers.IO) {
        dao.insertNetWorthSnapshot(snapshot)
        firestore.upsertNetWorthSnapshot(snapshot)
    }

    fun getNetWorthSnapshotsFlow(): Flow<List<NetWorthSnapshot>> = dao.getNetWorthSnapshots()
}
