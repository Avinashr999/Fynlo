package app.fynlo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.BudgetRepository
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Goal
import app.fynlo.data.remote.FirestoreRepository
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
class BudgetRepositoryTest {

    private lateinit var db: FynloDatabase
    private lateinit var repo: BudgetRepository
    private val firestore: FirestoreRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FynloDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = BudgetRepository(db.dao(), firestore)
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun `insert budget persists and is observable`() = runTest {
        val budget = Budget(id = UUID.randomUUID().toString(), category = "Food",
            limitAmount = 5_000.0, projectId = "proj1")
        repo.insertBudget(budget)
        val list = repo.allBudgets.first()
        assertTrue(list.any { it.category == "Food" })
    }

    @Test
    fun `delete budget removes from observable list`() = runTest {
        val budget = Budget(id = UUID.randomUUID().toString(), category = "Transport",
            limitAmount = 2_000.0, projectId = "proj1")
        repo.insertBudget(budget)
        repo.deleteBudget(budget)
        val list = repo.allBudgets.first()
        assertFalse(list.any { it.id == budget.id })
    }

    @Test
    fun `insert budget syncs to firestore`() = runTest {
        val budget = Budget(id = UUID.randomUUID().toString(), category = "Rent",
            limitAmount = 15_000.0, projectId = "proj1")
        repo.insertBudget(budget)
        coVerify { firestore.upsertBudget(budget) }
    }

    @Test
    fun `insert goal persists and is observable`() = runTest {
        val goal = Goal(id = UUID.randomUUID().toString(), name = "Emergency Fund",
            targetAmount = 100_000.0, currentAmount = 0.0, projectId = "proj1")
        repo.insertGoal(goal)
        val list = repo.allGoals.first()
        assertTrue(list.any { it.name == "Emergency Fund" })
    }

    @Test
    fun `goal progress can be updated`() = runTest {
        val goal = Goal(id = UUID.randomUUID().toString(), name = "Car Fund",
            targetAmount = 500_000.0, currentAmount = 0.0, projectId = "proj1")
        repo.insertGoal(goal)
        repo.updateGoal(goal.copy(currentAmount = 50_000.0))
        val updated = repo.allGoals.first().first { it.id == goal.id }
        assertEquals(50_000.0, updated.currentAmount, 0.01)
    }

    @Test
    fun `multiple budgets coexist correctly`() = runTest {
        val cats = listOf("Food","Rent","Fuel","Entertainment","Utilities")
        cats.forEach { cat ->
            repo.insertBudget(Budget(id = UUID.randomUUID().toString(),
                category = cat, limitAmount = 1_000.0, projectId = "proj1"))
        }
        val list = repo.allBudgets.first()
        assertEquals(cats.size, list.size)
        assertTrue(list.map { it.category }.containsAll(cats))
    }
}
