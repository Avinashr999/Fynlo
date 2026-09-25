package app.fynlo.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3.3.1 — FynloDao.rebuild*Paid* must not count a principal-0 row that carries
 * interest (or only a penalty) as its whole amount of principal. Only a legacy
 * row with no split at all (principal 0, interest 0, no penalty) counts amount.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RebuildPaidPrincipalJvmTest {

    private lateinit var db: FynloDatabase
    private val dao get() = db.dao()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FynloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `borrower principal-0 interest row counts 0 principal`() = runBlocking {
        dao.insertBorrower(Borrower(id = "L", name = "M", amount = 100_000.0, rate = 24.0, date = "2026-01-01", intType = "Simple Interest"))
        // Saved split: interest only, but typed 'Both' (the bug: counted as ₹2,000 principal).
        dao.insertPayment(Payment(id = "a", loanId = "L", name = "M", date = "2026-02-01", type = "Both", amount = 2_000.0, principal = 0.0, interest = 2_000.0))
        // Explicit Interest Only, lower-case / padded.
        dao.insertPayment(Payment(id = "b", loanId = "L", name = "M", date = "2026-03-01", type = " interest only ", amount = 1_500.0, principal = 0.0, interest = 0.0))
        // Genuine split row.
        dao.insertPayment(Payment(id = "c", loanId = "L", name = "M", date = "2026-04-01", type = "Both", amount = 5_000.0, principal = 3_000.0, interest = 2_000.0))
        // Penalty-only row.
        dao.insertPayment(Payment(id = "d", loanId = "L", name = "M", date = "2026-04-02", type = "Both", amount = 1.0, principal = 0.0, interest = 0.0, penaltyPaise = 100L))
        // Legacy row with no split at all still counts its whole amount.
        dao.insertPayment(Payment(id = "e", loanId = "L", name = "M", date = "2026-05-01", type = "Both", amount = 700.0, principal = 0.0, interest = 0.0))
        dao.rebuildBorrowerPaidFromPayments()
        val b = dao.getBorrowerById("L")!!
        assertEquals(3_700.0, b.paidPrincipal, 0.0)
        assertEquals(5_500.0, b.paidInterest, 0.0)
        assertEquals(9_200.0, b.paid, 0.0)
    }

    @Test
    fun `debt principal-0 interest row counts 0 principal`() = runBlocking {
        dao.insertDebt(Debt(id = "D", name = "M", amount = 100_000.0, rate = 24.0, date = "2026-01-01", intType = "Simple Interest"))
        dao.insertDebtPayment(DebtPayment(id = "a", debtId = "D", name = "M", date = "2026-02-01", type = "Both", amount = 2_000.0, principal = 0.0, interest = 2_000.0))
        dao.insertDebtPayment(DebtPayment(id = "b", debtId = "D", name = "M", date = "2026-03-01", type = "Interest Only", amount = 1_500.0))
        dao.insertDebtPayment(DebtPayment(id = "c", debtId = "D", name = "M", date = "2026-04-01", type = "Both", amount = 5_000.0, principal = 3_000.0, interest = 2_000.0))
        dao.insertDebtPayment(DebtPayment(id = "e", debtId = "D", name = "M", date = "2026-05-01", type = "Both", amount = 700.0))
        dao.rebuildDebtPaidFromDebtPayments()
        val d = dao.getDebtById("D")!!
        assertEquals(3_700.0, d.paidPrincipal, 0.0)
        assertEquals(5_500.0, d.paidInterest, 0.0)
        assertEquals(9_200.0, d.paid, 0.0)
    }
}
