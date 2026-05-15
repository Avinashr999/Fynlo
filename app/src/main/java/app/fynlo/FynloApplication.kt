package app.fynlo

import android.app.Application
import android.util.Log
import app.fynlo.data.AuthManager
import app.fynlo.data.FinanceRepository
import app.fynlo.data.local.FynloDao
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.notifications.ReminderScheduler
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class with Hilt DI.
 *
 * All dependencies are injected by Hilt after super.onCreate() returns.
 * DO NOT access @Inject fields before onCreate() or in field initialisers —
 * that was the root cause of the lateinit crash.
 */
@HiltAndroidApp
class FynloApplication : Application() {

    // Hilt injects all of these after super.onCreate() completes
    @Inject lateinit var dao: FynloDao
    @Inject lateinit var repository: FinanceRepository
    @Inject lateinit var firestoreRepository: FirestoreRepository

    // AuthManager has no Hilt dependency — keep as lazy
    val authManager: AuthManager by lazy { AuthManager() }

    // SyncManager is stateful (holds userId) — we create it after sign-in
    lateinit var syncManager: SyncManager

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e("FynloApp", "Unhandled coroutine exception: ${e.message}", e)
        }
    )

    override fun onCreate() {
        super.onCreate()
        // All @Inject fields are now populated — safe to use dao, repository, firestoreRepository

        FirebaseApp.initializeApp(this)

        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }

        ReminderScheduler.schedule(this)

        // Initialise SyncManager with empty userId — updated when user signs in
        syncManager = SyncManager("", dao)
        repository.updateRemote(firestoreRepository, syncManager)

        appScope.launch {
            try {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) initRemote(uid)
            } catch (e: Exception) {
                Log.e("FynloApp", "onCreate init failed: ${e.message}", e)
            }
        }
    }

    fun onGoogleSignInComplete(uid: String) {
        appScope.launch {
            try {
                initRemote(uid)
            } catch (e: Exception) {
                Log.e("FynloApp", "onGoogleSignInComplete failed: ${e.message}", e)
            }
        }
    }

    fun initRemote(uid: String) {
        if (uid.isBlank()) return
        try {
            if (::syncManager.isInitialized && syncManager.userId.isNotEmpty()) {
                syncManager.stopListening()
            }
            firestoreRepository = FirestoreRepository(uid)
            syncManager = SyncManager(uid, dao)
            repository.updateRemote(firestoreRepository, syncManager)
            syncManager.startListening()
        } catch (e: Exception) {
            Log.e("FynloApp", "initRemote setup failed: ${e.message}", e)
            return
        }

        appScope.launch {
            try {
                try {
                    repository.pushAllLocalToFirestore()
                    Log.d("FynloApp", "Local→Firestore push completed on sign-in")
                } catch (e: Exception) {
                    Log.e("FynloApp", "pushAllLocal failed (non-fatal): ${e.message}")
                }

                var waited = 0
                while (dao.getAllAccounts().first().isEmpty() && waited < 20000) {
                    kotlinx.coroutines.delay(500)
                    waited += 500
                }

                try {
                    val allProjects = dao.getAllProjects().first()
                    val firstProject = allProjects.firstOrNull()

                    if (allProjects.isEmpty()) {
                        val orphanedPid = dao.getAllAccounts().first()
                            .firstOrNull()?.projectId?.takeIf { it.isNotBlank() && it != "personal" }
                            ?: dao.getAllBorrowers().first()
                            .firstOrNull()?.projectId?.takeIf { it.isNotBlank() && it != "personal" }

                        val recoveryProject = app.fynlo.data.model.Project(
                            id        = orphanedPid ?: "personal",
                            name      = "Personal",
                            icon      = "person",
                            color     = "#3b82f6",
                            currency  = "INR",
                            createdAt = ""
                        )
                        dao.insertProject(recoveryProject)
                        Log.e("FynloApp", "Recovered missing project record: ${recoveryProject.id}")
                    } else if (firstProject != null && firstProject.id != "personal") {
                        val accounts = dao.getAllAccounts().first()
                        if (accounts.isNotEmpty()) {
                            repository.normalizeLegacyProjectIds(firstProject.id)
                            repository.pushNormalizedProjectIds()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FynloApp", "normalization failed (non-fatal): ${e.message}", e)
                }

                try {
                    repository.takeBackupIfNeeded(uid)
                } catch (e: Exception) {
                    Log.e("FynloApp", "backup failed (non-fatal): ${e.message}", e)
                }

            } catch (e: Exception) {
                Log.e("FynloApp", "initRemote coroutine failed: ${e.message}", e)
            }
        }
    }
}
