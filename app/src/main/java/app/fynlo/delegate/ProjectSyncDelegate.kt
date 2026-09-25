package app.fynlo.delegate

import android.content.Context
import androidx.core.content.edit
import app.fynlo.data.model.Project
import app.fynlo.data.model.Account
import app.fynlo.data.model.Transaction
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Investment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay

class ProjectSyncDelegate(private val ctx: ViewModelContext) {

    val isPrivacyMode: StateFlow<Boolean> = app.fynlo.data.UserPreferences.privacyModeEnabled(ctx.context)
        .stateIn(ctx.scope, SharingStarted.WhileSubscribed(5000), false)

    fun togglePrivacyMode() {
        ctx.scope.launch {
            app.fynlo.data.UserPreferences.setPrivacyModeEnabled(ctx.context, !isPrivacyMode.value)
        }
    }

    private val _currentProjectId = MutableStateFlow("")
    val currentProjectId: StateFlow<String> = _currentProjectId.asStateFlow()

    val projects = ctx.repository.allProjects
        .stateIn(ctx.scope, SharingStarted.Eagerly, emptyList())

    private val _isSyncReady = MutableStateFlow(false)
    val isSyncReady: StateFlow<Boolean> = _isSyncReady.asStateFlow()

    init {
        ctx.scope.launch(Dispatchers.IO) {
            projects.collect { list ->
                if (_currentProjectId.value.isEmpty() && list.isNotEmpty()) {
                    _currentProjectId.value = list.first().id
                }
                if (list.isNotEmpty()) {
                    _isSyncReady.value = true
                }
            }
        }
        ctx.scope.launch(Dispatchers.IO) {
            delay(30_000)
            if (!_isSyncReady.value) {
                _isSyncReady.value = true
            }
        }
    }

    val currentProject: StateFlow<Project?> = combine(projects, _currentProjectId) { list, pid ->
        list.find { it.id == pid }
    }.stateIn(ctx.scope, SharingStarted.Eagerly, null)

    fun switchProject(projectId: String) { _currentProjectId.value = projectId }

