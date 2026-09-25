package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.logic.InterestEngine
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v3.3.0 repository-level: re-split on delete / back-dated insert, date guard, rounding rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaymentResplitV330DataIntegrityTest {

    private lateinit var db: FynloDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runCatching { FirebaseApp.getInstance() }.getOrElse { FirebaseApp.initializeApp(ctx) }
        db = Room.inMemoryDatabaseBuilder(ctx, FynloDatabase::class.java).allowMainThreadQueries().build()
        repository = FinanceRepository(
            dao = db.dao(), db = db,
            firestore = FirestoreRepository(""),
            syncManager = SyncManager("", db.dao()),
        )
    }

    @After fun tearDown() { db.close() }

    private fun pay(id: String, date: String, amount: Double) =
        Payment(id = id, loanId = "loan", name = "Ravi", date = date, type = "Both", amount = amount)

    private suspend fun seedLoan(intType: String, freq: String = "Monthly") {
        db.dao().insertAccount(Account(id = "acc", name = "Personal Cash", type = "Cash", balance = 0.0))
        db.dao().insertBorrower(Borrower(id = "loan", name = "Ravi", amount = 10_000.0, rate = 12.0,
            date = "2026-01-01", intType = intType, compoundFrequency = freq))
    }

    @Test
    fun `deleting older reducing payment re-splits the later one`() = runBlocking {
        seedLoan("Reducing Balance")
        repository.insertPaymentWithDest(pay("p1", "2026-01-15", 500.0), "Personal Cash", "personal")
        repository.insertPaymentWithDest(pay("p2", "2026-02-01", 200.0), "Personal Cash", "personal")
        val before = db.dao().getPaymentById("p2")!!
        assertEquals(53.37, before.interest, 0.0)
        assertEquals(146.63, before.principal, 0.0)

        val t1 = db.dao().getTransactionsByRef("loan").single { it.category == "Loan Repayment" && it.amount == 500.0 }
        repository.deleteTransaction(t1)

        val after = db.dao().getPaymentById("p2")!!
        assertEquals(105.20, after.interest, 0.0)
        assertEquals(94.80, after.principal, 0.0)
        val b = db.dao().getBorrowerById("loan")!!
        assertEquals(94.80, b.paidPrincipal, 0.0001)
        assertEquals(105.20, b.paidInterest, 0.0001)
        assertEquals(200.0, db.dao().getAccountById("acc")!!.balance, 0.0001)
    }

    @Test
    fun `undoing a compound payment re-splits and rebuilds totals`() = runBlocking {
        seedLoan("Compound Interest", "Quarterly")
        repository.insertPaymentWithDest(pay("p2", "2026-02-01", 200.0), "Personal Cash", "personal")
        // Back-dated insert (earlier than p2) must re-split p2 immediately.
        repository.insertPaymentWithDest(pay("p1", "2026-01-15", 500.0), "Personal Cash", "personal")
        assertEquals(53.37, db.dao().getPaymentById("p2")!!.interest, 0.0)
        // Undo the last action (the back-dated p1) → p2 goes back to the no-p1 split.
        assertTrue(repository.undoLastMoneyAction())
        val p2 = db.dao().getPaymentById("p2")!!
        assertEquals(105.20, p2.interest, 0.0)
        assertEquals(94.80, p2.principal, 0.0)
        assertEquals(94.80, db.dao().getBorrowerById("loan")!!.paidPrincipal, 0.0001)
    }

    @Test
    fun `payment dated before start is rejected with a clear error`() = runBlocking {
        seedLoan("Simple Interest")
        try {
            repository.insertPaymentWithDest(pay("early", "2025-12-31", 100.0), "Personal Cash", "personal")
            fail("expected PaymentDateException")
        } catch (e: FinanceRepository.PaymentDateException) {
            assertEquals(InterestEngine.PAYMENT_BEFORE_START_MESSAGE, e.message)
        }
        assertTrue(db.dao().getPaymentsForLoanOnce("loan").isEmpty())
        assertEquals(0.0, db.dao().getAccountById("acc")!!.balance, 0.0)
        // Future-dated payment is allowed.
        repository.insertPaymentWithDest(pay("future", "2099-01-01", 100.0), "Personal Cash", "personal")
        assertEquals(1, db.dao().getPaymentsForLoanOnce("loan").size)
    }

    @Test
    fun `debt settled within 1 rupee short closes with audited write-off and exact ledger`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc", name = "Personal Cash", type = "Cash", balance = 20_000.0))
        db.dao().insertDebt(Debt(id = "debt", name = "Lender", amount = 10_000.0, rate = 12.0, date = "2026-01-01"))
        fun dp(id: String, amount: Double) =
            DebtPayment(id = id, debtId = "debt", name = "Lender", date = "2026-01-31", type = "Both", amount = amount)
        repository.insertDebtPaymentWithSource(dp("d1", 50.0), "Personal Cash", "personal")
        repository.insertDebtPaymentWithSource(dp("d2", 10_052.0), "Personal Cash", "personal")
        val d2 = db.dao().getDebtPaymentById("d2")!!
        assertTrue(kotlin.math.abs(d2.roundingPaise) < InterestEngine.ROUNDING_THRESHOLD_PAISE)
        assertEquals(0L, d2.penaltyPaise)
        db.dao().getDebtPaymentsForDebtOnce("debt").forEach { r ->
            assertEquals(
                InterestEngine.rupeesToPaise(r.amount),
                InterestEngine.rupeesToPaise(r.interest) + InterestEngine.rupeesToPaise(r.principal) + r.penaltyPaise + r.roundingPaise,
            )
        }
        val debt = db.dao().getDebtById("debt")!!
        assertEquals(10_000.0, debt.paidPrincipal, 0.0001)
        assertEquals(101.91, debt.paidInterest, 0.0001)
        assertEquals(20_000.0 - 10_102.0, db.dao().getAccountById("acc")!!.balance, 0.0001)
    }

    @Test
    fun `loan overpaid by 1 rupee stores penaltyPaise`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc", name = "Personal Cash", type = "Cash", balance = 0.0))
        db.dao().insertBorrower(Borrower(id = "loan", name = "Ravi", amount = 100.0, rate = 0.0, date = "2026-01-01"))
        repository.insertPaymentWithDest(pay("p", "2026-02-01", 101.0), "Personal Cash", "personal")
        val p = db.dao().getPaymentById("p")!!
        assertEquals(100L, p.penaltyPaise)
        assertEquals(0L, p.roundingPaise)
        assertTrue(p.notes.contains("Penalty on this account ₹1"))
    }
}
