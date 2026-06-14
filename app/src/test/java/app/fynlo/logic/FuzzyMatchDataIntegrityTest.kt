package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C22 (3.2.58) — fuzzy match unit tests. Pure-function, no Android deps,
 * runs in the regular `*DataIntegrity*` filter.
 *
 * The class is named "data integrity" by convention even though the
 * function under test is search-relevance — the project's existing test
 * naming gate (`./gradlew test --tests "*DataIntegrity*"`) is the
 * single CI hook we want this file to land in.
 */
class FuzzyMatchDataIntegrityTest {

    // ── Substring tier ───────────────────────────────────────────────

    @Test
    fun emptyNeedleScoresZero() {
        // Empty query is a degenerate match; the GlobalSearchScreen gates
        // on `query.length >= 2` so this never actually fires in prod, but
        // the helper must not crash and must not return -1 either.
        assertEquals(0, FuzzyMatch.score("anything", ""))
    }

    @Test
    fun emptyHaystackReturnsNoMatch() {
        assertEquals(-1, FuzzyMatch.score("", "food"))
    }

    @Test
    fun substringMatchIsCaseInsensitive() {
        assertTrue(FuzzyMatch.score("Food Delivery", "FOOD") > 0)
        assertTrue(FuzzyMatch.score("FOOD DELIVERY", "food") > 0)
    }

    @Test
    fun substringPrefixMatchOutranksTailMatch() {
        // Both haystacks contain "del" but the prefix hit must score higher.
        val prefix = FuzzyMatch.score("Delivery Service", "del")
        val tail   = FuzzyMatch.score("Forbidden Delights", "del")
        assertTrue("prefix=$prefix should beat tail=$tail", prefix > tail)
    }

    @Test
    fun substringShorterHaystackOutranksLongerOne() {
        val short = FuzzyMatch.score("Food", "foo")
        val long  = FuzzyMatch.score("Food and beverages and many other things", "foo")
        assertTrue("short=$short should beat long=$long", short > long)
    }

    // ── Subsequence tier ────────────────────────────────────────────

    @Test
    fun subsequenceMatchesNonContiguousChars() {
        // "fdr" should match "Food Delivery Rent" — each char appears in
        // order, separated by gaps.
        assertTrue(FuzzyMatch.matches("Food Delivery Rent", "fdr"))
    }

    @Test
    fun subsequenceRanksBelowSubstring() {
        // Substring "fud" doesn't exist in "Food Delivery", so fuzzy hit
        // ranks via subsequence. A direct substring match elsewhere wins.
        val subseq = FuzzyMatch.score("Food Delivery", "fdr")
        val substr = FuzzyMatch.score("Food Delivery", "del")
        assertTrue("subseq=$subseq should rank below substr=$substr", subseq < substr)
    }

    @Test
    fun subsequenceDensityBoostsScore() {
        // Same chars (xyz), no contiguous substring (spaces between),
        // packed vs spread. Packed → span 5, density 60. Sparse → much
        // wider span, much lower density.
        val packed = FuzzyMatch.score("x y z extras follow", "xyz")
        val sparse = FuzzyMatch.score("x then a very long buffer y then more z extras", "xyz")
        assertTrue("packed=$packed should beat sparse=$sparse", packed > sparse)
    }

    @Test
    fun unmatchedQueryReturnsNoMatch() {
        // Subsequence requires every needle char to appear in order. "xyz"
        // can't be matched in "Food Delivery".
        assertEquals(-1, FuzzyMatch.score("Food Delivery", "xyz"))
        assertFalse(FuzzyMatch.matches("Food Delivery", "xyz"))
    }

    @Test
    fun subsequenceMustPreserveOrder() {
        // "dfo" should NOT match "Food" — d comes after f in "Food", so
        // a subsequence walk fails. Reversed order doesn't count.
        assertEquals(-1, FuzzyMatch.score("Food", "dfo"))
    }

    // ── scoreAny across multiple haystacks ──────────────────────────

    @Test
    fun scoreAnyReturnsBestIndividualScore() {
        // Weak hit on field 1, strong hit on field 2. scoreAny returns the
        // max, not the sum — a single clear surface beats many vague ones.
        val score = FuzzyMatch.scoreAny(
            listOf("Misc Notes", "Food Delivery", "Tag x"),
            "food"
        )
        // Must equal the strong individual hit.
        assertEquals(FuzzyMatch.score("Food Delivery", "food"), score)
    }

    @Test
    fun scoreAnyReturnsMinusOneWhenAllFieldsMiss() {
        assertEquals(
            -1,
            FuzzyMatch.scoreAny(listOf("alpha", "beta", "gamma"), "xyz")
        )
    }

    @Test
    fun scoreAnyHandlesEmptyHaystackList() {
        // No surfaces to search → no match. Defensive.
        assertEquals(-1, FuzzyMatch.scoreAny(emptyList(), "food"))
    }
}
