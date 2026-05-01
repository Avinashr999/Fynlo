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

    lateinit var firestoreRepository: FirestoreRepository
    lateinit var syncManager: SyncManager
    lateinit var repository: FinanceRepository

    override fun onCreate() {
        super.onCreate()

        // Build offline-capable repository immediately
        val placeholderFirestore = FirestoreRepository("")
        val placeholderSync      = SyncManager("", database.dao())
        repository = FinanceRepository(
            database.dao(), database, placeholderFirestore, placeholderSync
        )

        // Sign in (anonymous if no Google account yet) then start sync
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) {
                    android.util.Log.d("CashMemoSync", "Auth UID: $uid | isGoogle: ${authManager.isSignedInWithGoogle}")
                    initRemote(uid)
                }
            }
        }
    }

    /** Called after Google Sign-In completes — re-init with real Google UID. */
    fun onGoogleSignInComplete(uid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { initRemote(uid) }
        }
    }

    private fun initRemote(uid: String) {
        if (::syncManager.isInitialized) syncManager.stopListening()
        firestoreRepository = FirestoreRepository(uid)
        syncManager         = SyncManager(uid, database.dao())
        repository.updateRemote(firestoreRepository, syncManager)
        syncManager.startListening()
    }
}