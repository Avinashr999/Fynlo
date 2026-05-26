package app.fynlo.data

import app.fynlo.data.model.BackupData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * C03a — backup integrity (UX_AUDIT §C03 stage 3a, items #1 and #4).
 *
 * Two-purpose object:
 *  - On **export**, [computeHash] produces the SHA-256 (lower-case hex)
 *    that `FinanceRepository.getAllDataAsJson()` embeds into
 *    `BackupData.contentHash` as the last step before serialising.
 *  - On **import**, [check] validates a freshly-decoded `BackupData`
 *    against this app's understanding: rejects too-new formats, rejects
 *    hash mismatches on `v2+` backups, accepts legacy `v1` backups
 *    unconditionally for backwards compatibility.
 *
 * Pure functions only — no Context, no I/O, no Room. Live alongside
 * the data layer because the hash is a property of the data shape,
 * not of any persistence mechanism. Unit-testable without Robolectric.
 */
object BackupIntegrity {

    /**
     * Highest backup format version this app understands. Bumped together
     * with any change to [BackupData]'s field layout that affects what
     * goes into the hash. Increment when adding required new fields
     * (default values for existing fields don't need a bump because old
     * backups still decode cleanly).
     */
    const val CURRENT_SCHEMA_VERSION = 2

    /**
     * Computes the canonical SHA-256 hash of [data] for the purposes of
     * the `contentHash` field. The input's existing `contentHash` is
     * stripped to `""` before computing — so this is symmetric whether
     * called on the export-side (with `contentHash = ""`) or on the
     * import-side (with `contentHash = <previous-stored-hash>`).
     *
     * Returns the SHA-256 digest as 64 lower-case hex characters.
     *
     * Implementation note: relies on kotlinx-serialization-json's default
     * compact encoding being deterministic for a given object. Field
     * order in [BackupData] is load-bearing (see the class kdoc); don't
     * reorder.
     */
    fun computeHash(data: BackupData): String {
        val canonical = Json.encodeToString(data.copy(contentHash = ""))
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates a freshly-decoded [BackupData] for restore.
     *
     * Returns one of:
     *  - [Check.Ok] — the backup is safe to restore. Caller proceeds.
     *  - [Check.UnsupportedVersion] — the backup is from a future app
     *    version we don't understand. Caller surfaces "please update
     *    Fynlo" to the user.
     *  - [Check.HashMismatch] — the backup is a v2+ format but its
     *    `contentHash` doesn't match what we recompute. The file is
     *    corrupted, was hand-edited, or was produced by a different
     *    kotlinx-serialization version that serialised differently.
     *    Either way the user should not restore from it.
     */
    fun check(data: BackupData): Check {
        if (data.schemaVersion > CURRENT_SCHEMA_VERSION) {
            return Check.UnsupportedVersion(data.schemaVersion)
        }
        // v1 (legacy) had no hash. Accept unconditionally for backwards compat.
        if (data.schemaVersion <= 1) return Check.Ok

        // v2+ must carry a non-blank hash that matches the canonical form.
        if (data.contentHash.isBlank()) return Check.HashMismatch
        val expected = computeHash(data)
        return if (expected == data.contentHash) Check.Ok else Check.HashMismatch
    }

    /** Outcome of [check]. Caller maps to user-facing behaviour. */
    sealed class Check {
        /** The backup is safe to restore. */
        data object Ok : Check()

        /** Backup format is newer than this app understands. */
        data class UnsupportedVersion(val version: Int) : Check()

        /** Hash didn't verify — file is corrupted or modified. */
        data object HashMismatch : Check()
    }
}
