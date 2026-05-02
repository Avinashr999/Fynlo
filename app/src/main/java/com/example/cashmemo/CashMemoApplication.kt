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
import kotlinx.coroutines.launch

class CashMemoApplication : Application() {

    val database: CashMemoDatabase by lazy {
        Room.databaseBuilder(this, CashMemoDatabase::class.java, "cashmemo_database")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    val authManager: AuthManager by lazy { AuthManager() }

    // Lateinit — only created AFTER Firebase is initialized in onCreate()
    lateinit var firestoreRepository: FirestoreRepository
    lateinit var syncManager: SyncManager
    lateinit var repository: FinanceRepository

    override fun onCreate() {
        super.onCreate()

        // Firebase MUST be initialized first before any Firebase class is touched
        FirebaseApp.initializeApp(this)

        // Now safe to create placeholder instances
        firestoreRepository = FirestoreRepository("")
        syncManager         = SyncManager("", database.dao())
        repository          = FinanceRepository(
            database.dao(), database, firestoreRepository, syncManager
        )

        // Sign in then swap in real instances
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) {
                        "Auth UID: $uid | isGoogle: ${authManager.isSignedInWithGoogle}")
                    initRemote(uid)
                }
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

        // Push all existing accounts to Firestore
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { repository.pushAllAccountsToFirestore() }
        }
    }
}