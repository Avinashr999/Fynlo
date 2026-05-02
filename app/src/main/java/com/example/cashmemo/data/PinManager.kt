package com.example.cashmemo.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the 4-digit PIN lock using EncryptedSharedPreferences.
 * PIN is stored encrypted — never in plaintext.
 */
class PinManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cashmemo_pin_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isPinSet: Boolean
        get() = prefs.getString("pin", null) != null

    fun setPin(pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be 4 digits" }
        prefs.edit().putString("pin", pin).apply()
    }

    fun verifyPin(input: String): Boolean =
        prefs.getString("pin", null) == input

    fun clearPin() {
        prefs.edit().remove("pin").apply()
    }
}