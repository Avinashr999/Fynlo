package app.fynlo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PinManagerDataIntegrityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("Fynlo_pin", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `setPin stores salted pbkdf2 record and verifies only correct pin`() {
        val manager = PinManager(context)

        manager.setPin("1234")

        val stored = storedPinHash()
        assertTrue(stored.startsWith("pbkdf2_sha256$"))
        assertNotEquals(sha256Hex("1234"), stored)
        assertTrue(manager.verifyPin("1234"))
        assertFalse(manager.verifyPin("0000"))
    }

    @Test
    fun `same pin gets a different stored record because salt is random`() {
        val first = PinManager(context).also { it.setPin("1234") }
        val firstRecord = storedPinHash()

        first.clearPinSync()
        PinManager(context).setPin("1234")

        assertNotEquals(firstRecord, storedPinHash())
    }

    @Test
    fun `legacy sha256 pin migrates to pbkdf2 after successful unlock`() {
        prefs().edit().putString("pin_hash", sha256Hex("4321")).commit()
        val manager = PinManager(context)

        assertTrue(manager.verifyPin("4321"))

        val migrated = storedPinHash()
        assertTrue(migrated.startsWith("pbkdf2_sha256$"))
        assertNotEquals(sha256Hex("4321"), migrated)
    }

    @Test
    fun `legacy sha256 pin is not migrated after failed unlock`() {
        val legacy = sha256Hex("4321")
        prefs().edit().putString("pin_hash", legacy).commit()
        val manager = PinManager(context)

        assertFalse(manager.verifyPin("1234"))
        assertEquals(legacy, storedPinHash())
    }

    private fun prefs() = context.getSharedPreferences("Fynlo_pin", Context.MODE_PRIVATE)

    private fun storedPinHash(): String = prefs().getString("pin_hash", "") ?: ""

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
