package app.fynlo.logic

/**
 * C22 (3.2.58) — fuzzy matching for the global Search screen.
 *
 * Two-tier scoring:
 *   - **Substring hit** (case-insensitive `contains`) → high score (1000)
 *     biased by where the match lands (earlier = better) and the haystack
 *     length (shorter = better). Substring exact match still wins because
 *     "Food Delivery" should outrank a fuzzy hit on "Foreign Drinks".
 *   - **Subsequence hit** (every query char appears in order, possibly with
 *     gaps) → lower score (~100..500) scaled by run density. So "fdr"
 *     matches "Food Delivery Rent" but ranks below a substring "del" hit.
 *
 * No match → -1. Caller filters by `score >= 0`, sorts descending.
 *
 * Pure-function, no Android deps; covered by `FuzzyMatchDataIntegrityTest`.
 */
object FuzzyMatch {

    /**
     * @return a positive score for a match, -1 otherwise. Higher = better.
     */
    fun score(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        if (haystack.isEmpty()) return -1
        val h = haystack.lowercase()
        val n = needle.lowercase()

        // Tier 1: substring. Position bias so a prefix match beats a tail
        // match; length penalty so a hit in a short string beats the same
        // hit in a long string (caps the haystack penalty at 200 so a
        // 1000-char note still scores positive).
        val idx = h.indexOf(n)
        if (idx >= 0) {
            return 1000 - idx.coerceAtMost(200) - h.length.coerceAtMost(200) / 2
        }

        // Tier 2: subsequence. Walk needle; for each char find the next
        // occurrence in haystack. Track the span (last - first) — shorter
        // span = denser run = higher score.
        var hi = 0
        var firstMatch = -1
        var lastMatch  = -1
        for (c in n) {
            val found = h.indexOf(c, startIndex = hi)
            if (found < 0) return -1
            if (firstMatch < 0) firstMatch = found
            lastMatch = found
            hi = found + 1
        }
        // Span includes the matched chars themselves; cap so pathological
        // strings don't go negative.
        val span = (lastMatch - firstMatch + 1).coerceAtLeast(n.length)
        val density = (n.length * 100) / span  // 100 = packed, ~10 = sparse
        return 100 + density.coerceAtMost(400)
    }

    /** Convenience: any kind of match (substring or subsequence). */
    fun matches(haystack: String, needle: String): Boolean =
        score(haystack, needle) >= 0

    /**
     * Score across multiple haystacks (name + notes + category, etc.).
     * Returns the best individual score so a single strong hit wins over
     * many weak ones — matches user intent ("if it shows up clearly
     * anywhere, surface it").
     */
    fun scoreAny(haystacks: List<String>, needle: String): Int =
        haystacks.maxOfOrNull { score(it, needle) } ?: -1
}
