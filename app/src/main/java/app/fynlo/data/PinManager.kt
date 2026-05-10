package app.fynlo.data

import android.content.Context
import java.security.MessageDigest

/**
 * Manages the 4-digit PIN lock using regular SharedPreferences.
 * PIN is stored as a SHA-256 hash — never in plaintext.
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
        prefs.edit().putString("pin_hash", hash(pin)).apply()
    }

    fun verifyPin(input: String): Boolean =
        prefs.getString("pin_hash", null) == hash(input)

    fun clearPin() {
        prefs.edit()
            .remove("pin_hash")
            .putBoolean("biometric_enabled", false)   // also disable biometric if PIN removed
            .apply()
    }

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}