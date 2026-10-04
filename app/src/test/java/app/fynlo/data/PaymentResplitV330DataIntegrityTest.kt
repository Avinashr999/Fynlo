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
import app.fynlo.logic.InterestPolicy
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
    fun `deleting older reducing payment preserves later saved purpose`() = runBlocking {
        seedLoan("Reducing Balance")
        repository.insertPaymentWithDest(pay("p1", "2026-01-15", 500.0), "Personal Cash", "personal")
        repository.insertPaymentWithDest(pay("p2", "2026-02-01", 200.0), "Personal Cash", "personal")
        val before = db.dao().getPaymentById("p2")!!
        assertEquals(53.37, before.interest, 0.0)
        assertEquals(146.63, before.principal, 0.0)

        val t1 = db.dao().getTransactionsByRef("loan").single { it.category == "Loan Repayment" && it.amount == 500.0 }
        repository.deleteTransaction(t1)

        val after = db.dao().getPaymentById("p2")!!
        assertEquals(before, after)
        val b = db.dao().getBorrowerById("loan")!!
        assertEquals(before.principal, b.paidPrincipal, 0.0001)
        assertEquals(before.interest, b.paidInterest, 0.0001)
        assertEquals(200.0, db.dao().getAccountById("acc")!!.balance, 0.0001)
    }

    @Test
    fun `backdating and undoing a compound payment preserves other payment purpose`() = runBlocking {
        seedLoan("Compound Interest", "Quarterly")
        repository.insertPaymentWithDest(pay("p2", "2026-02-01", 200.0), "Personal Cash", "personal")
        // A new payment may change interest due, but not the purpose of an existing receipt.
        repository.insertPaymentWithDest(pay("p1", "2026-01-15", 500.0), "Personal Cash", "personal")
        assertEquals(105.20, db.dao().getPaymentById("p2")!!.interest, 0.0)
        // Undo removes only the last payment.
        assertTrue(repository.undoLastMoneyAction())
        val p2 = db.dao().getPaymentById("p2")!!
        assertEquals(105.20, p2.interest, 0.0)
        assertEquals(94.80, p2.principal, 0.0)
        assertEquals(94.80, db.dao().getBorrowerById("loan")!!.paidPrincipal, 0.0001)
    }

    @Test
    fun `remote payment cannot overwrite a newer or equal timestamp local correction`() = runBlocking {
        val corrected = pay("p", "2026-03-04", 290.0).copy(type = "Interest Only", interest = 290.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST, updatedAt = 200L)
        db.dao().insertPayment(corrected)
        val wrong = corrected.copy(type = "Both", principal = 200.0, interest = 90.0, updatedAt = 100L)
        db.dao().insertRemotePaymentIfNewer(wrong)
        db.dao().insertRemotePaymentIfNewer(wrong.copy(updatedAt = 200L))
        assertEquals(corrected, db.dao().getPaymentById("p"))
        db.dao().insertRemotePaymentIfNewer(corrected.copy(notes = "newer", updatedAt = 300L))
        assertEquals("newer", db.dao().getPaymentById("p")!!.notes)

        val debtRow = DebtPayment(id = "d", debtId = "debt", name = "Test", date = "2026-03-04",
            type = "Interest Only", amount = 290.0, interest = 290.0, updatedAt = 200L)
        db.dao().insertDebtPayment(debtRow)
        db.dao().insertRemoteDebtPaymentIfNewer(debtRow.copy(principal = 200.0, interest = 90.0, updatedAt = 100L))
        db.dao().insertRemoteDebtPaymentIfNewer(debtRow.copy(principal = 200.0, interest = 90.0))
        assertEquals(debtRow, db.dao().getDebtPaymentById("d"))
        db.dao().insertRemoteDebtPaymentIfNewer(debtRow.copy(notes = "newer", updatedAt = 300L))
        assertEquals("newer", db.dao().getDebtPaymentById("d")!!.notes)
    }

    @Test
    fun `old interest remains unchanged after new payment and correction does not move cash`() = runBlocking {
        seedLoan("Simple Interest")
        val old = pay("old", "2026-02-03", 200.0).copy(type = "Interest Only", interest = 200.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2026-01-01", interestPeriodEndDate = "2026-01-31")
        db.dao().insertPayment(old)
        val new = pay("new", "2026-03-04", 290.0).copy(type = "Interest Only", interest = 290.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2026-02-01", interestPeriodEndDate = "2026-02-28")
        repository.insertPaymentWithDest(new, "Personal Cash")
        repository.insertPaymentWithDest(new, "Personal Cash")
        assertEquals(old, db.dao().getPaymentById(old.id))
        assertEquals(290.0, db.dao().getAccountById("acc")!!.balance, 0.0)
        assertEquals(0.0, db.dao().getBorrowerById("loan")!!.paidPrincipal, 0.0)
        val saved = db.dao().getPaymentById(new.id)!!
        val wrong = saved.copy(type = "Both", principal = 200.0, interest = 90.0)
        db.dao().insertPayment(wrong)
        val transactions = db.dao().getTransactionsByRef("loan")
        repository.correctBorrowerPaymentClassifications(listOf(wrong), listOf(saved))
        repository.correctBorrowerPaymentClassifications(listOf(wrong), listOf(saved))
        assertEquals(saved, db.dao().getPaymentById(new.id)!!.copy(updatedAt = saved.updatedAt))
        assertEquals(transactions, db.dao().getTransactionsByRef("loan"))
        assertEquals(290.0, db.dao().getAccountById("acc")!!.balance, 0.0)
        assertEquals(2, db.dao().getPaymentsForLoanOnce("loan").size)
        assertEquals(0.0, db.dao().getBorrowerById("loan")!!.paidPrincipal, 0.0)
    }

    @Test
    fun `correction rejects a stale payment and a changed cash amount`() = runBlocking {
        seedLoan("Simple Interest")
        val row = pay("interest", "2026-02-03", 200.0).copy(type = "Interest Only", interest = 200.0)
        db.dao().insertPayment(row.copy(notes = "changed"))
        try {
            repository.correctBorrowerPaymentClassifications(listOf(row), listOf(row.copy(
                interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST)))
            fail("stale correction must fail")
        } catch (_: IllegalArgumentException) { }
        try {
            val current = db.dao().getPaymentById(row.id)!!
            repository.correctBorrowerPaymentClassifications(listOf(current), listOf(current.copy(amount = 300.0, interest = 300.0)))
            fail("cash changes must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(row.copy(notes = "changed"), db.dao().getPaymentById(row.id))
    }

    @Test
    fun `debt interest purpose remains unchanged and debits cash only once`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc", name = "Personal Cash", type = "Cash", balance = 50_000.0))
        val debt = Debt(id = "debt", name = "Test", amount = 1_400_000.0, rate = 24.0, date = "2026-09-01")
        db.dao().insertDebt(debt)
        val row = DebtPayment(id = "interest", debtId = debt.id, name = debt.name, date = "2026-10-04",
            type = "Interest Only", amount = 29_000.0, interest = 29_000.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2026-09-01", interestPeriodEndDate = "2026-09-30")
        repository.insertDebtPaymentWithSource(row, "Personal Cash")
        repository.insertDebtPaymentWithSource(row, "Personal Cash")
        assertEquals(21_000.0, db.dao().getAccountById("acc")!!.balance, 0.0)
        assertEquals(0.0, db.dao().getDebtById(debt.id)!!.paidPrincipal, 0.0)
        assertEquals(1, db.dao().getDebtPaymentsForDebtOnce(debt.id).size)
        assertEquals(29_000.0, db.dao().getDebtPaymentById(row.id)!!.interest, 0.0)
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
