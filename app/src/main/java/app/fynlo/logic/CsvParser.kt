package app.fynlo.logic

/**
 * C22 (3.2.67) — minimal RFC 4180-style CSV parser.
 *
 * Handles:
 *   - Comma separators (the only one we accept — banks vary, but the
 *     mapper UI surfaces the parsed columns so the user can sanity-check).
 *   - Double-quoted fields (`"foo,bar"` is one field "foo,bar").
 *   - Escaped quotes inside quoted fields (`""` → `"`).
 *   - CRLF or LF line endings.
 *   - Trailing newlines (empty trailing row is dropped).
 *
 * Intentionally does not handle:
 *   - Multi-line fields (quoted fields with embedded newlines). Rare in
 *     bank statements; would need a streaming state machine and the
 *     line-by-line approach gives the mapper UI a clean "row count" to
 *     show. If a real bank uses them we'll extend.
 *   - Tab/semicolon separators. Add as a parser variant if needed.
 *
 * Pure-Kotlin; covered by `CsvParserDataIntegrityTest`.
 */
object CsvParser {

    /** Parsed rows; each row is a list of field strings. Header row (if
     *  any) is just the first entry — caller decides how to interpret it. */
    fun parse(input: String): List<List<String>> {
        if (input.isBlank()) return emptyList()
        val rows = mutableListOf<List<String>>()
        for (raw in input.split('\n')) {
            val line = raw.removeSuffix("\r")
            if (line.isEmpty()) continue
            rows += parseLine(line)
        }
        return rows
    }

    /**
     * Parse a single CSV line into fields. Public so the column-mapper
     * can re-parse the header alone when the user picks a different row
     * as headers (rare, but cheap to support).
     */
    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    // Escaped quote inside a quoted field: `""` → `"`.
                    buf.append('"')
                    i += 2
                    continue
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                    continue
                }
                c == ',' && !inQuotes -> {
                    out += buf.toString()
                    buf.clear()
                    i++
                    continue
                }
                else -> {
                    buf.append(c)
                    i++
                }
            }
        }
        out += buf.toString()
        return out
    }
}
