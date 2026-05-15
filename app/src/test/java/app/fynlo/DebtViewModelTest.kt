package app.fynlo

import app.fynlo.data.AccountRepository
import app.fynlo.data.DebtRepository
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class DebtViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var debtRepo: DebtRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var viewModel: DebtViewModel

    private val sampleDebts = listOf(
        Debt(id = "d1", name = "Home Loan", amount = 200_000.0, rate = 8.5,
            date = "01-01-2022", type = "Reducing Balance",
            paid = 0.0, paidPrincipal = 0.0, paidInterest = 0.0, projectId = "proj1"),
        Debt(id = "d2", name = "Car Loan",  amount = 50_000.0,  rate = 11.0,
            date = "01-06-2023", type = "Reducing Balance",
            paid = 0.0, paidPrincipal = 0.0, paidInterest = 0.0, projectId = "proj1")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        debtRepo  = mockk(relaxed = true)
        accountRepo = mockk(relaxed = true)
        every { debtRepo.allDebts }        returns flowOf(sampleDebts)
        every { debtRepo.allDebtPayments } returns flowOf(emptyList())
        every { accountRepo.allAccounts }  returns flowOf(emptyList())
        viewModel = DebtViewModel(debtRepo, accountRepo)
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `debts state initialises from repository`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.debts.value.size)
    }

    @Test
    fun `addDebt delegates to repository`() = runTest {
        val debt = Debt(id = UUID.randomUUID().toString(), name = "Personal Loan",
            amount = 10_000.0, rate = 14.0, date = "01-05-2024",
            type = "Simple", paid = 0.0, paidPrincipal = 0.0, paidInterest = 0.0,
            projectId = "proj1")
        viewModel.addDebt(debt)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { debtRepo.insertDebt(debt) }
    }

    @Test
    fun `payDebt delegates to repository with correct amounts`() = runTest {
        val debt = sampleDebts.first()
        val payment = DebtPayment(id = UUID.randomUUID().toString(), debtId = debt.id,
            amount = 5_708.33, principal = 5_000.0, interest = 708.33,
            date = "01-02-2022", projectId = "proj1")
        viewModel.payDebt(debt, 5_000.0, 708.33, payment)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { debtRepo.payInstalment(debt, 5_000.0, 708.33, payment) }
    }

    @Test
    fun `deleteDebt delegates to repository`() = runTest {
        val debt = sampleDebts.last()
        viewModel.deleteDebt(debt)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { debtRepo.deleteDebt(debt) }
    }

    @Test
    fun `updateDebt propagates changes`() = runTest {
        val debt    = sampleDebts.first()
        val updated = debt.copy(rate = 9.0)
        viewModel.updateDebt(updated)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { debtRepo.updateDebt(match { it.rate == 9.0 }) }
    }
}
