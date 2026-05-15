package app.fynlo

import app.fynlo.data.FinanceRepository
import app.fynlo.data.SyncStatus
import app.fynlo.data.model.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ViewModelStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FinanceRepository
    private lateinit var viewModel: FinanceViewModel

    private val fakeProjects = listOf(
        Project(id = "personal", name = "Personal", icon = "person", color = "#059669", currency = "INR", createdAt = ""),
        Project(id = "biz1",     name = "Business",  icon = "work",   color = "#3b82f6", currency = "INR", createdAt = "")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)

        // Set up flows
        every { repository.allProjects }        returns flowOf(fakeProjects)
        every { repository.allBorrowers }       returns flowOf(emptyList())
        every { repository.allTransactions }    returns flowOf(emptyList())
        every { repository.allAccounts }        returns flowOf(emptyList())
        every { repository.allInvestments }     returns flowOf(emptyList())
        every { repository.allDebts }           returns flowOf(emptyList())
        every { repository.allPayments }        returns flowOf(emptyList())
        every { repository.allDebtPayments }    returns flowOf(emptyList())
        every { repository.allPeople }          returns flowOf(emptyList())
        every { repository.allBudgets }         returns flowOf(emptyList())
        every { repository.allGoals }           returns flowOf(emptyList())
        every { repository.allRecurring }       returns flowOf(emptyList())
        every { repository.allFlowTemplates }   returns flowOf(emptyList())
        every { repository.syncStatus }         returns MutableStateFlow(SyncStatus.Idle)
        every { repository.getNetWorthSnapshots(any()) } returns flowOf(emptyList())

        viewModel = FinanceViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────
    @Test
    fun `initial uiState is Initial`() {
        assertEquals(UiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun `initial currentProjectId is empty`() {
        assertEquals("", viewModel.currentProjectId.value)
    }

    // ── Project switching ─────────────────────────────────────────────────────
    @Test
    fun `switchProject changes currentProjectId`() = runTest {
        viewModel.switchProject("biz1")
        assertEquals("biz1", viewModel.currentProjectId.value)
    }

    @Test
    fun `switchProject to personal works`() = runTest {
        viewModel.switchProject("biz1")
        viewModel.switchProject("personal")
        assertEquals("personal", viewModel.currentProjectId.value)
    }

    @Test
    fun `createProject calls repository insert`() = runTest {
        val newProject = Project("test", "Test", "home", "#ff0000", "INR", "")
        viewModel.createProject(newProject)
        testScheduler.advanceUntilIdle()
        verify { repository.insertProject(newProject) }
    }

    @Test
    fun `deleteProject switches to personal if current project deleted`() = runTest {
        viewModel.switchProject("biz1")
        val bizProject = fakeProjects[1]
        viewModel.deleteProject(bizProject)
        testScheduler.advanceUntilIdle()
        assertEquals("personal", viewModel.currentProjectId.value)
    }

    @Test
    fun `deleteProject does not switch if different project deleted`() = runTest {
        viewModel.switchProject("biz1")
        val personalProject = fakeProjects[0]
        viewModel.deleteProject(personalProject)
        testScheduler.advanceUntilIdle()
        assertEquals("biz1", viewModel.currentProjectId.value)
    }

    // ── Search ────────────────────────────────────────────────────────────────
    @Test
    fun `initial searchQuery is empty`() {
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun `updateSearchQuery changes value`() {
        viewModel.updateSearchQuery("Suresh")
        assertEquals("Suresh", viewModel.searchQuery.value)
    }

    @Test
    fun `updateSearchQuery to empty clears search`() {
        viewModel.updateSearchQuery("something")
        viewModel.updateSearchQuery("")
        assertEquals("", viewModel.searchQuery.value)
    }
}
