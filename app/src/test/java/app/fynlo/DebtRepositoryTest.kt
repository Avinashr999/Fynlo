package app.fynlo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.DebtRepository
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.remote.FirestoreRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class DebtRepositoryTest {

    private lateinit var db: FynloDatabase
    private lateinit var repo: DebtRepository
    private val firestore: FirestoreRepository = mockk(relaxed = true)

    private fun sampleDebt(
        id: String = UUID.randomUUID().toString(),
        name: String = "Home Loan",
        amount: Double = 200_000.0,
        rate: Double = 8.5,
        paid: Double = 0.0,
        paidPrincipal: Double = 0.0,
        paidInterest: Double = 0.0
    ) = Debt(
        id = id, name = name, amount = amount, rate = rate,
        date = "01-01-2024", type = "Reducing Balance",
        paid = paid, paidPrincipal = paidPrincipal, paidInterest = paidInterest,
        projectId = "proj1"
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FynloDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = DebtRepository(db.dao(), firestore)
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun `insert debt persists to db`() = runTest {
        val debt = sampleDebt()
        repo.insertDebt(debt)
        val result = repo.allDebts.first()
        assertTrue(result.any { it.id == debt.id })
    }

    @Test
    fun `insert debt syncs to firestore`() = runTest {
        val debt = sampleDebt()
        repo.insertDebt(debt)
        coVerify { firestore.upsertDebt(debt) }
    }

    @Test
    fun `delete debt removes from db`() = runTest {
        val debt = sampleDebt()
        repo.insertDebt(debt)
        repo.deleteDebt(debt)
        val result = repo.allDebts.first()
        assertFalse(result.any { it.id == debt.id })
    }

    @Test
    fun `payInstalment reduces outstanding correctly`() = runTest {
        val debt = sampleDebt(amount = 100_000.0)
        repo.insertDebt(debt)

        val principal = 5_000.0
        val interest  = 708.33
        val payment = DebtPayment(
            id = UUID.randomUUID().toString(),
            debtId = debt.id, amount = principal + interest,
            principal = principal, interest = interest,
            date = "15-05-2024", projectId = "proj1"
        )
        repo.payInstalment(debt, principal, interest, payment)

        val updated = repo.allDebts.first().first { it.id == debt.id }
        assertEquals(principal, updated.paidPrincipal, 0.01)
        assertEquals(interest,  updated.paidInterest,  0.01)
        assertEquals(principal + interest, updated.paid, 0.01)
    }

    @Test
    fun `payInstalment syncs payment and debt to firestore`() = runTest {
        val debt = sampleDebt()
        repo.insertDebt(debt)
        val payment = DebtPayment(
            id = UUID.randomUUID().toString(), debtId = debt.id,
            amount = 1_000.0, principal = 800.0, interest = 200.0,
            date = "15-05-2024", projectId = "proj1"
        )
        repo.payInstalment(debt, 800.0, 200.0, payment)
        coVerify { firestore.upsertDebtPayment(payment) }
        coVerify(atLeast = 2) { firestore.upsertDebt(any()) } // insert + update
    }

    @Test
    fun `multiple payments accumulate correctly`() = runTest {
        val debt = sampleDebt(amount = 50_000.0)
        repo.insertDebt(debt)

        repeat(3) { i ->
            val current = repo.allDebts.first().first { it.id == debt.id }
            val pmt = DebtPayment(
                id = UUID.randomUUID().toString(), debtId = debt.id,
                amount = 1_000.0, principal = 800.0, interest = 200.0,
                date = "0${i+1}-06-2024", projectId = "proj1"
            )
            repo.payInstalment(current, 800.0, 200.0, pmt)
        }

        val final = repo.allDebts.first().first { it.id == debt.id }
        assertEquals(2_400.0, final.paidPrincipal, 0.01)
        assertEquals(600.0,   final.paidInterest,  0.01)
    }
}
