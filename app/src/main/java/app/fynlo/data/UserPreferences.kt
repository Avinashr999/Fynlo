package app.fynlo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.fynlo.data.model.RecentlyUsedSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    // C18 (3.2.20) — split notifications into two independently-toggleable
    // sub-categories per UX_AUDIT §C18 fix #1. The legacy `notifications_enabled`
    // key stays as the master switch (used by [ReminderScheduler] / setup screen
    // / first-launch wizard); these two sub-keys add granular control inside
    // Settings. Both default to the master's current value, so existing users
    // who disabled notifications in setup see both sub-toggles already OFF.
    // Worker-layer differentiation (which alarm class reads which sub-key) is
    // a follow-up — for now both sub-toggles affect the master via UI
    // wire-up, and the Workers continue to read the master.
    private val LOAN_REMINDERS_ENABLED = booleanPreferencesKey("loan_reminders_enabled")
    private val BUDGET_ALERTS_ENABLED  = booleanPreferencesKey("budget_alerts_enabled")
    private val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
    private val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    private val DATE_FORMAT = stringPreferencesKey("date_format")

    /** Last time recalculateAllBalances() ran (epoch millis). 0L = never.
     *  Used by C02 (UX_AUDIT §C02) for auto-recalc-on-launch debouncing and
     *  the "Last updated X ago" subtitle on Dashboard. */
    private val LAST_RECALC_AT = longPreferencesKey("last_recalc_at")

    /** JSON-encoded `RecentlyUsedSnapshot`. Used by C04 (UX_AUDIT §C04) for
     *  the per-form, per-field picker memory.  Single string key keeps the
     *  whole picker-recency state addressable without needing a Room table
     *  (the dataset is small — ≤5 entries × ~10 slots ≤ 50 entries total).
     *  Empty / unset reads decode as an empty `RecentlyUsedSnapshot`. */
    private val RECENTLY_USED = stringPreferencesKey("recently_used")

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

    /** C18 sub-toggle: defaults to the master `notificationsEnabled` value so
     *  existing users who disabled notifications in setup see this OFF. */
    fun loanRemindersEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[LOAN_REMINDERS_ENABLED] ?: (it[NOTIFICATIONS_ENABLED] ?: true) }

    /** C18 sub-toggle: defaults to the master `notificationsEnabled` value. */
    fun budgetAlertsEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[BUDGET_ALERTS_ENABLED] ?: (it[NOTIFICATIONS_ENABLED] ?: true) }

    fun userDisplayName(context: Context): Flow<String> =
        context.dataStore.data.map { it[USER_DISPLAY_NAME] ?: "" }

    fun defaultCurrency(context: Context): Flow<String> =
        context.dataStore.data.map { it[DEFAULT_CURRENCY] ?: "INR" }

    fun dateFormat(context: Context): Flow<String> =
        context.dataStore.data.map { it[DATE_FORMAT] ?: "dd-MM-yyyy" }

    /** Last successful recalc time as epoch millis; `0L` until the first recalc. */
    fun lastRecalcAt(context: Context): Flow<Long> =
        context.dataStore.data.map { it[LAST_RECALC_AT] ?: 0L }

    /** Reactive [RecentlyUsedSnapshot]; empty snapshot until first record (C04).
     *  Decoder is fault-tolerant: a corrupted / unparseable blob yields an
     *  empty snapshot instead of crashing the UI. */
    fun recentlyUsed(context: Context): Flow<RecentlyUsedSnapshot> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[RECENTLY_USED] ?: return@map RecentlyUsedSnapshot()
            runCatching { Json.decodeFromString<RecentlyUsedSnapshot>(raw) }
                .getOrDefault(RecentlyUsedSnapshot())
        }

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

    /**
     * C18 sub-toggle setter. Also updates the master `notificationsEnabled`
     * via OR-with-the-other-sub: master is ON when EITHER sub-toggle is ON
     * (so the [ReminderScheduler] keeps scheduling work). When BOTH sub-
     * toggles are off, the master flips to OFF and the scheduler stops.
     */
    suspend fun setLoanRemindersEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LOAN_REMINDERS_ENABLED] = enabled
            // Derive master: ON if either sub is ON.
            val budget = prefs[BUDGET_ALERTS_ENABLED] ?: (prefs[NOTIFICATIONS_ENABLED] ?: true)
            prefs[NOTIFICATIONS_ENABLED] = enabled || budget
        }
    }

    /** C18 sub-toggle setter — symmetric with [setLoanRemindersEnabled]. */
    suspend fun setBudgetAlertsEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BUDGET_ALERTS_ENABLED] = enabled
            val loan = prefs[LOAN_REMINDERS_ENABLED] ?: (prefs[NOTIFICATIONS_ENABLED] ?: true)
            prefs[NOTIFICATIONS_ENABLED] = enabled || loan
        }
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

    /** Stamps the recalc time (epoch millis). Only called from `RecalcCoordinator`. */
    suspend fun setLastRecalcAt(context: Context, epochMillis: Long) {
        context.dataStore.edit { it[LAST_RECALC_AT] = epochMillis }
    }

    /** C04: atomic read-modify-write on the [RecentlyUsedSnapshot] blob.
     *  Caller passes a pure function that takes the current snapshot and
     *  returns the new one (use `RecentlyUsedLogic.add` for the standard
     *  dedup-and-cap semantics). Only called from `RecentlyUsedTracker`. */
    suspend fun editRecentlyUsed(
        context: Context,
        mutate: (RecentlyUsedSnapshot) -> RecentlyUsedSnapshot,
    ) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENTLY_USED]
                ?.let { runCatching { Json.decodeFromString<RecentlyUsedSnapshot>(it) }.getOrDefault(RecentlyUsedSnapshot()) }
                ?: RecentlyUsedSnapshot()
            prefs[RECENTLY_USED] = Json.encodeToString(mutate(current))
        }
    }

    /** Wipes every stored preference — used by Reset All Data. */
    suspend fun clearAll(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
