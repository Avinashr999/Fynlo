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

    val firestoreRepository: FirestoreRepository by lazy {
        FirestoreRepository(authManager.userId)
    }

    val syncManager: SyncManager by lazy {
        SyncManager(authManager.userId, database.dao())
    }

    val repository: FinanceRepository by lazy {
        FinanceRepository(database.dao(), database, firestoreRepository, syncManager)
    }

    override fun onCreate() {
        super.onCreate()
        // Sign in anonymously then start Firestore listeners
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                authManager.ensureSignedIn()
                // Re-init with real userId now that auth is ready
                val realFirestore = FirestoreRepository(authManager.userId)
                val realSync      = SyncManager(authManager.userId, database.dao())
                realSync.startListening()
            }
        }
    }
}