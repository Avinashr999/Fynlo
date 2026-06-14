package app.fynlo.data

import app.fynlo.data.model.Account
import app.fynlo.data.model.BackupData
import app.fynlo.data.model.Transaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C03a — backup integrity hash and version-guard contract
 * (UX_AUDIT §C03 stage 3a, items #1 and #4).
 *
 * Pure-function tests over [BackupIntegrity]. Covers every behaviour the
 * restore path depends on:
 *
 *  - Round-trip: encode-with-hash, decode, verify → `Ok`.
 *  - Tamper detection: change one byte of payload data, `check()` → `HashMismatch`.
 *  - Tamper detection on hash itself: corrupt the hash string → `HashMismatch`.
 *  - Forward-compat: schemaVersion strictly greater than
 *    `CURRENT_SCHEMA_VERSION` → `UnsupportedVersion`.
 *  - Backwards-compat: v1 legacy backup (no hash) → `Ok` unconditionally,
 *    even when the contentHash field is blank.
 *  - v2 with blank hash (rare — partial write, corruption, hand-edit
 *    removal) → `HashMismatch` so the user gets a meaningful error
 *    rather than a silent restore from un-verified data.
 *
 * Matches the `*DataIntegrity*` filter and is picked up by `checks.yml`'s
 * data-integrity gate alongside the C01/C02 tests. Data-integrity gate
 * count after this lands: **11 → 19** (3 C01 + 8 C02 + 8 C03a).
 */
class BackupDataIntegrityTest {

    /** A small but non-trivial fixture so JSON serialization actually has content. */
    private fun fixture(seed: String = "alpha") = BackupData(
        schemaVersion = 2,
        appVersion    = "3.2.3",
        exportedAt    = "2026-05-26T22:30:00Z",
        userId        = "user-$seed",
        deviceName    = "TestDevice-$seed",
        contentHash   = "",
        accounts = listOf(
            Account(id = "a1", name = "Personal Cash", type = "Cash", balance = 10_000.0),
            Account(id = "a2", name = "HDFC Bank",    type = "Bank", balance = 85_000.0),
        ),
        transactions = listOf(
            Transaction(
                id        = "t1",
                date      = "2026-05-26",
                type      = "Income",
                amount    = 5_000.0,
                toAcct    = "Personal Cash",
                category  = "Salary",
                desc      = "Part time",
            ),
        ),
    )

    @Test
    fun `computeHash produces 64-char lowercase hex`() {
        val hash = BackupIntegrity.computeHash(fixture())
        assertEquals("SHA-256 in hex is exactly 64 chars.", 64, hash.length)
        assertTrue(
            "Hash chars must be 0-9a-f only.",
            hash.all { it in '0'..'9' || it in 'a'..'f' },
        )
    }

    @Test
    fun `computeHash is deterministic for the same payload`() {
        val a = fixture()
        val b = fixture()
        assertEquals(
            "Same input -> same hash; deterministic encoding is the whole contract.",
            BackupIntegrity.computeHash(a),
            BackupIntegrity.computeHash(b),
        )
    }

    @Test
    fun `computeHash is stable regardless of existing contentHash value`() {
        // Both inputs should produce the same hash because [computeHash]
        // strips contentHash before computing — that's the symmetry the
        // export-side (writing a fresh hash) and the import-side
        // (verifying an existing hash) both rely on.
        val data       = fixture()
        val withHash   = data.copy(contentHash = "deadbeef".repeat(8))
        val noHash     = data.copy(contentHash = "")
        assertEquals(
            BackupIntegrity.computeHash(withHash),
            BackupIntegrity.computeHash(noHash),
        )
    }

    @Test
    fun `roundtrip — encode with hash, decode, check returns Ok`() {
        val draft = fixture()
        val hash  = BackupIntegrity.computeHash(draft)
        val final = draft.copy(contentHash = hash)
        val json  = Json.encodeToString(final)
        val decoded = Json.decodeFromString<BackupData>(json)
        assertEquals(BackupIntegrity.Check.Ok, BackupIntegrity.check(decoded))
    }

    @Test
    fun `tampering with payload data is detected as HashMismatch`() {
        val draft = fixture()
        val hash  = BackupIntegrity.computeHash(draft)
        val final = draft.copy(contentHash = hash)
        // Mutate one account's balance — a single byte change in the JSON.
        val tampered = final.copy(
            accounts = final.accounts.map {
                if (it.id == "a1") it.copy(balance = 999_999.0) else it
            },
        )
        assertEquals(BackupIntegrity.Check.HashMismatch, BackupIntegrity.check(tampered))
    }

    @Test
    fun `tampering with the contentHash itself is detected`() {
        val draft = fixture()
        val hash  = BackupIntegrity.computeHash(draft)
        val final = draft.copy(contentHash = hash)
        // Flip one character in the hash string.
        val flipped = if (final.contentHash[0] == '0') '1' else '0'
        val corrupted = final.copy(contentHash = flipped + final.contentHash.drop(1))
        assertEquals(BackupIntegrity.Check.HashMismatch, BackupIntegrity.check(corrupted))
    }

    @Test
    fun `v2 backup with blank contentHash is rejected as HashMismatch (not Ok)`() {
        // A v2 backup MUST carry a hash. Blank hash means partial write or
        // hand-edit removal — refuse to restore silently.
        val noHash = fixture().copy(contentHash = "")
        assertEquals(BackupIntegrity.Check.HashMismatch, BackupIntegrity.check(noHash))
    }

    @Test
    fun `v1 legacy backup (no metadata, no hash) is accepted unconditionally`() {
        // Legacy 3.2.0-and-earlier export produced no metadata and no hash.
        // The defaults on BackupData mean such JSON still decodes; the
        // schemaVersion default of 1 marks it as legacy and check() must
        // accept it for backwards compat.
        val legacy = BackupData(
            schemaVersion = 1,
            // appVersion / exportedAt / userId / deviceName / contentHash all blank
            accounts = listOf(Account(id = "a1", name = "Legacy Cash", type = "Cash", balance = 5_000.0)),
        )
        assertEquals(BackupIntegrity.Check.Ok, BackupIntegrity.check(legacy))
    }

    @Test
    fun `schemaVersion strictly greater than CURRENT is rejected with UnsupportedVersion`() {
        val future = fixture().copy(
            schemaVersion = BackupIntegrity.CURRENT_SCHEMA_VERSION + 1,
            contentHash   = "0".repeat(64), // even with a valid-shaped hash, version takes precedence
        )
        val result = BackupIntegrity.check(future)
        assertTrue(
            "Forward-compat: a backup from a newer Fynlo must be refused.",
            result is BackupIntegrity.Check.UnsupportedVersion,
        )
        assertEquals(
            BackupIntegrity.CURRENT_SCHEMA_VERSION + 1,
            (result as BackupIntegrity.Check.UnsupportedVersion).version,
        )
    }

    @Test
    fun `different fixtures produce different hashes (collision sanity)`() {
        val a = BackupIntegrity.computeHash(fixture("alpha"))
        val b = BackupIntegrity.computeHash(fixture("beta"))
        assertNotEquals(
            "Two distinct payloads must hash differently (SHA-256 collision sanity).",
            a, b,
        )
    }
}
