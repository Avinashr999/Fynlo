package app.fynlo.data

import app.fynlo.data.model.Account
import app.fynlo.data.model.BackupData
import app.fynlo.data.model.Project
import app.fynlo.data.model.Transaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePreviewDataIntegrityTest {

    private fun signed(data: BackupData): String {
        val hash = BackupIntegrity.computeHash(data)
        return Json.encodeToString(data.copy(contentHash = hash))
    }

    @Test
    fun `preview returns user-facing counts for restore confirmation`() {
        val json = signed(
            BackupData(
                schemaVersion = BackupIntegrity.CURRENT_SCHEMA_VERSION,
                appVersion = "3.2.91",
                exportedAt = "2026-06-14T10:00:00Z",
                projects = listOf(Project(id = "personal", name = "Personal")),
                accounts = listOf(Account(id = "a1", name = "Cash", type = "Cash", balance = 100.0)),
                transactions = listOf(
                    Transaction(
                        id = "t1",
                        date = "2026-06-14",
                        type = "income",
                        amount = 100.0,
                        toAcct = "Cash",
                        toAcctId = "a1",
                        category = "Salary",
                    )
                ),
            )
        )

        val preview = BackupRestorePreviewer.preview(json)

        assertEquals(1, preview.counts.first { it.first == "Projects" }.second)
        assertEquals(1, preview.counts.first { it.first == "Accounts" }.second)
        assertEquals(1, preview.counts.first { it.first == "Transactions" }.second)
        assertTrue(preview.warnings.isEmpty())
    }

    @Test
    fun `preview rejects tampered backup before restore`() {
        val data = BackupData(
            schemaVersion = BackupIntegrity.CURRENT_SCHEMA_VERSION,
            accounts = listOf(Account(id = "a1", name = "Cash", type = "Cash", balance = 100.0)),
        )
        val hash = BackupIntegrity.computeHash(data)
        val tamperedJson = Json.encodeToString(
            data.copy(
                contentHash = hash,
                accounts = listOf(Account(id = "a1", name = "Cash", type = "Cash", balance = 999.0)),
            )
        )

        val error = runCatching { BackupRestorePreviewer.preview(tamperedJson) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("integrity", ignoreCase = true))
    }

    @Test
    fun `preview warns when transaction account references are missing`() {
        val json = signed(
            BackupData(
                schemaVersion = BackupIntegrity.CURRENT_SCHEMA_VERSION,
                projects = listOf(Project(id = "personal", name = "Personal")),
                transactions = listOf(
                    Transaction(
                        id = "t1",
                        date = "2026-06-14",
                        type = "expense",
                        amount = 50.0,
                        fromAcct = "Missing Wallet",
                        fromAcctId = "missing-account-id",
                        category = "Food",
                    )
                ),
            )
        )

        val preview = BackupRestorePreviewer.preview(json)

        assertTrue(preview.warnings.any { it.contains("account ID", ignoreCase = true) })
        assertTrue(preview.warnings.any { it.contains("account names", ignoreCase = true) })
    }

    @Test
    fun `preview rejects csv data export with friendly restore message`() {
        val csv = "Fynlo - DATA EXPORT Scope,\"whole\"\nDate,Description,Amount\n"

        val error = runCatching { BackupRestorePreviewer.preview(csv) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("not a restore backup", ignoreCase = true))
    }
}
