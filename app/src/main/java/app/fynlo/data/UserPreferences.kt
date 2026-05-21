package app.fynlo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fynlo_settings")

/**
 * Centralized preferences using Jetpack DataStore.
 * Replaces legacy SharedPreferences ("fynlo_prefs" and "theme_prefs").
 *
 * PIN storage remains in SharedPreferences (PinManager) because
 * biometric/crypto APIs require synchronous access.
 */
object UserPreferences {

    // Keys
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val SETUP_DONE = booleanPreferencesKey("setup_done")
    private val APP_LANGUAGE = stringPreferencesKey("app_language")
    private val DARK_MODE = stringPreferencesKey("dark_mode") // "light", "dark", "system"
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
    private val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    private val DATE_FORMAT = stringPreferencesKey("date_format")

    // ── Read (Flow-based, reactive) ──────────────────────────────────────

    fun onboardingDone(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    fun setupDone(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SETUP_DONE] ?: false }

    fun appLanguage(context: Context): Flow<String> =
        context.dataStore.data.map { it[APP_LANGUAGE] ?: "en" }

    fun darkMode(context: Context): Flow<String> =
        context.dataStore.data.map { it[DARK_MODE] ?: "system" }

    fun notificationsEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    fun userDisplayName(context: Context): Flow<String> =
        context.dataStore.data.map { it[USER_DISPLAY_NAME] ?: "" }

    fun defaultCurrency(context: Context): Flow<String> =
        context.dataStore.data.map { it[DEFAULT_CURRENCY] ?: "INR" }

    fun dateFormat(context: Context): Flow<String> =
        context.dataStore.data.map { it[DATE_FORMAT] ?: "dd-MM-yyyy" }

    // ── Read (blocking, for one-time checks like Navigation init) ────────

    /**
     * Warms the DataStore on a background thread so the first-frame
     * synchronous reads (getDarkModeSync etc.) hit a cached value instead
     * of paying the cold file-open cost on the main thread. Safe to call
     * fire-and-forget from Application.onCreate.
     */
    suspend fun warmUp(context: Context) {
        runCatching { context.dataStore.data.first() }
    }

    fun getOnboardingDoneSync(context: Context): Boolean = runBlocking {
        context.dataStore.data.first()[ONBOARDING_DONE] ?: false
    }

    fun getSetupDoneSync(context: Context): Boolean = runBlocking {
        context.dataStore.data.first()[SETUP_DONE] ?: false
    }

    fun getDarkModeSync(context: Context): String = runBlocking {
        context.dataStore.data.first()[DARK_MODE] ?: "system"
    }

    // ── Write ────────────────────────────────────────────────────────────

    suspend fun setOnboardingDone(context: Context, done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    suspend fun setSetupDone(context: Context, done: Boolean) {
        context.dataStore.edit { it[SETUP_DONE] = done }
    }

    suspend fun setAppLanguage(context: Context, code: String) {
        context.dataStore.edit { it[APP_LANGUAGE] = code }
    }

    suspend fun setDarkMode(context: Context, mode: String) {
        context.dataStore.edit { it[DARK_MODE] = mode }
    }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setUserDisplayName(context: Context, name: String) {
        context.dataStore.edit { it[USER_DISPLAY_NAME] = name }
    }

    suspend fun setDefaultCurrency(context: Context, currency: String) {
        context.dataStore.edit { it[DEFAULT_CURRENCY] = currency }
    }

    suspend fun setDateFormat(context: Context, format: String) {
        context.dataStore.edit { it[DATE_FORMAT] = format }
    }
}
