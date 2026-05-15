package app.fynlo

import app.fynlo.data.AccountRepository
import app.fynlo.data.LendingRepository
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Person
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class LendingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var lendingRepo: LendingRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var viewModel: LendingViewModel

    private val sampleBorrowers = listOf(
        Borrower(id = "b1", name = "Alice", amount = 10_000.0, rate = 12.0,
            date = "01-01-2024", type = "Compound", projectId = "proj1"),
        Borrower(id = "b2", name = "Bob",   amount = 5_000.0,  rate = 0.0,
            date = "15-03-2024", type = "Hand Loan", projectId = "proj1")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        lendingRepo = mockk(relaxed = true)
        accountRepo = mockk(relaxed = true)

        every { lendingRepo.allBorrowers } returns flowOf(sampleBorrowers)
        every { lendingRepo.allPayments  } returns flowOf(emptyList())
        every { accountRepo.allAccounts  } returns flowOf(emptyList())
        every { accountRepo.allPeople    } returns flowOf(emptyList())

        viewModel = LendingViewModel(lendingRepo, accountRepo)
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `borrowers state initialises from repository`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val borrowers = viewModel.borrowers.value
        assertEquals(2, borrowers.size)
        assertTrue(borrowers.any { it.name == "Alice" })
    }

    @Test
    fun `addBorrower delegates to repository`() = runTest {
        val newBorrower = Borrower(id = UUID.randomUUID().toString(), name = "Carol",
            amount = 20_000.0, rate = 10.0, date = "01-06-2024",
            type = "Simple", projectId = "proj1")
        viewModel.addBorrower(newBorrower, "Cash in Hand")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { lendingRepo.insertBorrower(newBorrower) }
    }

    @Test
    fun `deleteBorrower delegates to repository`() = runTest {
        val borrower = sampleBorrowers.first()
        viewModel.deleteBorrower(borrower)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { lendingRepo.deleteBorrower(borrower) }
    }

    @Test
    fun `collectLoanPayment delegates full payment to repository`() = runTest {
        val borrower = sampleBorrowers.first()
        val payment  = Payment(id = UUID.randomUUID().toString(), loanId = borrower.id,
            amount = 1_000.0, date = "01-06-2024", projectId = "proj1")
        viewModel.collectLoanPayment(borrower, 1_000.0, 800.0, 200.0, payment)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { lendingRepo.collectPayment(borrower, 1_000.0, 800.0, 200.0, payment) }
    }

    @Test
    fun `markBorrowerDefaulted delegates to repository`() = runTest {
        viewModel.markBorrowerDefaulted("b1", 500.0, "15-05-2024")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { lendingRepo.markDefaulted("b1", 500.0, "15-05-2024") }
    }

    @Test
    fun `restoreBorrowerToActive sets status to Active`() = runTest {
        val borrower = sampleBorrowers.first().copy(status = "Defaulted")
        viewModel.restoreBorrowerToActive(borrower)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { lendingRepo.updateBorrower(match { it.status == "Active" }) }
    }

    @Test
    fun `writeOffBorrower sets status to Written Off`() = runTest {
        val borrower = sampleBorrowers.first()
        viewModel.writeOffBorrower(borrower)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { lendingRepo.updateBorrower(match { it.status == "Written Off" }) }
    }
}
