package app.fynlo.data

import androidx.test.platform.app.InstrumentationRegistry
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.di.AppModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in history-only recovery. Never runs in ordinary device suites. */
class ApprovedHistoryRecoveryTest {
    @Serializable data class Request(val expectedLedger: BackupData, val repairs: List<NetWorthHistoryRepair>)

    @Test fun applyHistoryOnlyRecovery() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("approvedHistoryRecovery") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "app.fynlo")
        val file = java.io.File(context.filesDir, "approved-history-recovery.json")
        check(file.isFile)
        val request = Json { ignoreUnknownKeys = true }.decodeFromString<Request>(file.readText())
        val db = AppModule.provideDatabase(context)
        try {
            val dao = db.dao()
            val repository = FinanceRepository(dao, db, FirestoreRepository(""), SyncManager("", dao))
            fun <T> assertSame(expected: List<T>, actual: List<T>) = assertEquals(expected.toSet(), actual.toSet())
            assertSame(request.expectedLedger.accounts, dao.getAllAccountsList())
            assertSame(request.expectedLedger.borrowers, dao.getSnapshotBorrowers())
            assertSame(request.expectedLedger.debts, dao.getSnapshotDebts())
            assertSame(request.expectedLedger.investments, dao.getSnapshotInvestments())
            assertSame(request.expectedLedger.payments, dao.getSnapshotPayments())
            assertSame(request.expectedLedger.debtPayments, dao.getSnapshotDebtPayments())
            val before = Json.decodeFromString<BackupData>(repository.getAllDataAsJson())
            repeat(2) { repository.recoverNetWorthHistory("personal", request.repairs) }
            val after = Json.decodeFromString<BackupData>(repository.getAllDataAsJson())
            val beforeFields = Json.encodeToJsonElement(before).jsonObject
            val afterFields = Json.encodeToJsonElement(after).jsonObject
            for (key in beforeFields.keys + afterFields.keys) {
                if (key in setOf("netWorthSnapshots", "exportedAt", "contentHash")) continue
                val a = beforeFields[key]
                val b = afterFields[key]
                // Queries with equal dates need not return rows in the same order.
                val same = if (a is JsonArray && b is JsonArray)
                    a.map { it.toString() }.sorted() == b.map { it.toString() }.sorted()
                else a == b
                check(same) { "Financial backup field changed: $key" }
            }
        } finally { db.close() }
    }
}
