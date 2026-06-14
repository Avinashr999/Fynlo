package app.fynlo.logic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 3.2.72 — diagnostic audit log for every account-balance mutation.
 *
 * Smoke surfaced "net worth changes after every install" with no visible
 * transaction inserts. Hypothesis: Firestore sync rewriting local
 * `accounts.balance` on each listener attach, OR direct dao writes that
 * bypass the transaction record. The log captures every balance write
 * with a source tag so we can prove which subsystem is firing.
 *
 * Storage: a separate DataStore file (so the existing
 * `fynlo_settings` blob stays small and unaffected) holding a JSON list,
 * newest first, capped at [CAP] entries. Reading is reactive — the UI
 * collects a Flow so new entries appear without re-opening the screen.
 *
 * Pure-Kotlin (no Android coroutines beyond DataStore primitives); the
 * recording side is suspend so the caller controls when to write.
 */
object BalanceAuditLog {

    /** Source tag — every mutation site picks one. */
    enum class Source {
        MANUAL_TXN,          // FinanceRepository.insertTransaction (user-driven add)
        EDIT_TXN_REVERSE,    // editTransaction: old-side reversal
        EDIT_TXN_APPLY,      // editTransaction: new-side application
        DELETE_TXN,          // deleteTransaction reversal
        QUICK_EDIT_BALANCE,  // quickEditBalance (manual balance correction)
        RECURRING_WORKER,    // RecurringWorker.doWork auto-log
        SYNC_PULL,           // SyncManager listener: account balance overwrite from Firestore
        SYNC_PULL_BORROWER,  // 3.2.73 — SyncManager listener: borrower.paid overwrite from Firestore
        SYNC_PULL_DEBT,      // 3.2.73 — SyncManager listener: debt.paid overwrite from Firestore
        RECALC_BORROWER_PAID,// 3.2.73 — FinanceRepository.recalculateAllBalances: borrower.paid rebuilt from payments
        RECALC_DEBT_PAID,    // 3.2.73 — FinanceRepository.recalculateAllBalances: debt.paid rebuilt from debt_payments
        ORPHAN_REPAIR,       // OrphanRepairDialog re-pointing (3.2.59)
        CSV_IMPORT,          // BankStatementImport bulk insert
        OTHER,               // fallback — caller passes its own free-form note
    }

    @Serializable
    data class Entry(
        val timestamp: Long,
        val source:    String,    // Source.name; string for forward-compat
        val account:   String,
        val delta:     Double,    // can be 0 (e.g. orphan repair where the old side was a no-op)
        val note:      String,
    )

    private const val CAP = 200
    private val LOG_KEY = stringPreferencesKey("balance_audit_log")

    // Separate DataStore file so the 200-entry JSON doesn't bloat the
    // hot-path `fynlo_settings` blob (read on every theme / locale query).
    private val Context.auditDataStore: DataStore<Preferences> by preferencesDataStore(name = "fynlo_balance_audit")

    // FinanceRepository is intentionally Context-free (KMP hedge per the
    // C02 comment); a singleton-initialised app context lets the
    // diagnostic record from inside the repository / sync layer without
    // breaking that boundary. `FynloApplication.onCreate` calls init()
    // before any DAO is touched.
    @Volatile private var appCtx: Context? = null
    fun init(context: Context) { appCtx = context.applicationContext }

    suspend fun record(
        source:  Source,
        account: String,
        delta:   Double,
        note:    String = "",
    ) {
        val ctx = appCtx ?: return  // silent no-op until init() has run
        val entry = Entry(
            timestamp = System.currentTimeMillis(),
            source    = source.name,
            account   = account,
            delta     = delta,
            note      = note,
        )
        ctx.auditDataStore.edit { prefs ->
            val current = prefs[LOG_KEY]
                ?.let { runCatching { Json.decodeFromString<List<Entry>>(it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            val next = (listOf(entry) + current).take(CAP)
            prefs[LOG_KEY] = Json.encodeToString(next)
        }
    }

    fun observe(context: Context): Flow<List<Entry>> =
        context.auditDataStore.data.map { prefs ->
            val raw = prefs[LOG_KEY] ?: return@map emptyList()
            runCatching { Json.decodeFromString<List<Entry>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun clear(context: Context) {
        context.auditDataStore.edit { it.remove(LOG_KEY) }
    }
}