    fun createProject(project: Project) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.insertProject(project) }
    }

    fun deleteProject(project: Project) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.deleteProject(project)
            if (_currentProjectId.value == project.id) _currentProjectId.value = "personal"
        }
    }
    
    fun restoreRealData() {
        ctx.scope.launch(Dispatchers.IO) {
            val uid = ctx.repository.syncManager.userId
            if (uid.isBlank()) return@launch
            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            try {
                val txDocs = fs.collection("users").document(uid).collection("transactions").get().await()
                txDocs.documents.forEach { it.reference.delete().await() }
                ctx.repository.dao.deleteAllTransactions()
            } catch (e: Exception) { android.util.Log.e("Restore", "txn: ${e.message}") }

            val cashAccount = Account(
                id = "1", name = "Personal Cash", balance = 3962.0, type = "Cash"
            )
            val hdfcAccount = Account(
                id = "2", name = "HDFC Bank", balance = 122500.0, type = "Bank"
            )
            try {
                ctx.repository.dao.insertAccount(cashAccount)
                ctx.repository.dao.insertAccount(hdfcAccount)
                fs.collection("users").document(uid).collection("accounts")
                    .document("1").set(mapOf("id" to "1", "name" to "Personal Cash", "balance" to 3962.0, "type" to "Cash", "projectId" to "personal", "updatedAt" to System.currentTimeMillis())).await()
                fs.collection("users").document(uid).collection("accounts")
                    .document("2").set(mapOf("id" to "2", "name" to "HDFC Bank", "balance" to 122500.0, "type" to "Bank", "projectId" to "personal", "updatedAt" to System.currentTimeMillis())).await()
            } catch (e: Exception) { android.util.Log.e("Restore", "accounts: ${e.message}") }
        }
    }

    fun cleanupSeeederData() {
        ctx.scope.launch(Dispatchers.IO) {
            val uid = ctx.repository.syncManager.userId
            if (uid.isBlank()) return@launch
            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            try {
                val invDocs = fs.collection("users").document(uid).collection("investments").get().await()
                invDocs.documents.forEach { it.reference.delete().await() }
                ctx.repository.dao.deleteAllInvestments()
            } catch (e: Exception) { android.util.Log.e("Cleanup", "inv: ${e.message}") }

            try {
                val bDocs = fs.collection("users").document(uid).collection("borrowers").get().await()
                bDocs.documents.forEach { it.reference.delete().await() }
                ctx.repository.dao.deleteAllBorrowers()
            } catch (e: Exception) { android.util.Log.e("Cleanup", "borrowers: ${e.message}") }

            try {
                val txDocs = fs.collection("users").document(uid).collection("transactions").get().await()
                val seederDescs = setOf("Monthly Salary", "Grocery & Dining", "Petrol & Diesel",
                    "ATM Withdrawal", "Online Shopping", "Web Design Project",
                    "Miscellaneous", "Auto fuel", "Part time work",
                    "Lent to Ravi Kumar", "Lent to Suresh Babu", "Lent to Lakshmi Devi", "Lent to Mohan Rao",
                    "Loan from Home Loan EMI", "Loan from Personal Loan", "Loan from Gold Loan",
                    "Invested in Gold ETF", "Invested in LIC Policy", "Invested in FD - HDFC", "Invested in Mutual Fund SIP")
                txDocs.documents.forEach { doc ->
                    val desc = doc.getString("desc") ?: ""
                    if (desc in seederDescs) {
                        doc.reference.delete().await()
                        ctx.repository.dao.deleteTransactionById(doc.id)
                    }
                }
            } catch (e: Exception) { android.util.Log.e("Cleanup", "txn: ${e.message}") }

            try {
                val debtDocs = fs.collection("users").document(uid).collection("debts").get().await()
                debtDocs.documents.forEach { it.reference.delete().await() }
                ctx.repository.dao.deleteAllDebts()
            } catch (e: Exception) { android.util.Log.e("Cleanup", "debts: ${e.message}") }

            try {
                val seederAccIds = setOf("acc-cash", "acc-hdfc", "acc-sbi", "acc-petty")
                seederAccIds.forEach { id ->
                    fs.collection("users").document(uid).collection("accounts").document(id).delete().await()
                    ctx.repository.dao.deleteAccountById(id)
                }
            } catch (e: Exception) { android.util.Log.e("Cleanup", "accounts: ${e.message}") }
        }
    }

    fun loadDummyData() {
        ctx.scope.launch(Dispatchers.IO) {
            val seeder = app.fynlo.logic.DummyDataSeeder
            ctx.repository.dao.deleteAllTransactions()
            ctx.repository.dao.deleteAllBorrowers()
            ctx.repository.dao.deleteAllDebts()
            ctx.repository.dao.deleteAllInvestments()
            ctx.repository.dao.deleteAllAccounts()

            val collections = listOf("transactions","borrowers","debts","investments","accounts","payments","debt_payments")
            val uid = ctx.repository.syncManager.userId
            if (uid.isNotBlank()) {
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                collections.forEach { col ->
                    try {
                        val docs = fs.collection("users").document(uid).collection(col).get().await()
                        docs.documents.forEach { it.reference.delete() }
                    } catch (e: Exception) {
                        android.util.Log.e("Seeder", "Clear $col failed: ${e.message}")
                    }
                }
            }

            seeder.accounts().forEach { ctx.repository.upsertAccount(it) }
            seeder.borrowers().forEach { b ->
                ctx.repository.dao.insertBorrower(b)
                ctx.repository.sync { setBorrower(b) }
            }
            seeder.debts().forEach { d ->
                ctx.repository.dao.insertDebt(d)
                ctx.repository.sync { setDebt(d) }
            }
            seeder.investments().forEach { i ->
                ctx.repository.dao.insertInvestment(i)
                ctx.repository.sync { setInvestment(i) }
            }
            seeder.transactions().forEach { t ->
                ctx.repository.dao.insertTransaction(t)
                ctx.repository.sync { setTransaction(t) }
            }
            seeder.budgets().forEach { b ->
                ctx.repository.dao.insertBudget(b)
            }
        }
    }
    
    fun populateDummyData() {
        ctx.scope.launch(Dispatchers.IO) {
            val pid = ctx.currentProjectId()
            val today = ctx.today()
            val cash = Account("1", "Personal Cash", "Cash",  5000.0,  projectId = pid)
            val bank = Account("2", "HDFC Bank",    "Bank",  45000.0, projectId = pid)
            ctx.repository.insertAccount(cash)
            ctx.repository.insertAccount(bank)

            val borrower1 = Borrower(
                "b1", "John Doe", amount = 10000.0, rate = 2.0,
                date = "2024-01-10", due = "2024-12-31", intType = "Simple Interest",
                sourceAccount = "Personal Cash",
                notes = "Personal loan for home renovation.", projectId = pid
            )
            ctx.repository.insertBorrowerWithSource(borrower1, "Personal Cash", pid)

            val gold = Investment(
                "i1", "Gold Coins", "Gold", invested = 50000.0, currentVal = 58000.0,
                date = "2023-05-20", notes = "Bought from Tanishq. 24 Karat, 10gm.", projectId = pid
            )
            val stocks = Investment(
                "i2", "Nifty 50 ETF", "Stocks", invested = 20000.0, currentVal = 22500.0,
                date = "2024-02-15", notes = "Long term wealth creation.", projectId = pid
            )
            ctx.repository.insertInvestmentWithSource(gold,   "HDFC Bank", pid)
            ctx.repository.insertInvestmentWithSource(stocks, "HDFC Bank", pid)

            val debt1 = Debt(
                "d1", "Home Loan", amount = 150000.0, rate = 8.5,
                date = "2023-01-01", paid = 20000.0,
                notes = "Monthly EMI ?2500. 15 years tenure.", projectId = pid
            )
            ctx.repository.insertDebtWithDestination(debt1, "HDFC Bank", pid)

            listOf(
                Transaction("t1", today, "Expense", 1200.0, fromAcct = "Personal Cash", category = "Food",     notes = "Dinner at Barbeque Nation.", projectId = pid),
                Transaction("t2", today, "Expense", 2500.0, fromAcct = "HDFC Bank",    category = "Fuel",     notes = "Full tank refill.",           projectId = pid),
                Transaction("t3", today, "Income",  60000.0, toAcct  = "HDFC Bank",    category = "Salary",   notes = "April 2024 Salary.",           projectId = pid),
                Transaction("t4", today, "Expense", 800.0,  fromAcct = "Personal Cash", category = "Shopping", notes = "New charger cable.",           projectId = pid)
            ).forEach { ctx.repository.insertTransaction(it) }
        }
    }
    
    fun refresh(onComplete: () -> Unit) {
        ctx.scope.launch {
            ctx.repository.syncManager.setSyncing()
            delay(900)
            ctx.repository.syncManager.setSynced()
            onComplete()
        }
    }
    
    fun wipeAllData() {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.wipeAllData() }
    }
    
    fun resetCloudSyncToLocal(onComplete: (Boolean) -> Unit) {
        ctx.scope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.repository.resetCloudSyncToLocal() }.isSuccess
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    fun resetAllData(
        context: Context,
        authManager: app.fynlo.data.AuthManager,
        onComplete: () -> Unit
    ) {
        ctx.scope.launch(Dispatchers.IO) {
            runCatching { ctx.repository.resetAllData(context) }
                .onFailure { android.util.Log.e("Reset", "resetAllData failed: ${it.message}") }

            runCatching {
                app.fynlo.data.UserPreferences.clearAll(context)
                app.fynlo.data.UserPreferences.setSetupDone(context, false)
                app.fynlo.data.UserPreferences.setOnboardingDone(context, false)
            }

            runCatching {
                app.fynlo.data.PinManager(context).clearPinSync()
                context.getSharedPreferences("fynlo_prefs", Context.MODE_PRIVATE)
                    .edit(commit = true) { clear() }
                context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                    .edit(commit = true) { clear() }
            }

            runCatching { authManager.signOut() }
            runCatching { androidx.work.WorkManager.getInstance(context).cancelAllWork() }

            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun deleteAccountPermanently(
        authManager: app.fynlo.data.AuthManager,
        onResult: (Boolean) -> Unit
    ) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.wipeAllData()
            val deleted = authManager.deleteAccount().isSuccess
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(deleted) }
        }
    }
}
