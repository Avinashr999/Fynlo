package app.fynlo

import android.app.Application
import android.util.Log
import androidx.room.Room
import app.fynlo.data.AuthManager
import app.fynlo.data.FinanceRepository
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.local.MIGRATION_3_4
import app.fynlo.data.local.MIGRATION_4_5
import app.fynlo.data.local.MIGRATION_5_6
import app.fynlo.data.local.MIGRATION_6_7
import app.fynlo.data.local.MIGRATION_7_8
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.notifications.ReminderScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FynloApplication : Application() {

    val database: FynloDatabase by lazy {
        Room.databaseBuilder(this, FynloDatabase::class.java, "Fynlo_database")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

            .fallbackToDestructiveMigrationOnDowngrade() // safety
            .build()
    }

    val authManager: AuthManager by lazy { AuthManager() }

    lateinit var firestoreRepository: FirestoreRepository
    lateinit var syncManager: SyncManager
    lateinit var repository: FinanceRepository

    // Supervised scope — child coroutine failures don't crash sibling coroutines or the app
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e("FynloApp", "Unhandled coroutine exception: ${e.message}", e)
        }
    )

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        // Crashlytics — auto-captures all crashes and sends to Firebase Console
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG) // only in release
        }

        ReminderScheduler.schedule(this)

        firestoreRepository = FirestoreRepository("")
        syncManager         = SyncManager("", database.dao())
        repository          = FinanceRepository(
            database.dao(), database, firestoreRepository, syncManager
        )

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
            syncManager         = SyncManager(uid, database.dao())
            repository.updateRemote(firestoreRepository, syncManager)
            syncManager.startListening()
        } catch (e: Exception) {
            Log.e("FynloApp", "initRemote setup failed: ${e.message}", e)
            return
        }

        appScope.launch {
            try {
                // Poll up to 20 seconds for Firestore to populate Room
                var waited = 0
                while (database.dao().getAllAccounts().first().isEmpty() && waited < 20000) {
                    kotlinx.coroutines.delay(500)
                    waited += 500
                }

                // Only normalize projectIds — never push on startup
                try {
                    val firstProject = database.dao().getAllProjects().first().firstOrNull()
                    if (firstProject != null && firstProject.id != "personal") {
                        val accounts = database.dao().getAllAccounts().first()
                        if (accounts.isNotEmpty()) {
                            repository.normalizeLegacyProjectIds(firstProject.id)
                            repository.pushNormalizedProjectIds()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FynloApp", "normalization failed (non-fatal): ${e.message}", e)
                }

                // Daily backup — fully isolated, never crashes the app
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
