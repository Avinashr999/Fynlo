package app.fynlo.data

import android.util.Log
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class AccountRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allAccounts: Flow<List<Account>> = dao.getAllAccounts()

    private inline fun <T> recordOnFail(op: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Log.e("AccountRepo", "$op failed: ${e.message}", e)
        FirebaseCrashlytics.getInstance().apply {
            log("AccountRepository.$op failed")
            recordException(e)
        }
        throw e
    }

    suspend fun insertAccount(account: Account) = withContext(Dispatchers.IO) {
        recordOnFail("insertAccount") {
            val a = account.copy(updatedAt = System.currentTimeMillis())
            dao.insertAccount(a)
        }
    }

    suspend fun getAccountByName(name: String): Account? = withContext(Dispatchers.IO) {
        recordOnFail("getAccountByName") { dao.getAccountByName(name) }
    }

    suspend fun getAccountById(id: String): Account? = withContext(Dispatchers.IO) {
        recordOnFail("getAccountById") { dao.getAccountById(id) }
    }

    suspend fun updateAccountBalance(name: String, amount: Double) = withContext(Dispatchers.IO) {
        recordOnFail("updateAccountBalance") { dao.updateAccountBalance(name, amount) }
    }

    suspend fun deleteAccountById(id: String) = withContext(Dispatchers.IO) {
        recordOnFail("deleteAccountById") { dao.deleteAccountById(id) }
    }

    fun getNetWorthSnapshots(pid: String): Flow<List<NetWorthSnapshot>> =
        dao.getNetWorthSnapshots(pid)

    suspend fun insertNetWorthSnapshot(snapshot: NetWorthSnapshot) = withContext(Dispatchers.IO) {
        recordOnFail("insertNetWorthSnapshot") { dao.insertNetWorthSnapshot(snapshot) }
    }
}
