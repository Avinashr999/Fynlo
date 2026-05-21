package app.fynlo

import android.app.Application
import android.util.Log
import app.fynlo.data.Analytics
import app.fynlo.data.AuthManager
import app.fynlo.data.FinanceRepository
import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.notifications.ReminderScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FynloApplication : Application() {

    @Inject lateinit var database: FynloDatabase
    @Inject lateinit var repository: FinanceRepository
    @Inject lateinit var dao: FynloDao

    val authManager: AuthManager by lazy { AuthManager() }

    lateinit var syncManager: SyncManager

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e("FynloApp", "Unhandled coroutine exception: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    )

    override fun onCreate() {
        super.onCreate()
        // Hilt injects @Inject fields during super.onCreate() via bytecode transformation

        // Kick off DataStore warm-up immediately on a background thread so the
        // first-frame synchronous reads (theme, onboarding/setup gates) hit a
        // warm cache instead of cold-opening the prefs file on the main thread.
        appScope.launch { app.fynlo.data.UserPreferences.warmUp(this@FynloApplication) }

        val startupTrace = FirebasePerformance.getInstance().newTrace("app_startup")
        startupTrace.start()

        FirebaseApp.initializeApp(this)

        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
            // Verification: enabled in DEBUG too so test crashes/non-fatals reach Firebase
            setCrashlyticsCollectionEnabled(true)
        }

        Analytics.init(this)

        syncManager = SyncManager("", dao)
        repository.updateRemote(FirestoreRepository(""), syncManager)

        startupTrace.stop()

        appScope.launch {
            // Scheduling periodic work initializes the WorkManager subsystem
            // (its own DB + executors). Kept OFF the main thread so it never
            // delays the first frame.
            try { ReminderScheduler.schedule(this@FynloApplication) } catch (e: Exception) {
                Log.e("FynloApp", "ReminderScheduler.schedule failed: ${e.message}")
            }
            try {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) initRemote(uid)
            } catch (e: Exception) {
                Log.e("FynloApp", "onCreate init failed: ${e.message}", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun triggerTestCrash(): Nothing = throw RuntimeException("Fynlo test crash - Crashlytics verification")

    fun onGoogleSignInComplete(uid: String) {
        appScope.launch {
            try {
                initRemote(uid)
            } catch (e: Exception) {
                Log.e("FynloApp", "onGoogleSignInComplete failed: ${e.message}", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun initRemote(uid: String) {
        if (uid.isBlank()) return
        try {
            if (::syncManager.isInitialized && syncManager.userId.isNotEmpty()) {
                syncManager.stopListening()
            }
            val newFirestore = FirestoreRepository(uid)
            syncManager = SyncManager(uid, dao)
            repository.updateRemote(newFirestore, syncManager)
            syncManager.startListening()
        } catch (e: Exception) {
            Log.e("FynloApp", "initRemote setup failed: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            return
        }

        appScope.launch {
            try {
                try {
                    repository.pushAllLocalToFirestore()
                    Log.d("FynloApp", "Local\u2192Firestore push completed on sign-in")
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
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }
}
