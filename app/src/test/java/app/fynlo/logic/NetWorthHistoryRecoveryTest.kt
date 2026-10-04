package app.fynlo.logic

import app.fynlo.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class NetWorthHistoryRecoveryTest {
    private val ledger = BackupData(accounts = listOf(Account("a", "Cash", "Cash", 500.0)),
        borrowers = listOf(Borrower("b", "B", amount = 1000.0, rate = 36.5, date = "2026-01-01")))

    @Test fun `dated reconstruction uses complete ledger and does not mutate input`() {
        val before = ledger.copy()
        val result = NetWorthHistoryRecovery.reconstruct(ledger, "2026-01-10", "a".repeat(64), "personal", 1)
        assertEquals(1510.0, result.snapshot!!.netWorth, 0.001)
        assertEquals(before, ledger)
        assertTrue(result.reasons.isEmpty())
    }

    @Test fun `bad historical classifications cannot be recovered as trusted history`() {
        val p = Payment("p", "b", "B", "2026-01-02", "Interest Only", 50.0, principal = 20.0, interest = 30.0)
        val result = NetWorthHistoryRecovery.reconstruct(ledger.copy(payments = listOf(p)), "2026-01-10", "a".repeat(64), "personal", 1)
        assertNull(result.snapshot)
        assertEquals(2, result.reasons.size)
    }

    @Test fun `legacy timestamps and plausible totals cannot establish provenance`() {
        val old = NetWorthSnapshot("2026-01-01", 100.0, 100.0, 0.0, createdAt = 123)
        assertFalse(NetWorthHistoryPolicy.isTrusted(old))
        val good = old.copy(captureSource = NetWorthHistoryPolicy.LIVE, sourceReference = "complete read")
        assertTrue(NetWorthHistoryPolicy.isTrusted(good))
        assertNull(NetWorthHistoryPolicy.at(listOf(good), LocalDate.parse("2026-01-02")))
        assertEquals(listOf(good), NetWorthHistoryPolicy.trusted(listOf(old.copy(date = "2025-12-31"), good)))
        assertFalse(NetWorthHistoryPolicy.isTrusted(good.copy(netWorth = 999.0)))
    }

    /** Opt-in offline analysis; inputs/results must stay outside the repository. */
    @Test fun `analyze private dated backup candidates when explicitly provided`() {
        val root = System.getenv("FYNLO_HISTORY_RECOVERY_DIR") ?: return
        val json = Json { ignoreUnknownKeys = true }
        File(root).listFiles { file -> file.name.endsWith("-input.json") }!!.forEach { file ->
            val input = json.parseToJsonElement(file.readText()).jsonObject
            val data = json.decodeFromJsonElement<BackupData>(input.getValue("ledger"))
            val date = input.getValue("date").jsonPrimitive.content
            val result = NetWorthHistoryRecovery.reconstruct(data, date,
                input.getValue("sourceSha256").jsonPrimitive.content, "personal",
                System.currentTimeMillis())
            val report = buildJsonObject {
                put("date", date)
                put("snapshot", result.snapshot?.let { json.encodeToJsonElement(it) } ?: JsonNull)
                put("reasons", json.encodeToJsonElement(result.reasons))
            }
            File(root, file.name.replace("-input.json", "-result.json")).writeText(report.toString())
        }
    }
}
