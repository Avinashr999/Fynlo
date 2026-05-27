package app.fynlo.logic

/**
 * C10 (3.2.39) — single shared pluralization helper per UX_AUDIT §C10.
 *
 * Pre-C10 the codebase had hardcoded plural strings sprinkled across ~12
 * sites: `"$count snapshots"`, `"$count transactions"`, `"$count debt(s)"`,
 * etc. — all of them rendering "1 snapshots" / "1 debts(s)" when count = 1.
 *
 * This helper is intentionally Kotlin-side (not Android `<plurals>`) — the
 * app's strings live in Compose `Text(...)` calls, not in `string.xml`,
 * so a `pluralStringResource(...)` migration would touch every call site
 * anyway. Once we add localization (currently English-only), revisit and
 * promote the call sites to `<plurals>` resources for proper CLDR
 * pluralization rules in other languages.
 *
 * Usage:
 * ```
 * "${pluralize(snapshots.size, "snapshot")} recorded"   // "1 snapshot recorded" / "5 snapshots recorded"
 * pluralize(loans.size, "loan", "loans")                // explicit plural
 * pluralize(children.size, "child", "children")         // irregular plurals
 * ```
 *
 * `nounOnly` variant is for callers that compose the number elsewhere:
 * ```
 * "Across ${count} active ${pluralNoun(count, "debt")}"
 * ```
 */
object Pluralize {

    /**
     * Returns `"$count $word"` for count == 1, otherwise `"$count $plural"`.
     * Default plural is `singular + "s"`.
     */
    fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
        "$count ${if (count == 1) singular else plural}"

    /**
     * Returns just the noun (no count prefix). For callers that compose the
     * number into a larger sentence.
     */
    fun pluralNoun(count: Int, singular: String, plural: String = "${singular}s"): String =
        if (count == 1) singular else plural

    /** Long overload — common when callers pass day-counts from ChronoUnit. */
    fun pluralize(count: Long, singular: String, plural: String = "${singular}s"): String =
        "$count ${if (count == 1L) singular else plural}"

    /** Long noun-only overload. */
    fun pluralNoun(count: Long, singular: String, plural: String = "${singular}s"): String =
        if (count == 1L) singular else plural
}

/** Top-level shorthand. Most call sites only need this. */
fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    Pluralize.pluralize(count, singular, plural)

/** Top-level shorthand for the noun-only variant. */
fun pluralNoun(count: Int, singular: String, plural: String = "${singular}s"): String =
    Pluralize.pluralNoun(count, singular, plural)

/** Long overload — common when callers pass day-counts from ChronoUnit. */
fun pluralize(count: Long, singular: String, plural: String = "${singular}s"): String =
    Pluralize.pluralize(count, singular, plural)

/** Long noun-only overload. */
fun pluralNoun(count: Long, singular: String, plural: String = "${singular}s"): String =
    Pluralize.pluralNoun(count, singular, plural)
