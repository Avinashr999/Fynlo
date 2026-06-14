package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * C03b Stage #4 (3.2.91) — `Ids.newId` contract gate.
 *
 * The helper exists to close the audit's "UUID format isn't standardised"
 * complaint. These tests pin the three properties every caller depends on:
 *
 *   1. **Format**: every returned string is a parseable RFC 4122 v4 UUID
 *      (lowercase, hyphenated, 36 chars). Migrations and Firestore docs
 *      treat these as opaque strings — but the moment a developer
 *      regex-matches an id elsewhere, the format becomes load-bearing.
 *   2. **Variant**: each id is a *random* UUID (variant 4), not a
 *      time-based v1 or name-based v3/v5. Random is the safe default
 *      for the offline-first sync model — no collisions across devices
 *      without coordination.
 *   3. **Uniqueness**: consecutive calls return distinct strings. The
 *      original PeopleScreen scheme (`"P-${timestamp}-${hashSuffix}"`)
 *      could collide under fast inserts; this test documents that
 *      Stage #4's generator does not.
 */
class IdsDataIntegrityTest {

    @Test
    fun `newId returns a well-formed UUID v4 string`() {
        val id = Ids.newId()
        // UUID.fromString throws on malformed input; pattern: 8-4-4-4-12 hex chars.
        val parsed = UUID.fromString(id)
        assertEquals("UUID version must be 4 (random).", 4, parsed.version())
        assertEquals("Standard string form is 36 chars (hyphens included).", 36, id.length)
    }

    @Test
    fun `newId returns the lowercase hyphenated canonical form`() {
        val id = Ids.newId()
        assertEquals(
            "Canonical UUID form is lowercase; mixed-case keys would " +
                "break Firestore document-id equality across clients " +
                "that re-stringify ids differently.",
            id.lowercase(), id,
        )
        assertTrue("Must contain four hyphens at positions 8, 13, 18, 23.",
            id[8] == '-' && id[13] == '-' && id[18] == '-' && id[23] == '-')
    }

    @Test
    fun `newId returns distinct strings on consecutive calls`() {
        val a = Ids.newId()
        val b = Ids.newId()
        assertNotEquals(
            "Random UUID v4 collision is astronomically unlikely; if this " +
                "ever fires, the generator has been silently swapped to a " +
                "deterministic source.",
            a, b,
        )
    }

    @Test
    fun `newId stays unique across a tight burst of calls`() {
        // The original "P-${timestamp}-..." scheme collided in this exact
        // shape: a tight loop with millisecond-resolution clock + a
        // 4-digit hash suffix → birthday-paradox collisions around
        // 100 inserts. UUID v4 sees no collisions at 10,000.
        val ids = buildSet {
            repeat(10_000) { add(Ids.newId()) }
        }
        assertEquals(
            "10,000 consecutive Ids.newId() calls must produce 10,000 distinct ids.",
            10_000, ids.size,
        )
    }
}
