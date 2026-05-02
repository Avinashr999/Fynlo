package com.example.cashmemo

import android.app.Application
import androidx.room.Room
import com.example.cashmemo.data.AuthManager
import com.example.cashmemo.data.FinanceRepository
import com.example.cashmemo.data.local.CashMemoDatabase
import com.example.cashmemo.data.local.MIGRATION_3_4
import com.example.cashmemo.data.local.MIGRATION_4_5
import com.example.cashmemo.data.local.MIGRATION_5_6
import com.example.cashmemo.data.local.MIGRATION_6_7
import com.example.cashmemo.data.remote.FirestoreRepository
import com.example.cashmemo.data.remote.SyncManager
import com.example.cashmemo.notifications.ReminderScheduler
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CashMemoApplication : Application() {

    val database: CashMemoDatabase by lazy {
        Room.databaseBuilder(this, CashMemoDatabase::class.java, "cashmemo_database")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
    }

    val authManager: AuthManager by lazy { AuthManager() }

    lateinit var firestoreRepository: FirestoreRepository
    lateinit var syncManager: SyncManager
    lateinit var repository: FinanceRepository

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        ReminderScheduler.schedule(this)

        firestoreRepository = FirestoreRepository("")
        syncManager         = SyncManager("", database.dao())
        repository          = FinanceRepository(
            database.dao(), database, firestoreRepository, syncManager
        )

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) initRemote(uid)
            }
        }
    }

    fun onGoogleSignInComplete(uid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { initRemote(uid) }
        }
    }

    fun initRemote(uid: String) {
        if (::syncManager.isInitialized && syncManager.userId.isNotEmpty()) {
            syncManager.stopListening()
        }
        firestoreRepository = FirestoreRepository(uid)
        syncManager         = SyncManager(uid, database.dao())
        repository.updateRemote(firestoreRepository, syncManager)
        syncManager.startListening()

        // SAFETY: Never push to Firestore on startup.
        // Firestore is the source of truth. The real-time listener
        // populates Room from Firestore automatically.
        // Pushes only happen when the user makes actual changes.
        //
        // We only run projectId normalization AFTER confirming
        // Firestore data has arrived in Room (accounts are non-empty).
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // Poll up to 20 seconds for Firestore to populate Room
                var waited = 0
                while (database.dao().getAllAccounts().first().isEmpty() && waited < 20000) {
                    kotlinx.coroutines.delay(500)
                    waited += 500
                }
                // Only normalize projectIds — never push on startup
                val firstProject = database.dao().getAllProjects().first().firstOrNull()
                if (firstProject != null && firstProject.id != "personal") {
                    val accounts = database.dao().getAllAccounts().first()
                    if (accounts.isNotEmpty()) {
                        repository.normalizeLegacyProjectIds(firstProject.id)
                        // After normalization, push ONLY the normalized projectId fields back
                        // (not a full push, just the records that changed)
                        repository.pushNormalizedProjectIds()
                    }
                }
                // Take a daily backup snapshot to protect against data loss
                repository.takeBackupIfNeeded(uid)
            }
        }
    }
}