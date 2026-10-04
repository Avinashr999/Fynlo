package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NetWorthCaptureDataIntegrityTest {
    private lateinit var db: FynloDatabase
    private lateinit var repository: FinanceRepository
    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { FirebaseApp.getInstance() }.getOrElse { FirebaseApp.initializeApp(context) }
        db = Room.inMemoryDatabaseBuilder(context, FynloDatabase::class.java).allowMainThreadQueries().build()
        repository = FinanceRepository(db.dao(), db, FirestoreRepository(""), SyncManager("", db.dao()))
    }
    @After fun tearDown() = db.close()

    private suspend fun seed() {
        db.dao().insertAccount(Account("cash", "Cash", "Cash", 1000.0))
        db.dao().insertInvestment(Investment("inv", "Investment", "Other", invested = 2000.0, currentVal = 2000.0, date = "2026-01-01"))
        db.dao().insertBorrower(Borrower("loan", "Borrower", amount = 10000.0, rate = 36.5, date = "2026-01-01", intType = "Simple Interest"))
        db.dao().insertDebt(Debt("debt", "Lender", amount = 5000.0, rate = 36.5, date = "2026-01-01", intType = "Simple Interest"))
    }

    @Test fun `capture reads full ledger without UI collectors and replaces today's partial snapshot`() = runBlocking {
        seed()
        val old = NetWorthSnapshot("2026-01-09", 1000.0, 1000.0, 0.0)
        db.dao().insertNetWorthSnapshot(old)
        db.dao().insertNetWorthSnapshot(old.copy(date = "2026-01-10"))
        val saved = repository.captureNetWorthSnapshot("personal", "2026-01-10")
        assertEquals(13100.0, saved.totalAssets, 0.001)
        assertEquals(5050.0, saved.totalLiabilities, 0.001)
        assertEquals(8050.0, saved.netWorth, 0.001)
        assertTrue(saved.createdAt > 0)
        assertEquals(old, db.dao().getNetWorthSnapshotForDate(old.date))
    }

    @Test fun `capture applies saved payment rows and never changes money records`() = runBlocking {
        seed()
        val p = Payment("p", "loan", "Borrower", "2026-01-05", "Principal Only", 1000.0, principal = 1000.0)
        val d = DebtPayment("dp", "debt", "Lender", "2026-01-05", "Principal Only", 500.0, principal = 500.0)
        db.dao().insertPayment(p)
        db.dao().insertDebtPayment(d)
        val accounts = db.dao().getAllAccountsList()
        val borrowers = db.dao().getSnapshotBorrowers()
        val debts = db.dao().getSnapshotDebts()
        val investments = db.dao().getSnapshotInvestments()
        repeat(2) {
            val saved = repository.captureNetWorthSnapshot("personal", "2026-01-10")
            assertEquals(12095.0, saved.totalAssets, 0.001)
            assertEquals(4547.5, saved.totalLiabilities, 0.001)
            assertEquals(7547.5, saved.netWorth, 0.001)
        }
        assertEquals(accounts, db.dao().getAllAccountsList())
        assertEquals(borrowers, db.dao().getSnapshotBorrowers())
        assertEquals(debts, db.dao().getSnapshotDebts())
        assertEquals(investments, db.dao().getSnapshotInvestments())
        assertEquals(listOf(p), db.dao().getSnapshotPayments())
        assertEquals(listOf(d), db.dao().getSnapshotDebtPayments())
        assertEquals(1, db.dao().getNetWorthSnapshotsOnce("personal").size)
        assertTrue(db.dao().getAllTransactionsList().isEmpty())
    }

    @Test fun `capture uses project scope and preserves other project same-date history`() = runBlocking {
        seed()
        db.dao().insertAccount(Account("other", "Other cash", "Cash", 900000.0, projectId = "other"))
        db.dao().insertAccount(Account("legacy", "Legacy cash", "Cash", 500.0, projectId = ""))
        val saved = repository.captureNetWorthSnapshot("personal", "2026-01-10")
        assertEquals(8550.0, saved.netWorth, 0.001)
        try {
            repository.captureNetWorthSnapshot("other", "2026-01-10")
            fail("Another project's row must not be overwritten")
        } catch (_: IllegalArgumentException) { }
        assertEquals(saved, db.dao().getNetWorthSnapshotForDate("2026-01-10"))
    }

    @Test fun `zero net worth with assets and debts is retained and older history is never deleted`() = runBlocking {
        db.dao().insertAccount(Account("a", "Cash", "Cash", 5000.0))
        db.dao().insertDebt(Debt("d", "Debt", amount = 5000.0, rate = 0.0, date = "2026-01-01"))
        val oldZero = NetWorthSnapshot(date = "2026-01-09")
        db.dao().insertNetWorthSnapshot(oldZero)
        val saved = repository.captureNetWorthSnapshot("personal", "2026-01-10")
        assertEquals(0.0, saved.netWorth, 0.0)
        assertEquals(5000.0, saved.totalAssets, 0.0)
        assertEquals(5000.0, saved.totalLiabilities, 0.0)
        assertEquals(oldZero, db.dao().getNetWorthSnapshotForDate(oldZero.date))
    }
}
