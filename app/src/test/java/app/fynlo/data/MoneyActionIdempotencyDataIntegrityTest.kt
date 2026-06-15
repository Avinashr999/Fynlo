package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Account
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Transaction
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MoneyActionIdempotencyDataIntegrityTest {

    private lateinit var db: FynloDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runCatching { FirebaseApp.getInstance() }
            .getOrElse { FirebaseApp.initializeApp(ctx) }
        db = Room.inMemoryDatabaseBuilder(ctx, FynloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(
            dao = db.dao(),
            db = db,
            firestore = FirestoreRepository(""),
            syncManager = SyncManager("", db.dao()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `deleting the same expense transaction twice reverses account balance only once`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 900.0))
        val transaction = Transaction(
            id = "txn-1",
            date = "2026-06-15",
            type = "Expense",
            amount = 100.0,
            fromAcct = "Personal Cash",
            fromAcctId = "acc-1",
            category = "Food",
            desc = "Lunch",
        )
        db.dao().insertTransaction(transaction)

        repository.deleteTransaction(transaction)
        repository.deleteTransaction(transaction)

        assertEquals(1000.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertNull(db.dao().getTransactionById("txn-1"))
    }

    @Test
    fun `funding investment from account deducts selected account by id`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        val investment = Investment(
            id = "inv-1",
            name = "Test Fund",
            type = "Mutual Funds",
            invested = 100.0,
            currentVal = 100.0,
            date = "2026-06-15",
        )

        repository.insertInvestmentFundedByAccount(
            investment = investment,
            accountName = "Personal Cash",
            projectId = "personal",
            accountId = "acc-1",
        )

        val fundingTransaction = db.dao().getTransactionsByRef("inv-1").single()
        assertEquals(900.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals("acc-1", fundingTransaction.fromAcctId)
    }

    @Test
    fun `retrying same account funded investment does not create duplicate funding transactions`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        val investment = Investment(
            id = "inv-1",
            name = "Test Fund",
            type = "Mutual Funds",
            invested = 100.0,
            currentVal = 100.0,
            date = "2026-06-15",
        )

        repeat(2) {
            repository.insertInvestmentFundedByAccount(
                investment = investment,
                accountName = "Personal Cash",
                projectId = "personal",
                accountId = "acc-1",
            )
        }

        assertEquals(900.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getTransactionsByRef("inv-1").count { it.category == "Investment" })
    }

    @Test
    fun `investment add then delete restore leaves account and assets neutral`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        val investment = Investment(
            id = "inv-1",
            name = "Test Fund",
            type = "Mutual Funds",
            invested = 100.0,
            currentVal = 100.0,
            date = "2026-06-15",
        )

        repository.insertInvestmentFundedByAccount(
            investment = investment,
            accountName = "Personal Cash",
            projectId = "personal",
            accountId = "acc-1",
        )
        repository.deleteInvestmentAndReverseAccount(investment)

        assertEquals(1000.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(emptyList<Investment>(), db.dao().getAllInvestments().first())
    }

    @Test
    fun `deleting the same investment with restore twice restores cash only once`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 900.0))
        val investment = Investment(
            id = "inv-1",
            name = "Test Fund",
            type = "Mutual Funds",
            invested = 100.0,
            currentVal = 100.0,
            date = "2026-06-15",
            fundingSource = "Personal Cash",
            sourceType = "account",
        )
        db.dao().insertInvestment(investment)
        db.dao().insertTransaction(
            Transaction(
                id = "txn-1",
                date = "2026-06-15",
                type = "Expense",
                amount = 100.0,
                fromAcct = "Personal Cash",
                fromAcctId = "acc-1",
                category = "Investment",
                desc = "Invested in Test Fund",
                ref = "inv-1",
            )
        )

        repository.deleteInvestmentAndReverseAccount(investment)
        repository.deleteInvestmentAndReverseAccount(investment)

        assertEquals(1000.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertNull(db.dao().getInvestmentById("inv-1"))
        assertNull(db.dao().getTransactionById("txn-1"))
        assertEquals(true, db.dao().isRemoteDocDeleted("investments", "inv-1"))
        assertEquals(true, db.dao().isRemoteDocDeleted("transactions", "txn-1"))
    }

    @Test
    fun `stale investment delete does not restore cash when original funding transaction is already gone`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        val staleInvestment = Investment(
            id = "inv-1",
            name = "Test Fund",
            type = "Mutual Funds",
            invested = 100.0,
            currentVal = 100.0,
            date = "2026-06-15",
            fundingSource = "Personal Cash",
            sourceType = "account",
        )
        db.dao().insertInvestment(staleInvestment)

        repository.deleteInvestmentAndReverseAccount(staleInvestment)

        assertEquals(1000.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertNull(db.dao().getInvestmentById("inv-1"))
    }
}
