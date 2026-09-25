package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import kotlinx.coroutines.CoroutineScope

internal class RepositoryContext(
    val dao: FynloDao,
    val db: FynloDatabase,
    var firestoreRepo: FirestoreRepository,
    var syncManager: SyncManager,
    val ioScope: CoroutineScope
)
