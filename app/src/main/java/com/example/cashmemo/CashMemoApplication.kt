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

    // Always initialised — starts with real UID if already signed in,
    // placeholder if not. initRemote() replaces them after auth.
    var firestoreRepository: FirestoreRepository = FirestoreRepository("")
    var syncManager: SyncManager = SyncManager("", database.dao())
    
    // Repository is created ONCE and never replaced.
    // firestore + syncManager inside it are swapped via updateRemote().
    val repository: FinanceRepository by lazy {
        FinanceRepository(database.dao(), database, firestoreRepository, syncManager)
    }

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) {
                    android.util.Log.d("CashMemoSync",
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
        // Stop old listeners if any
        if (syncManager.userId.isNotEmpty()) syncManager.stopListening()

        firestoreRepository = FirestoreRepository(uid)
        syncManager         = SyncManager(uid, database.dao())

        // Swap into the singleton repository so all writes go to right UID
        // and the status StateFlow updates correctly
        repository.updateRemote(firestoreRepository, syncManager)

        // Start listeners — first snapshot delivers ALL existing Firestore
        // docs as ADDED changes → writes them into Room → Room flows
        // emit → UI recomposes automatically (Room is reactive)
        syncManager.startListening()
    }
}