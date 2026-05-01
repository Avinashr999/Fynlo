package com.example.cashmemo.data.remote

import com.example.cashmemo.data.SyncStatus
import com.example.cashmemo.data.local.CashMemoDao
import com.example.cashmemo.data.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Listens to Firestore snapshot updates for all collections and writes
 * incoming changes back to Room, implementing last-write-wins via updatedAt.
 *
 * This is the ONLY path that writes to Room without going through
 * FinanceRepository, so it never triggers a second Firestore write.
 */
class SyncManager(
    val userId: String,
    private val dao: CashMemoDao
) {
    private val db     = Firebase.firestore
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableListOf<ListenerRegistration>()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Initialising)
    val status: StateFlow<SyncStatus> = _status

    private fun col(name: String) =
        db.collection("users").document(userId).collection(name)

    /** Start real-time listeners for every collection. */
    fun startListening() {
        if (userId.isEmpty()) return
        android.util.Log.d("CashMemoSync", "SyncManager.startListening() uid=$userId")

        listeners += col("borrowers").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                android.util.Log.d("CashMemoSync", "Borrowers snapshot: ${snap.documentChanges.size} changes")
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        val remote = Borrower(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            address   = doc.str("address"),
                            guarantor = doc.str("guarantor"),
                            amount    = doc.dbl("amount"),
                            rate      = doc.dbl("rate"),
                            date      = doc.str("date"),
                            due       = doc.str("due"),
                            tenure    = doc.int("tenure"),
                            type      = doc.str("type"),
                            paid      = doc.dbl("paid"),
                            status    = doc.str("status"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt")
                        )
                        dao.insertBorrower(remote)
                        android.util.Log.d("CashMemoSync", "Inserted borrower: ${remote.id} name=${remote.name} proj=${remote.projectId}")
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("transactions").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                android.util.Log.d("CashMemoSync", "Transactions snapshot: ${snap.documentChanges.size} changes")
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        val remote = Transaction(
                            id        = doc.id,
                            date      = doc.str("date"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            fromAcct  = doc.str("fromAcct"),
                            toAcct    = doc.str("toAcct"),
                            category  = doc.str("category"),
                            subcat    = doc.str("subcat"),
                            person    = doc.str("person"),
                            desc      = doc.str("desc"),
                            ref       = doc.str("ref"),
                            notes     = doc.str("notes"),
                            tags      = doc.str("tags"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt")
                        )
                        dao.insertTransaction(remote)
                        android.util.Log.d("CashMemoSync", "Inserted transaction: ${remote.id} cat=${remote.category} proj=${remote.projectId}")
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("accounts").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertAccount(Account(
                            id        = doc.id,
                            name      = doc.str("name"),
                            type      = doc.str("type"),
                            balance   = doc.dbl("balance"),
                            icon      = doc.str("icon"),
                            color     = doc.str("color"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("investments").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertInvestment(Investment(
                            id           = doc.id,
                            name         = doc.str("name"),
                            type         = doc.str("type"),
                            subtype      = doc.str("subtype"),
                            invested     = doc.dbl("invested"),
                            currentVal   = doc.dbl("currentVal"),
                            date         = doc.str("date"),
                            maturityDate = doc.str("maturityDate"),
                            rate         = doc.dbl("rate"),
                            realized     = doc.dbl("realized"),
                            withdrawn    = doc.dbl("withdrawn"),
                            notes        = doc.str("notes"),
                            projectId    = doc.str("projectId"),
                            updatedAt    = doc.lng("updatedAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("debts").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertDebt(Debt(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            rate      = doc.dbl("rate"),
                            date      = doc.str("date"),
                            due       = doc.str("due"),
                            tenure    = doc.int("tenure"),
                            intType   = doc.str("intType"),
                            paid      = doc.dbl("paid"),
                            status    = doc.str("status"),
                            collateral = doc.str("collateral"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("people").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertPerson(Person(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            type      = doc.str("type"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("projects").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { _status.value = SyncStatus.Offline; return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertProject(Project(
                            id        = doc.id,
                            name      = doc.str("name"),
                            icon      = doc.str("icon"),
                            color     = doc.str("color"),
                            currency  = doc.str("currency"),
                            createdAt = doc.str("createdAt"),
                            updatedAt = doc.lng("updatedAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        _status.value = SyncStatus.Synced
    }

    fun setSyncing() { _status.value = SyncStatus.Syncing }
    fun setSynced()  { _status.value = SyncStatus.Synced  }

    /** Call from Application.onTerminate or when user signs out. */
    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
        scope.cancel()
    }

    // ── Extension helpers to safely read Firestore fields ─────────────────────
    private fun DocumentSnapshot.str(key: String)  = getString(key)  ?: ""
    private fun DocumentSnapshot.dbl(key: String)  = getDouble(key)  ?: 0.0
    private fun DocumentSnapshot.lng(key: String)  = getLong(key)    ?: 0L
    private fun DocumentSnapshot.int(key: String)  = getLong(key)?.toInt() ?: 0
}