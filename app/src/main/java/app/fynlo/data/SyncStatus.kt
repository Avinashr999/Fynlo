package app.fynlo.data

sealed class SyncStatus {
    /** All local changes have been pushed to Firestore. */
    object Synced : SyncStatus()
    /** A write is in-flight to Firestore. */
    object Syncing : SyncStatus()
    /** Device has no network — changes queued locally. */
    object Offline : SyncStatus()
    /** Auth not ready yet. */
    object Initialising : SyncStatus()
}