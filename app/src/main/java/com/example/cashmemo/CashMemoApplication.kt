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

    // These are initialised AFTER auth so userId is never empty
    lateinit var firestoreRepository: FirestoreRepository
    lateinit var syncManager: SyncManager
    lateinit var repository: FinanceRepository

    override fun onCreate() {
        super.onCreate()

        // Step 1: build offline-capable repository immediately so the app
        // can open and show Room data without waiting for network.
        val placeholderFirestore = FirestoreRepository("")   // no-op until auth ready
        val placeholderSync      = SyncManager("", database.dao())
        repository = FinanceRepository(database.dao(), database, placeholderFirestore, placeholderSync)

        // Step 2: sign in anonymously, then swap in the real Firestore/Sync
        // instances on the same repository object.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authManager.ensureSignedIn()
                val uid = authManager.userId
                if (uid.isNotEmpty()) {
                    firestoreRepository = FirestoreRepository(uid)
                    syncManager         = SyncManager(uid, database.dao())
                    // Hot-swap the real instances into the repository
                    repository.updateRemote(firestoreRepository, syncManager)
                    syncManager.startListening()
                }
            }
        }
    }
}