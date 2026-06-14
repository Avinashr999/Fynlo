package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C22 (3.2.67) — CsvParser unit tests.
 */
class CsvParserDataIntegrityTest {

    @Test
    fun parsesPlainCommaSeparatedRows() {
        val csv = "a,b,c\n1,2,3"
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            CsvParser.parse(csv)
        )
    }

    @Test
    fun handlesCrlfLineEndings() {
        val csv = "a,b\r\n1,2\r\n"
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            CsvParser.parse(csv)
        )
    }

    @Test
    fun blankInputReturnsEmpty() {
        assertTrue(CsvParser.parse("").isEmpty())
        assertTrue(CsvParser.parse("   ").isEmpty())
    }

    @Test
    fun trailingNewlinesDoNotCreateEmptyRows() {
        val csv = "a,b\n1,2\n\n\n"
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            CsvParser.parse(csv)
        )
    }

    @Test
    fun quotedFieldsKeepEmbeddedCommas() {
        // "Hello, world" is ONE field, not two.
        val csv = "name,note\n\"Smith, John\",\"Hello, world\""
        val rows = CsvParser.parse(csv)
        assertEquals(2, rows.size)
        assertEquals(listOf("Smith, John", "Hello, world"), rows[1])
    }

    @Test
    fun escapedQuotesCollapseToSingleQuote() {
        // RFC 4180 escape: "" inside a quoted field means a literal ".
        // Built with concatenation because Kotlin raw strings can't contain
        // a literal three-quote run.
        val csv = "field1,field2\n" +
                  "\"He said \"\"hi\"\"\",\"ok\"\"\""
        val rows = CsvParser.parse(csv)
        assertEquals("He said \"hi\"", rows[1][0])
        assertEquals("ok\"", rows[1][1])
    }

    @Test
    fun emptyFieldsArePreserved() {
        val csv = "a,b,c\n1,,3"
        assertEquals(listOf("1", "", "3"), CsvParser.parse(csv)[1])
    }

    @Test
    fun trailingEmptyFieldIsPreserved() {
        // "1,2," parses to ["1", "2", ""] — trailing field preserved.
        val csv = "a,b,c\n1,2,"
        assertEquals(listOf("1", "2", ""), CsvParser.parse(csv)[1])
    }

    @Test
    fun parseLineWorksStandaloneForHeaderInspection() {
        assertEquals(listOf("Date", "Amount", "Description"),
            CsvParser.parseLine("Date,Amount,Description"))
    }
}
