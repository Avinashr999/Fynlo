package app.fynlo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.LendingRepository
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.data.remote.FirestoreRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LendingRepositoryTest {

    private lateinit var db: FynloDatabase
    private lateinit var repo: LendingRepository
    private val firestore: FirestoreRepository = mockk(relaxed = true)

    private fun sampleBorrower(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Borrower",
        amount: Double = 10_000.0,
        rate: Double = 12.0,
        paid: Double = 0.0,
        paidPrincipal: Double = 0.0,
        paidInterest: Double = 0.0,
        projectId: String = "personal"
    ) = Borrower(
        id            = id,
        name          = name,
        amount        = amount,
        rate          = rate,
        date          = "2024-01-01",
        due           = "2025-01-01",
        type          = "Simple Interest",
        paid          = paid,
        paidPrincipal = paidPrincipal,
        paidInterest  = paidInterest,
        status        = "Active",
        notes         = "",
        phone         = "",
        projectId     = projectId,
        frozenInterest = 0.0,
        defaultDate   = "",
        sourceAccount = "",
        withdrawn     = 0.0
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FynloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LendingRepository(db.dao(), firestore)
    }

    @After
    fun tearDown() { db.close() }

    // ── Insert & retrieve ─────────────────────────────────────────────────────
    @Test
    fun `insertBorrower - borrower appears in allBorrowers flow`() = runTest {
        val b = sampleBorrower(name = "Ramu")
        repo.insertBorrower(b)
        val all = repo.allBorrowers.first()
        assertTrue("Borrower should be in list", all.any { it.name == "Ramu" })
    }

    @Test
    fun `insertBorrower - multiple borrowers retrieved in order`() = runTest {
        repo.insertBorrower(sampleBorrower(name = "Alice"))
        repo.insertBorrower(sampleBorrower(name = "Bob"))
        repo.insertBorrower(sampleBorrower(name = "Charlie"))
        assertEquals(3, repo.allBorrowers.first().size)
    }

    // ── Update ────────────────────────────────────────────────────────────────
    @Test
    fun `updateBorrower - name change persists`() = runTest {
        val b = sampleBorrower(id = "u1", name = "Original")
        repo.insertBorrower(b)
        repo.updateBorrower(b.copy(name = "Updated"))
        val found = repo.allBorrowers.first().find { it.id == "u1" }
        assertEquals("Updated", found?.name)
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    @Test
    fun `deleteBorrower - borrower removed from list`() = runTest {
        val b = sampleBorrower(id = "del1")
        repo.insertBorrower(b)
        repo.deleteBorrower(b)
        assertTrue(repo.allBorrowers.first().none { it.id == "del1" })
    }

    // ── Payment collection ────────────────────────────────────────────────────
    @Test
    fun `collectPayment - paid field increases correctly`() = runTest {
        val b = sampleBorrower(id = "pay1", amount = 10_000.0)
        repo.insertBorrower(b)

        val payment = Payment(
            id            = UUID.randomUUID().toString(),
            loanId        = "pay1",
            amount        = 1_200.0,
            date          = "2024-06-01",
            notes         = "Monthly collection",
            principalPaid = 1_000.0,
            interestPaid  = 200.0,
            projectId     = "personal"
        )
        repo.collectPayment(b, 1_200.0, 1_000.0, 200.0, payment)

        val updated = repo.allBorrowers.first().find { it.id == "pay1" }
        assertNotNull(updated)
        assertEquals(1_200.0, updated!!.paid, 0.01)
        assertEquals(1_000.0, updated.paidPrincipal, 0.01)
        assertEquals(200.0,   updated.paidInterest,  0.01)
    }

    @Test
    fun `collectPayment - payment recorded in payments table`() = runTest {
        val b = sampleBorrower(id = "pay2")
        repo.insertBorrower(b)
        val payment = Payment(
            id = "pmt1", loanId = "pay2", amount = 500.0,
            date = "2024-03-01", notes = "", principalPaid = 500.0,
            interestPaid = 0.0, projectId = "personal"
        )
        repo.collectPayment(b, 500.0, 500.0, 0.0, payment)
        val payments = repo.getPaymentsForLoan("pay2")
        assertEquals(1, payments.size)
        assertEquals(500.0, payments[0].amount, 0.01)
    }

    @Test
    fun `collectPayment - multiple payments accumulate correctly`() = runTest {
        val b = sampleBorrower(id = "pay3", amount = 50_000.0)
        repo.insertBorrower(b)

        for (i in 1..3) {
            val current = repo.allBorrowers.first().find { it.id == "pay3" }!!
            val pmt = Payment(
                id = "p$i", loanId = "pay3", amount = 1_000.0,
                date = "2024-0$i-01", notes = "", principalPaid = 800.0,
                interestPaid = 200.0, projectId = "personal"
            )
            repo.collectPayment(current, 1_000.0, 800.0, 200.0, pmt)
        }

        val final = repo.allBorrowers.first().find { it.id == "pay3" }!!
        assertEquals(3_000.0,  final.paid,          0.01)
        assertEquals(2_400.0,  final.paidPrincipal, 0.01)
        assertEquals(600.0,    final.paidInterest,  0.01)
        assertEquals(3, repo.getPaymentsForLoan("pay3").size)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────
    @Test
    fun `zero rate borrower has no interest type`() = runTest {
        val handLoan = sampleBorrower(rate = 0.0, name = "Hand Loan")
        repo.insertBorrower(handLoan)
        val found = repo.allBorrowers.first().find { it.name == "Hand Loan" }
        assertEquals(0.0, found!!.rate, 0.0)
    }

    @Test
    fun `markDefaulted - status changes to Defaulted`() = runTest {
        val b = sampleBorrower(id = "def1")
        repo.insertBorrower(b)
        repo.markDefaulted("def1", frozenInterest = 1_500.0, defaultDate = "2024-12-01")
        val updated = repo.allBorrowers.first().find { it.id == "def1" }
        assertEquals("Defaulted", updated?.status)
        assertEquals(1_500.0, updated?.frozenInterest ?: 0.0, 0.01)
    }

    @Test
    fun `getPaymentsForLoan - returns empty list when no payments`() = runTest {
        val payments = repo.getPaymentsForLoan("nonexistent-loan-id")
        assertTrue(payments.isEmpty())
    }
}
