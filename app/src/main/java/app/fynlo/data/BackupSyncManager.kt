package app.fynlo.data

import androidx.room.withTransaction
import app.fynlo.data.model.BackupData
// BackupIntegrity is in the same package (app.fynlo.data)
import app.fynlo.data.remote.deleteFirestoreUserTree
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

internal class BackupSyncManager(
    private val ctx: RepositoryContext,
    private val helper: RepositoryHelper
) {
    suspend fun resetCloudSyncToLocal() {
        runCatching { ctx.syncManager.stopListening() }
        val uid = ctx.syncManager.userId
        if (uid.isNotBlank()) {
            deleteFirestoreUserTree(
                com.google.firebase.firestore.FirebaseFirestore.getInstance(), uid
            )
        }
        pushAllLocalToFirestore()
        runCatching { ctx.syncManager.startListening() }
    }

    suspend fun pushAllLocalToFirestore() {
        val uid = ctx.syncManager.userId
        if (uid.isEmpty()) return
        val fs = ctx.firestoreRepo
        var pushed = 0
        var failed = 0

        suspend fun push(label: String, block: suspend () -> Unit) {
            runCatching { block() }
                .onSuccess { pushed++ }
                .onFailure { e ->
                    failed++
                    android.util.Log.e("FynloSync", "Push failed [$label]: ${e.message}")
                }
        }

        ctx.dao.getAllBorrowers().first().forEach    { push("borrower:${it.id}")    { fs.setBorrower(it) } }
        ctx.dao.getAllTransactions().first().forEach { push("txn:${it.id}")         { fs.setTransaction(it) } }
        ctx.dao.getAllAccounts().first().forEach     { push("account:${it.id}")     { fs.setAccount(it) } }
        ctx.dao.getAllInvestments().first().forEach  { push("investment:${it.id}")  { fs.setInvestment(it) } }
        ctx.dao.getAllDebts().first().forEach        { push("debt:${it.id}")        { fs.setDebt(it) } }
        ctx.dao.getAllPayments().first().forEach     { push("payment:${it.id}")     { fs.setPayment(it) } }
        ctx.dao.getAllDebtPayments().first().forEach { push("debtPay:${it.id}")     { fs.setDebtPayment(it) } }
        ctx.dao.getAllPeople().first().forEach       { push("person:${it.id}")      { fs.setPerson(it) } }
        ctx.dao.getAllBudgets().first().forEach      { push("budget:${it.category}") { fs.setBudget(it) } }
        ctx.dao.getAllGoals().first().forEach        { push("goal:${it.id}")        { fs.setGoal(it) } }
        ctx.dao.getAllProjects().first().forEach     { push("project:${it.id}")     { fs.setProject(it) } }
        ctx.dao.getAllRecurringTransactionsOnce().forEach { push("recurring:${it.id}") { fs.setRecurring(it) } }
        ctx.dao.getAllValuationsOnce().forEach       { push("valuation:${it.id}")   { fs.setValuation(it) } }
        ctx.dao.getAllAuditEventsOnce().forEach      { push("audit:${it.id}")       { fs.setAuditEvent(it) } }

        if (failed == 0) ctx.syncManager.setSynced() else ctx.syncManager.setSyncing()
    }

    suspend fun normalizeLegacyProjectIds(realProjectId: String) {
        ctx.db.withTransaction {
            ctx.dao.normalizeAccountProjectIds(realProjectId)
            ctx.dao.normalizeTransactionProjectIds(realProjectId)
            ctx.dao.normalizeBorrowerProjectIds(realProjectId)
            ctx.dao.normalizeInvestmentProjectIds(realProjectId)
            ctx.dao.normalizeDebtProjectIds(realProjectId)
            ctx.dao.normalizePeopleProjectIds(realProjectId)
            ctx.dao.normalizePaymentProjectIds(realProjectId)
            ctx.dao.normalizeDebtPaymentProjectIds(realProjectId)
            ctx.dao.normalizeBudgetProjectIds(realProjectId)
            ctx.dao.normalizeGoalProjectIds(realProjectId)
        }
    }

    suspend fun pushAllAccountsToFirestore() {
        val accounts = ctx.dao.getAllAccounts().first()
        accounts.forEach { account ->
            runCatching { ctx.firestoreRepo.setAccount(account) }
        }
    }

    suspend fun pushAllCollectionsToFirestore() {
        val accounts      = ctx.dao.getAllAccounts().first()
        val transactions  = ctx.dao.getAllTransactions().first()
        val borrowers     = ctx.dao.getAllBorrowers().first()
        val investments   = ctx.dao.getAllInvestments().first()
        val debts         = ctx.dao.getAllDebts().first()
        val people        = ctx.dao.getAllPeople().first()
        accounts.forEach     { runCatching { ctx.firestoreRepo.setAccount(it) } }
        transactions.forEach { runCatching { ctx.firestoreRepo.setTransaction(it) } }
        borrowers.forEach    { runCatching { ctx.firestoreRepo.setBorrower(it) } }
        investments.forEach  { runCatching { ctx.firestoreRepo.setInvestment(it) } }
        debts.forEach        { runCatching { ctx.firestoreRepo.setDebt(it) } }
        people.forEach       { runCatching { ctx.firestoreRepo.setPerson(it) } }
    }

    suspend fun pushNormalizedProjectIds() {
        val accounts     = ctx.dao.getAllAccounts().first()
        val borrowers    = ctx.dao.getAllBorrowers().first()
        val transactions = ctx.dao.getAllTransactions().first()
        val debts        = ctx.dao.getAllDebts().first()
        val investments  = ctx.dao.getAllInvestments().first()
        accounts.filter    { it.projectId != "personal" }.forEach { runCatching { ctx.firestoreRepo.setAccount(it) } }
        borrowers.filter   { it.projectId != "personal" }.forEach { runCatching { ctx.firestoreRepo.setBorrower(it) } }
        transactions.filter{ it.projectId != "personal" }.forEach { runCatching { ctx.firestoreRepo.setTransaction(it) } }
        debts.filter       { it.projectId != "personal" }.forEach { runCatching { ctx.firestoreRepo.setDebt(it) } }
        investments.filter { it.projectId != "personal" }.forEach { runCatching { ctx.firestoreRepo.setInvestment(it) } }
    }

    suspend fun takeBackupIfNeeded(uid: String) {
        if (uid.isBlank()) return
        try {
            val accounts = ctx.dao.getAllAccounts().first()
            if (accounts.isEmpty()) return

            val today   = java.time.LocalDate.now().toString()
            val fs      = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val metaRef = fs.collection("users").document(uid)
                           .collection("backup_meta").document("last_backup")

            val lastBackup = try { metaRef.get().await().getString("date") ?: "" }
                            catch (e: Exception) { "" }
            if (lastBackup == today) return

            val borrowers    = try { ctx.dao.getAllBorrowers().first() }   catch (e: Exception) { emptyList() }
            val transactions = try { ctx.dao.getAllTransactions().first() } catch (e: Exception) { emptyList() }
            val debts        = try { ctx.dao.getAllDebts().first() }        catch (e: Exception) { emptyList() }
            val investments  = try { ctx.dao.getAllInvestments().first() }  catch (e: Exception) { emptyList() }

            val backupRef = fs.collection("users").document(uid)
                             .collection("backups").document(today)

            backupRef.set(mapOf(
                "date"          to today,
                "netWorth"      to accounts.sumOf { it.balance },
                "accountCount"  to accounts.size,
                "txnCount"      to transactions.size,
                "borrowerCount" to borrowers.size,
                "debtCount"     to debts.size,
                "investCount"   to investments.size,
                "accountNames"  to accounts.map { it.name },
                "accountBals"   to accounts.map { it.balance },
                "createdAt"     to System.currentTimeMillis()
            )).await()

            metaRef.set(mapOf("date" to today)).await()

        } catch (e: Exception) {
            android.util.Log.e("Backup", "takeBackupIfNeeded failed: ${e.message}", e)
        }
    }

    suspend fun wipeAllData() {
        val uid = ctx.syncManager.userId
        if (uid.isBlank()) return
        val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userDoc = fs.collection("users").document(uid)

        val collections = listOf(
            "accounts", "transactions", "borrowers", "investments", "debts",
            "people", "payments", "debt_payments", "budgets", "goals",
            "projects", "recurring_transactions", "backup_meta", "backups",
            "net_worth_snapshots", "investment_valuations", "audit_events"
        )

        ctx.db.withTransaction {
            ctx.dao.deleteAllAccounts()
            ctx.dao.deleteAllTransactions()
            ctx.dao.deleteAllBorrowers()
            ctx.dao.deleteAllPayments()
            ctx.dao.deleteAllInvestments()
            ctx.dao.deleteAllDebts()
            ctx.dao.deleteAllDebtPayments()
            ctx.dao.deleteAllPeople()
            ctx.dao.deleteAllProjects()
            ctx.dao.deleteAllBudgets()
            ctx.dao.deleteAllGoals()
            ctx.dao.deleteAllAuditEvents()
            ctx.dao.deleteAllDeletedRemoteDocs()
            ctx.dao.deleteAllMonthlyCloses()
            ctx.dao.deleteAllProofAttachments()
            ctx.dao.deleteAllUndoActions()
            ctx.dao.deleteAllSyncConflicts()
            ctx.dao.deleteAllValuations()
            ctx.dao.deleteAllRecurringTransactions()
        }

        collections.forEach { colName ->
            try {
                val snapshot = userDoc.collection(colName).get().await()
                snapshot.documents.forEach { it.reference.delete().await() }
            } catch (e: Exception) {
                android.util.Log.e("Wipe", "Failed to wipe $colName: ${e.message}")
            }
        }
    }

    suspend fun resetAllData(context: android.content.Context) {
        runCatching { ctx.syncManager.stopListening() }

        val uid = ctx.syncManager.userId
        if (uid.isNotBlank()) {
            deleteFirestoreUserTree(
                com.google.firebase.firestore.FirebaseFirestore.getInstance(), uid
            )
        }

        try {
            ctx.db.withTransaction {
                ctx.dao.deleteAllAccounts()
                ctx.dao.deleteAllTransactions()
                ctx.dao.deleteAllBorrowers()
                ctx.dao.deleteAllPayments()
                ctx.dao.deleteAllInvestments()
                ctx.dao.deleteAllDebts()
                ctx.dao.deleteAllDebtPayments()
                ctx.dao.deleteAllPeople()
                ctx.dao.deleteAllProjects()
                ctx.dao.deleteAllBudgets()
                ctx.dao.deleteAllGoals()
                ctx.dao.deleteAllAuditEvents()
                ctx.dao.deleteAllDeletedRemoteDocs()
                ctx.dao.deleteAllMonthlyCloses()
                ctx.dao.deleteAllProofAttachments()
                ctx.dao.deleteAllUndoActions()
                ctx.dao.deleteAllSyncConflicts()
                ctx.dao.deleteAllValuations()
                ctx.dao.deleteAllRecurringTransactions()
                ctx.dao.deleteAllNetWorthSnapshots()
                ctx.dao.deleteAllFlowTemplates()
            }
        } catch (e: Exception) {
            android.util.Log.e("Reset", "DAO clear failed — deleting database file: ${e.message}")
            runCatching { ctx.db.close() }
            runCatching { context.deleteDatabase("Fynlo_database") }
        }
    }

    suspend fun getAllDataAsJson(userId: String = ""): String {
        val draft = BackupData(
            schemaVersion = BackupIntegrity.CURRENT_SCHEMA_VERSION,
            appVersion    = app.fynlo.BuildConfig.VERSION_NAME,
            exportedAt    = java.time.Instant.now().toString(),
            userId        = userId,
            deviceName    = android.os.Build.MODEL ?: "",
            contentHash   = "",
            accounts              = ctx.dao.getAllAccounts().first(),
            transactions          = ctx.dao.getAllTransactions().first(),
            borrowers             = ctx.dao.getAllBorrowers().first(),
            investments           = ctx.dao.getAllInvestments().first(),
            debts                 = ctx.dao.getAllDebts().first(),
            people                = ctx.dao.getAllPeople().first(),
            projects              = ctx.dao.getAllProjects().first(),
            payments              = ctx.dao.getAllPayments().first(),
            debtPayments          = ctx.dao.getAllDebtPayments().first(),
            budgets               = ctx.dao.getAllBudgets().first(),
            goals                 = ctx.dao.getAllGoals().first(),
            recurringTransactions = ctx.dao.getAllRecurringTransactionsOnce(),
            monthlyCloses         = ctx.dao.getAllMonthlyCloses().first(),
            proofAttachments      = ctx.dao.getAllProofAttachments().first(),
        )
        val hash = BackupIntegrity.computeHash(draft)
        return Json.encodeToString(draft.copy(contentHash = hash))
    }

    suspend fun restoreDataFromJson(json: String) {
        val raw = Json.decodeFromString<BackupData>(json)
        when (val verdict = BackupIntegrity.check(raw)) {
            is BackupIntegrity.Check.Ok -> { }
            is BackupIntegrity.Check.UnsupportedVersion ->
                throw IllegalStateException(
                    "Backup format v${verdict.version} is newer than this app supports. Update Fynlo Ledger."
                )
            is BackupIntegrity.Check.HashMismatch ->
                throw IllegalStateException(
                    "Backup integrity check failed (SHA-256 hash mismatch)."
                )
        }
        val data = sanitizeLegacyCashName(raw)
        ctx.db.withTransaction {
            ctx.dao.deleteAllAccounts(); ctx.dao.deleteAllTransactions(); ctx.dao.deleteAllBorrowers()
            ctx.dao.deleteAllInvestments(); ctx.dao.deleteAllDebts(); ctx.dao.deleteAllPeople(); ctx.dao.deleteAllProjects()
            ctx.dao.deleteAllPayments(); ctx.dao.deleteAllDebtPayments(); ctx.dao.deleteAllBudgets(); ctx.dao.deleteAllGoals()
            ctx.dao.deleteAllAuditEvents()
            ctx.dao.deleteAllMonthlyCloses(); ctx.dao.deleteAllProofAttachments(); ctx.dao.deleteAllUndoActions(); ctx.dao.deleteAllSyncConflicts()
            data.accounts.forEach { ctx.dao.insertAccount(it) }; data.transactions.forEach { ctx.dao.insertTransaction(it) }
            data.borrowers.forEach { ctx.dao.insertBorrower(it) }; data.investments.forEach { ctx.dao.insertInvestment(it) }
            data.debts.forEach { ctx.dao.insertDebt(it) }; data.people.forEach { ctx.dao.insertPerson(it) }
            data.projects.forEach { ctx.dao.insertProject(it) }
            data.payments.forEach { ctx.dao.insertPayment(it) }; data.debtPayments.forEach { ctx.dao.insertDebtPayment(it) }
            data.budgets.forEach { ctx.dao.insertBudget(it) }; data.goals.forEach { ctx.dao.insertGoal(it) }
            data.recurringTransactions.forEach { ctx.dao.insertRecurringTransaction(it) }
            data.monthlyCloses.forEach { ctx.dao.insertMonthlyClose(it) }
            data.proofAttachments.forEach { ctx.dao.insertProofAttachment(it) }
            helper.recordAudit(
                action = "RESTORE",
                entityType = "backup",
                entityId = "restore",
                title = "Backup restored",
                afterValue = "accounts=${data.accounts.size}:transactions=${data.transactions.size}:loans=${data.borrowers.size}:debts=${data.debts.size}:investments=${data.investments.size}",
                projectId = data.projects.firstOrNull()?.id ?: "personal",
            )
        }
    }

    private fun sanitizeLegacyCashName(input: BackupData): BackupData {
        val OLD = "Cash in Hand"
        val NEW = "Personal Cash"
        if (!input.accounts.any { it.name == OLD } &&
            !input.transactions.any { it.fromAcct == OLD || it.toAcct == OLD } &&
            !input.borrowers.any { it.sourceAccount == OLD } &&
            !input.recurringTransactions.any { it.fromAcct == OLD || it.toAcct == OLD }) {
            return input
        }
        return input.copy(
            accounts              = input.accounts.map { if (it.name == OLD) it.copy(name = NEW) else it },
            transactions          = input.transactions.map { t ->
                t.copy(
                    fromAcct = if (t.fromAcct == OLD) NEW else t.fromAcct,
                    toAcct   = if (t.toAcct   == OLD) NEW else t.toAcct,
                )
            },
            borrowers             = input.borrowers.map { if (it.sourceAccount == OLD) it.copy(sourceAccount = NEW) else it },
            recurringTransactions = input.recurringTransactions.map { r ->
                r.copy(
                    fromAcct = if (r.fromAcct == OLD) NEW else r.fromAcct,
                    toAcct   = if (r.toAcct   == OLD) NEW else r.toAcct,
                )
            },
        )
    }
}
