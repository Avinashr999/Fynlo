package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Account
import app.fynlo.data.model.AuditEvent
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Payment
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
    fun `funding investment from existing debt creates journal trace without moving accounts`() = runBlocking {
        db.dao().insertAccount(Account(id = "business", name = "Business Investment", type = "Bank", balance = 1725000.0))
        db.dao().insertAccount(Account(id = "family", name = "Family Cash", type = "Cash", balance = 2990000.0))
        val debt = Debt(
            id = "debt-1",
            name = "Sudhakar Sabbella",
            amount = 200000.0,
            rate = 0.0,
            date = "2025-11-15",
        )
        val investment = Investment(
            id = "inv-1",
            name = "BBS",
            type = "Business",
            invested = 200000.0,
            currentVal = 200000.0,
            date = "2025-10-30",
        )
        db.dao().insertDebt(debt)

        repository.insertInvestmentFundedByExistingDebt(investment, debt)

        val trace = db.dao().getTransactionsByRef("inv-1").single()
        assertEquals("Info", trace.type)
        assertEquals("journal_only", trace.tags)
        assertEquals("", trace.fromAcct)
        assertEquals("", trace.toAcct)
        assertEquals(1725000.0, db.dao().getAccountById("business")!!.balance, 0.0001)
        assertEquals(2990000.0, db.dao().getAccountById("family")!!.balance, 0.0001)
    }

    @Test
    fun `legacy debt funded investment transfer is neutralized once`() = runBlocking {
        db.dao().insertAccount(Account(id = "business", name = "Business Investment", type = "Bank", balance = 1725000.0))
        db.dao().insertAccount(Account(id = "family", name = "Family Cash", type = "Cash", balance = 2990000.0))
        val debt = Debt(
            id = "debt-1",
            name = "Sudhakar Sabbella",
            amount = 200000.0,
            rate = 0.0,
            date = "2025-11-15",
        )
        val investment = Investment(
            id = "inv-1",
            name = "BBS",
            type = "Business",
            invested = 200000.0,
            currentVal = 200000.0,
            date = "2025-10-30",
            fundingSource = debt.name,
            sourceType = "existing_debt",
            linkedDebtId = debt.id,
        )
        db.dao().insertDebt(debt)
        db.dao().insertInvestment(investment)
        db.dao().insertTransaction(
            Transaction(
                id = "bad-trace",
                date = "2025-10-30",
                type = "Transfer",
                amount = 200000.0,
                fromAcct = "Business Investment",
                fromAcctId = "business",
                toAcct = "Family Cash",
                toAcctId = "family",
                category = "Investment",
                desc = "Invested in BBS using Sudhakar Sabbella loan funds",
            )
        )

        assertEquals(1, repository.repairDebtFundedInvestmentTransferTraces())
        assertEquals(0, repository.repairDebtFundedInvestmentTransferTraces())

        val repaired = db.dao().getTransactionById("bad-trace")!!
        assertEquals("Info", repaired.type)
        assertEquals("inv-1", repaired.ref)
        assertEquals("journal_only", repaired.tags)
        assertEquals("", repaired.fromAcct)
        assertEquals("", repaired.toAcct)
        assertEquals(1925000.0, db.dao().getAccountById("business")!!.balance, 0.0001)
        assertEquals(2790000.0, db.dao().getAccountById("family")!!.balance, 0.0001)
    }

    @Test
    fun `retrying same expense transaction does not debit account twice`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
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

        repeat(2) { repository.insertTransaction(transaction) }

        assertEquals(900.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(transaction.id, db.dao().getTransactionById("txn-1")!!.id)
    }

    @Test
    fun `retrying same borrower disbursal does not debit source account twice`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        val borrower = Borrower(
            id = "loan-1",
            name = "Ravi",
            amount = 100.0,
            rate = 0.0,
            date = "2026-06-15",
        )

        repeat(2) { repository.insertBorrowerWithSource(borrower, "Personal Cash", "personal") }

        assertEquals(900.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getTransactionsByRef("loan-1").count { it.category == "Lending" })
    }

    @Test
    fun `retrying same debt received does not credit destination account twice`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        val debt = Debt(
            id = "debt-1",
            name = "Lender",
            amount = 100.0,
            rate = 0.0,
            date = "2026-06-15",
        )

        repeat(2) { repository.insertDebtWithDestination(debt, "Personal Cash", "personal") }

        assertEquals(1100.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getTransactionsByRef("debt-1").count { it.category == "Debt Received" })
    }

    @Test
    fun `editing debt amount applies only principal delta to original destination account`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Business Investment", type = "Bank", balance = 1000.0))
        val debt = Debt(
            id = "debt-1",
            name = "Lender",
            amount = 100.0,
            rate = 0.0,
            date = "2026-06-15",
        )

        repository.insertDebtWithDestination(debt, "Business Investment", "personal")
        repository.updateDebt(debt.copy(amount = 675.0))

        val fundingTransaction = db.dao().getTransactionsByRef("debt-1")
            .single { it.category == "Debt Received" }
        assertEquals(1675.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(675.0, fundingTransaction.amount, 0.0001)
        assertEquals("acc-1", fundingTransaction.toAcctId)
    }

    @Test
    fun `repairing debt receipt mismatch applies only missing principal delta once`() = runBlocking {
        db.dao().insertAccount(Account(id = "business", name = "Business Investment", type = "Cash", balance = 100000.0))
        db.dao().insertDebt(
            Debt(
                id = "debt-kalyani",
                name = "Kalyani Mohamad towards KK",
                amount = 675000.0,
                rate = 0.0,
                date = "2025-10-01",
            )
        )
        db.dao().insertTransaction(
            Transaction(
                id = "receipt-kalyani",
                date = "2025-10-01",
                type = "Income",
                amount = 100000.0,
                toAcct = "Business Investment",
                toAcctId = "business",
                category = "Debt Received",
                desc = "Debt received from Kalyani Mohamad towards KK",
                ref = "debt-kalyani",
            )
        )

        assertEquals(1, repository.repairDebtReceiptAmountMismatches())
        assertEquals(0, repository.repairDebtReceiptAmountMismatches())

        val repairedReceipt = db.dao().getTransactionById("receipt-kalyani")!!
        assertEquals(675000.0, repairedReceipt.amount, 0.0001)
        assertEquals(675000.0, db.dao().getAccountById("business")!!.balance, 0.0001)
    }

    @Test
    fun `repairing stored account drift restores balance from opening audit and ledger`() = runBlocking {
        db.dao().insertAccount(Account(id = "family", name = "Family Cash", type = "Cash", balance = 2990000.0))
        db.dao().insertAuditEvent(
            AuditEvent(
                id = "audit-family-create",
                timestamp = 1L,
                action = "CREATE",
                entityType = "account",
                entityId = "family",
                title = "Account created: Family Cash",
                amountDelta = 11615000.0,
                accountName = "Family Cash",
            )
        )
        db.dao().insertTransaction(
            Transaction(
                id = "family-expense-ledger",
                date = "2026-06-15",
                type = "Expense",
                amount = 8825000.0,
                fromAcct = "Family Cash",
                fromAcctId = "family",
                category = "Ledger correction test",
                desc = "Family Cash outgoing ledger total",
            )
        )

        assertEquals(1, repository.repairAccountBalanceDriftFromLedger())
        assertEquals(0, repository.repairAccountBalanceDriftFromLedger())

        assertEquals(2790000.0, db.dao().getAccountById("family")!!.balance, 0.0001)
    }

    @Test
    fun `editing borrower source reverses old account and debits corrected account`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-family", name = "Family Cash", type = "Cash", balance = 1000.0))
        db.dao().insertAccount(Account(id = "acc-business", name = "Business Investment", type = "Bank", balance = 2000.0))
        val borrower = Borrower(
            id = "loan-1",
            name = "Sudhakar",
            amount = 100.0,
            rate = 0.0,
            date = "2026-06-15",
        )

        repository.insertBorrowerWithSource(borrower, "Family Cash", "personal")
        repository.updateBorrowerWithSource(
            borrower.copy(amount = 675.0, sourceAccount = "Business Investment"),
            "Business Investment",
        )

        val fundingTransaction = db.dao().getTransactionsByRef("loan-1")
            .single { it.category == "Lending" }
        assertEquals(1000.0, db.dao().getAccountById("acc-family")!!.balance, 0.0001)
        assertEquals(1325.0, db.dao().getAccountById("acc-business")!!.balance, 0.0001)
        assertEquals(675.0, fundingTransaction.amount, 0.0001)
        assertEquals("Business Investment", fundingTransaction.fromAcct)
        assertEquals("acc-business", fundingTransaction.fromAcctId)
    }

    @Test
    fun `editing debt destination reverses old account and credits corrected account`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-family", name = "Family Cash", type = "Cash", balance = 1000.0))
        db.dao().insertAccount(Account(id = "acc-business", name = "Business Investment", type = "Bank", balance = 2000.0))
        val debt = Debt(
            id = "debt-1",
            name = "Sudhakar",
            amount = 100.0,
            rate = 0.0,
            date = "2026-06-15",
        )

        repository.insertDebtWithDestination(debt, "Family Cash", "personal")
        repository.updateDebtWithDestination(debt.copy(amount = 675.0), "Business Investment")

        val fundingTransaction = db.dao().getTransactionsByRef("debt-1")
            .single { it.category == "Debt Received" }
        assertEquals(1000.0, db.dao().getAccountById("acc-family")!!.balance, 0.0001)
        assertEquals(2675.0, db.dao().getAccountById("acc-business")!!.balance, 0.0001)
        assertEquals(675.0, fundingTransaction.amount, 0.0001)
        assertEquals("Business Investment", fundingTransaction.toAcct)
        assertEquals("acc-business", fundingTransaction.toAcctId)
    }

    @Test
    fun `editing account funded investment source reverses old account and debits corrected account`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-family", name = "Family Cash", type = "Cash", balance = 1000.0))
        db.dao().insertAccount(Account(id = "acc-business", name = "Business Investment", type = "Bank", balance = 2000.0))
        val investment = Investment(
            id = "inv-1",
            name = "Gold ETF",
            type = "Gold",
            invested = 100.0,
            currentVal = 125.0,
            date = "2026-06-15",
        )

        repository.insertInvestmentFundedByAccount(investment, "Family Cash", "personal", "acc-family")
        repository.updateInvestmentFundedByAccount(
            investment.copy(invested = 675.0, currentVal = 700.0),
            "Business Investment",
            "personal",
            "acc-business",
        )

        val fundingTransaction = db.dao().getTransactionsByRef("inv-1")
            .single { it.category == "Investment" }
        assertEquals(1000.0, db.dao().getAccountById("acc-family")!!.balance, 0.0001)
        assertEquals(1325.0, db.dao().getAccountById("acc-business")!!.balance, 0.0001)
        assertEquals(675.0, fundingTransaction.amount, 0.0001)
        assertEquals("Business Investment", fundingTransaction.fromAcct)
        assertEquals("acc-business", fundingTransaction.fromAcctId)
    }

    @Test
    fun `retrying same loan repayment does not credit destination account twice`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        db.dao().insertBorrower(Borrower(id = "loan-1", name = "Ravi", amount = 100.0, rate = 0.0, date = "2026-06-15"))
        val payment = Payment(
            id = "pay-1",
            loanId = "loan-1",
            name = "Ravi",
            date = "2026-06-15",
            type = "Both",
            amount = 100.0,
            principal = 100.0,
        )

        repeat(2) { repository.insertPaymentWithDest(payment, "Personal Cash", "personal") }

        assertEquals(1100.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getPaymentsForLoanOnce("loan-1").size)
        assertEquals(1, db.dao().getTransactionsByRef("loan-1").count { it.category == "Loan Repayment" })
    }

    @Test
    fun `retrying same debt repayment does not debit source account twice`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        db.dao().insertDebt(Debt(id = "debt-1", name = "Lender", amount = 100.0, rate = 0.0, date = "2026-06-15"))
        val payment = DebtPayment(
            id = "debt-pay-1",
            debtId = "debt-1",
            name = "Lender",
            date = "2026-06-15",
            type = "Both",
            amount = 100.0,
            principal = 100.0,
        )

        repeat(2) { repository.insertDebtPaymentWithSource(payment, "Personal Cash", "personal") }

        assertEquals(900.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getDebtPaymentsForDebtOnce("debt-1").size)
        assertEquals(1, db.dao().getTransactionsByRef("debt-1").count { it.category == "Debt Repayment" })
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
