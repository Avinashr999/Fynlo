package app.fynlo.ui

import app.fynlo.data.SyncStatus

data class CloudStatusPresentation(val label: String, val message: String)

// Entering the app as a guest is not a cloud sign-in.
fun cloudStatusPresentation(status: SyncStatus, hasCloudAccount: Boolean): CloudStatusPresentation {
    if (!hasCloudAccount) return CloudStatusPresentation(
        "Local only", "Saved on this device. Sign in from Profile & Security to use cloud backup."
    )
    return when (status) {
        SyncStatus.Initialising -> CloudStatusPresentation("Connecting", "Checking cloud backup...")
        SyncStatus.Syncing -> CloudStatusPresentation("Syncing", "Syncing...")
        SyncStatus.Synced -> CloudStatusPresentation("Synced", "All changes synced to cloud")
        SyncStatus.Offline -> CloudStatusPresentation("Offline", "Offline - changes sync when reconnected")
        is SyncStatus.Error -> CloudStatusPresentation("Sync failed", "Cloud backup needs attention. Check Profile & Security.")
    }
}
