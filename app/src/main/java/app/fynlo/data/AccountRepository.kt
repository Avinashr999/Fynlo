package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class AccountRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allAccounts: Flow<List<Account>> = dao.getAllAccounts()
    val allSnapshots: Flow<List<NetWorthSnapshot>>
        get() = throw UnsupportedOperationException("Use getNetWorthSnapshots(pid) instead")

    suspend fun insertAccount(account: Account) = withContext(Dispatchers.IO) {
        val a = account.copy(updatedAt = System.currentTimeMillis())
        dao.insertAccount(a)
    }

    suspend fun getAccountByName(name: String): Account? = withContext(Dispatchers.IO) {
        dao.getAccountByName(name)
    }

    suspend fun getAccountById(id: String): Account? = withContext(Dispatchers.IO) {
        dao.getAccountById(id)
    }

    suspend fun updateAccountBalance(name: String, amount: Double) = withContext(Dispatchers.IO) {
        dao.updateAccountBalance(name, amount)
    }

    suspend fun deleteAccountById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteAccountById(id)
    }

    fun getNetWorthSnapshots(pid: String): Flow<List<NetWorthSnapshot>> =
        dao.getNetWorthSnapshots(pid)

    suspend fun insertNetWorthSnapshot(snapshot: NetWorthSnapshot) = withContext(Dispatchers.IO) {
        dao.insertNetWorthSnapshot(snapshot)
    }
}
