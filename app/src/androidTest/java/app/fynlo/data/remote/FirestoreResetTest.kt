package app.fynlo.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [deleteFirestoreUserTree] — the Firestore half of Reset All Data —
 * against the local Firebase emulator. Requires the Firestore emulator running
 * on the host (firebase.emulator.json) and a device that can reach it at
 * 10.0.2.2:8080 (an Android Virtual Device). No real account or cloud data is
 * touched.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreResetTest {

    private lateinit var fs: FirebaseFirestore
    private val uid = "reset-test-uid"

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Use an isolated secondary FirebaseApp so we never collide with the
        // default FirebaseFirestore the app process may have already started
        // (useEmulator can only be called on a not-yet-started instance).
        val defaultApp = try { FirebaseApp.getInstance() }
                         catch (e: IllegalStateException) { FirebaseApp.initializeApp(ctx)!! }
        val testApp = try {
            FirebaseApp.initializeApp(ctx, defaultApp.options, "emulatorTest")
        } catch (e: IllegalStateException) {
            FirebaseApp.getInstance("emulatorTest")
        }
        fs = FirebaseFirestore.getInstance(testApp)
        runCatching { fs.useEmulator("10.0.2.2", 8080) }
        fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)   // always hit the emulator, never a cache
            .build()
    }

    @Test
    fun resetDeletesEntireUserTree() = runBlocking {
        val userDoc = fs.collection("users").document(uid)

        // Seed two docs in every collection + the user root document.
        USER_FIRESTORE_COLLECTIONS.forEach { col ->
            userDoc.collection(col).document("a").set(mapOf("v" to 1)).await()
            userDoc.collection(col).document("b").set(mapOf("v" to 2)).await()
        }
        userDoc.set(mapOf("seeded" to true)).await()

        // Legacy nested backup snapshots (accounts/borrowers under a daily doc).
        LEGACY_BACKUP_SUBCOLLECTIONS.forEach { sub ->
            userDoc.collection("backups").document("a")
                .collection(sub).document("x").set(mapOf("v" to 1)).await()
        }

        // Precondition: the data is really there (read from the server, not cache).
        USER_FIRESTORE_COLLECTIONS.forEach { col ->
            val n = userDoc.collection(col).get(Source.SERVER).await().size()
            assertEquals("seed should create 2 docs in $col", 2, n)
        }
        LEGACY_BACKUP_SUBCOLLECTIONS.forEach { sub ->
            val n = userDoc.collection("backups").document("a")
                .collection(sub).get(Source.SERVER).await().size()
            assertEquals("seed should create nested backups/$sub", 1, n)
        }

        // Act: the exact function Reset All Data uses.
        deleteFirestoreUserTree(fs, uid)

        // Assert: every collection is empty and the root document is gone.
        USER_FIRESTORE_COLLECTIONS.forEach { col ->
            val n = userDoc.collection(col).get(Source.SERVER).await().size()
            assertEquals("$col should be empty after reset", 0, n)
        }
        LEGACY_BACKUP_SUBCOLLECTIONS.forEach { sub ->
            val n = userDoc.collection("backups").document("a")
                .collection(sub).get(Source.SERVER).await().size()
            assertEquals("nested backups/$sub should be empty after reset", 0, n)
        }
        assertFalse(
            "user root doc should be deleted",
            userDoc.get(Source.SERVER).await().exists()
        )
    }
}
