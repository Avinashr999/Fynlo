package app.fynlo.data

import android.content.Context
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages the 4-digit PIN lock using regular SharedPreferences.
 * PIN is stored as a salted PBKDF2 hash — never in plaintext.
 * Also stores the user's preference for biometric unlock.
 */
class PinManager(context: Context) {

    private val prefs = context.getSharedPreferences("Fynlo_pin", Context.MODE_PRIVATE)

    val isPinSet: Boolean
        get() = prefs.getString("pin_hash", null) != null

    /** True only when user has explicitly enabled biometric AND PIN is set. */
    var isBiometricEnabled: Boolean
        get() = isPinSet && prefs.getBoolean("biometric_enabled", false)
        set(value) { prefs.edit().putBoolean("biometric_enabled", value).apply() }

    fun setPin(pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be 4 digits" }
        prefs.edit().putString(PIN_HASH, pbkdf2Record(pin)).apply()
    }

    fun verifyPin(input: String): Boolean {
        val stored = prefs.getString(PIN_HASH, null) ?: return false
        if (stored.startsWith(PBKDF2_PREFIX)) {
            return verifyPbkdf2(input, stored)
        }

        // Legacy migration path: earlier builds stored SHA-256(pin). If a
        // correct legacy PIN is entered, immediately rewrite it as PBKDF2.
        val legacyMatches = constantTimeEquals(stored, sha256Hex(input))
        if (legacyMatches) setPin(input)
        return legacyMatches
    }

    fun clearPin() {
        prefs.edit()
            .remove(PIN_HASH)
            .putBoolean("biometric_enabled", false)   // also disable biometric if PIN removed
            .apply()
    }

    /**
     * Synchronous clear for the Reset-All-Data path, which restarts the process
     * immediately after. apply()'s async write would be dropped by the hard
     * Runtime.exit() before it reaches disk, so we commit() here. Clears every
     * key (pin_hash + biometric_enabled).
     */
    fun clearPinSync() {
        prefs.edit().clear().commit()
    }

    private fun pbkdf2Record(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
        return listOf(
            PBKDF2_PREFIX,
            PBKDF2_ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
        ).joinToString("$")
    }

    private fun verifyPbkdf2(input: String, record: String): Boolean {
        val parts = record.split("$")
        if (parts.size != 4 || parts[0] != PBKDF2_PREFIX) return false
        return runCatching {
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            MessageDigest.isEqual(expected, pbkdf2(input, salt, iterations))
        }.getOrDefault(false)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }

    private companion object {
        const val PIN_HASH = "pin_hash"
        const val PBKDF2_PREFIX = "pbkdf2_sha256"
        const val PBKDF2_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val HASH_BITS = 256
    }
}
