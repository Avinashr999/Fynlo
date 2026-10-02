package app.fynlo.ui

import app.fynlo.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudStatusPresentationTest {
    private val states = listOf(SyncStatus.Initialising, SyncStatus.Syncing, SyncStatus.Synced,
        SyncStatus.Offline, SyncStatus.Error("Internal exception"))

    @Test fun guestsAlwaysSeeLocalOnlyEvenWithStaleRepositoryStatus() {
        for (state in states) {
            val result = cloudStatusPresentation(state, false)
            assertEquals("Local only", result.label)
            assertEquals("Saved on this device. Sign in from Profile & Security to use cloud backup.", result.message)
        }
    }

    @Test fun signedInStatesRemainDistinctAndHideExceptions() {
        assertEquals(listOf("Connecting", "Syncing", "Synced", "Offline", "Sync failed"),
            states.map { cloudStatusPresentation(it, true).label })
        assertFalse(cloudStatusPresentation(states.last(), true).message.contains("Internal exception"))
    }

    @Test fun signInAndSignOutImmediatelyChangePresentation() {
        assertEquals("Local only", cloudStatusPresentation(SyncStatus.Initialising, false).label)
        assertEquals("Connecting", cloudStatusPresentation(SyncStatus.Initialising, true).label)
        assertEquals("Synced", cloudStatusPresentation(SyncStatus.Synced, true).label)
        assertEquals("Local only", cloudStatusPresentation(SyncStatus.Synced, false).label)
    }
}
