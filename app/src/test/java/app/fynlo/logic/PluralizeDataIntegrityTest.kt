package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C10 (3.2.39) — data-integrity guards for the shared pluralize helper.
 * These cases pin the contract: singular for `count == 1`, plural for
 * everything else (including 0 and negative), default plural is
 * `singular + "s"`, irregular plurals via explicit second arg.
 *
 * Counted as a `*DataIntegrity*` class so it rides the standard CI gate.
 */
class PluralizeDataIntegrityTest {

    @Test fun `singular form for count == 1`() {
        assertEquals("1 snapshot", pluralize(1, "snapshot"))
        assertEquals("1 loan", pluralize(1, "loan"))
        assertEquals("1 entry", pluralize(1, "entry", "entries"))
    }

    @Test fun `plural form for count == 0`() {
        // "0 snapshots" reads naturally — English uses plural for zero.
        assertEquals("0 snapshots", pluralize(0, "snapshot"))
        assertEquals("0 entries", pluralize(0, "entry", "entries"))
    }

    @Test fun `plural form for count gt 1`() {
        assertEquals("2 snapshots", pluralize(2, "snapshot"))
        assertEquals("10 loans", pluralize(10, "loan"))
        assertEquals("100 transactions", pluralize(100, "transaction"))
    }

    @Test fun `plural form for negative count`() {
        // Negative counts shouldn't happen for collection sizes but the
        // helper shouldn't crash or render "−1 snapshot" as singular —
        // English uses plural for anything that isn't exactly 1.
        assertEquals("-3 snapshots", pluralize(-3, "snapshot"))
    }

    @Test fun `default plural is singular plus s`() {
        assertEquals("5 days", pluralize(5, "day"))
        assertEquals("5 borrowers", pluralize(5, "borrower"))
    }

    @Test fun `explicit irregular plural`() {
        assertEquals("3 children", pluralize(3, "child", "children"))
        assertEquals("1 child", pluralize(1, "child", "children"))
        assertEquals("2 mice", pluralize(2, "mouse", "mice"))
    }

    @Test fun `pluralNoun returns only the noun`() {
        assertEquals("snapshot", pluralNoun(1, "snapshot"))
        assertEquals("snapshots", pluralNoun(5, "snapshot"))
        assertEquals("entry", pluralNoun(1, "entry", "entries"))
        assertEquals("entries", pluralNoun(2, "entry", "entries"))
    }

    @Test fun `pluralNoun zero is plural`() {
        assertEquals("snapshots", pluralNoun(0, "snapshot"))
    }
}
