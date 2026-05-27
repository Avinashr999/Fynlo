package app.fynlo.data

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * C09 — regression test for UTF-8 mojibake in the source tree (UX_AUDIT §C09).
 *
 * Pre-3.2.19 the dialog bodies in `SettingsScreen.kt` rendered garbled
 * characters like `âš ï¸` (instead of `⚠️`) and `â‚¹` (instead of `₹`).
 * Root cause: source files saved with Windows-1252 encoding at some
 * point, then re-read as UTF-8 by the Kotlin compiler — every multi-byte
 * UTF-8 codepoint became a string of mis-decoded Latin-1 characters.
 *
 * This test walks the `app/src/main` source tree and fails if any
 * known mojibake byte sequence appears. It's a defensive regression
 * guard: if someone edits a .kt or .xml file on a Windows machine
 * with a misconfigured editor and re-saves as CP1252, the test
 * catches it before it ships.
 *
 * The list of detected mojibake sequences is deliberately narrow —
 * only the high-confidence ones that don't appear in legitimate
 * source. Adding broader patterns would risk false positives on
 * code that genuinely uses Latin-1 supplementary letters (e.g.,
 * a French project name with `é`).
 *
 * Matches the `*DataIntegrity*` filter — picked up by the CI gate.
 */
class Utf8MojibakeDataIntegrityTest {

    /**
     * Each entry is a (mojibake sequence, what it was supposed to be) pair.
     * The sequences are the exact UTF-8 reading of bytes that originated
     * as UTF-8 but were saved through a Windows-1252-thinking editor.
     */
    private val knownMojibake = listOf(
        // ₹ U+20B9 (Indian rupee sign) — UTF-8 bytes E2 82 B9 →
        //   E2=â (U+00E2), 82=‚ (U+201A), B9=¹ (U+00B9). The 3-char string is "â‚¹".
        "â‚¹" to "₹",

        // ⚠ U+26A0 (warning sign) — UTF-8 bytes E2 9A A0 →
        //   E2=â (U+00E2), 9A=š (U+0161), A0=NBSP (U+00A0). The 3-char string is "âš<NBSP>".
        "âš " to "⚠",

        // ️ U+FE0F (variation selector 16, makes ⚠ render as emoji) —
        //   UTF-8 bytes EF B8 8F → EF=ï (U+00EF), B8=¸ (U+00B8), 8F=control (U+008F).
        "ï¸" to "️",

        // — U+2014 (em-dash) — UTF-8 bytes E2 80 94 →
        //   E2=â, 80=€ (U+20AC), 94=" (U+201D in CP1252). The 3-char string is "â€”".
        "â€”" to "—",

        // – U+2013 (en-dash) — UTF-8 bytes E2 80 93 →
        //   E2=â, 80=€, 93=" (U+201C in CP1252). The 3-char string is "â€“".
        "â€“" to "–",

        // ─ U+2500 (box drawings light horizontal) — UTF-8 bytes E2 94 80 →
        //   E2=â, 94=" (U+201D), 80=€. The 3-char string is "â”€".
        "â”€" to "─",
    )

    @Test
    fun `source tree has no UTF-8 mojibake byte sequences`() {
        // Tests run from the `:app` module directory under Gradle, so
        // `src/main` resolves correctly. The fallback (`app/src/main`)
        // handles a hypothetical workspace-root invocation.
        val sourceRoot = listOf(File("src/main"), File("app/src/main"))
            .firstOrNull { it.exists() && it.isDirectory }
            ?: error("Couldn't locate src/main relative to ${File(".").absolutePath}")

        val offenders = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                knownMojibake.forEach { (mojibake, intended) ->
                    var idx = text.indexOf(mojibake)
                    while (idx >= 0) {
                        // Compute the line number for actionable failure output.
                        val line = text.substring(0, idx).count { it == '\n' } + 1
                        offenders += "${file.relativeTo(sourceRoot)}:$line — mojibake for '$intended'"
                        idx = text.indexOf(mojibake, idx + 1)
                    }
                }
            }

        if (offenders.isNotEmpty()) {
            fail(
                "Found ${offenders.size} UTF-8 mojibake sequence(s) in source tree.\n" +
                "These are bytes that look like UTF-8 reading of Windows-1252 misencoded content.\n" +
                "Re-save the offending files as UTF-8 (without BOM). Sites:\n" +
                offenders.joinToString("\n") { "  $it" }
            )
        }
    }

    @Test
    fun `mojibake detection patterns are themselves valid Unicode`() {
        // Lockdown: every (mojibake, intended) entry should have the
        // mojibake be exactly 3 characters (the typical mis-decoding of
        // a 3-byte UTF-8 codepoint) and the intended be a single
        // codepoint (or a base + combining sequence).
        // This stops future contributors from adding patterns that are
        // themselves valid prose (which would create false positives).
        knownMojibake.forEach { (mojibake, intended) ->
            assertTrue(
                "Mojibake '$mojibake' (for '$intended') should be 3 chars (mis-decoded 3-byte UTF-8).",
                mojibake.length == 3,
            )
        }
    }
}
