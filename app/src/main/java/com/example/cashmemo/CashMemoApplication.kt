package com.example.cashmemo

import android.app.Application
import androidx.room.Room
import com.example.cashmemo.data.AuthManager
import com.example.cashmemo.data.FinanceRepository
import com.example.cashmemo.data.local.CashMemoDatabase
import com.example.cashmemo.data.local.MIGRATION_3_4
import com.example.cashmemo.data.local.MIGRATION_4_5
import com.example.cashmemo.data.remote.FirestoreRepository
import com.example.cashmemo.data.remote.SyncManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CashMemoApplication : Application() {

    val database: CashMemoDatabase by lazy {
        Room.databaseBuilder(this, CashMemoDatabase::class.java, "cashmemo_database")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    val authManager: AuthManager by lazy { AuthManager() }

    lateinit var firestoreRepository: FirestoreRepository
    lateinit var syncManager: SyncManager
    lateinit var repository: FinanceRepository

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

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

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // Normalize legacy projectIds to real project UUID
                val firstProject = database.dao().getAllProjects().first().firstOrNull()
                if (firstProject != null && firstProject.id != "personal") {
                    repository.normalizeLegacyProjectIds(firstProject.id)
                }
                // Push all accounts to Firestore (catches missed balance updates)
                repository.pushAllAccountsToFirestore()
            }
        }
    }
}