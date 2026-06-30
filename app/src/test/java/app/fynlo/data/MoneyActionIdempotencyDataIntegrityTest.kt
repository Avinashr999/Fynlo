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
import app.fynlo.logic.InterestEngine
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
    fun `debt funded investment journal traces are relinked to matching investment amounts`() = runBlocking {
        val debt = Debt(
            id = "debt-kalyani",
            name = "Kalyani Ammamma",
            amount = 325000.0,
            rate = 0.0,
            date = "2025-06-06",
        )
        val largeBza = Investment(
            id = "inv-bza-250",
            name = "BZA",
            type = "Business",
            invested = 250000.0,
            currentVal = 250000.0,
            date = "2025-06-06",
            fundingSource = debt.name,
            sourceType = "existing_debt",
            linkedDebtId = debt.id,
        )
        val smallBza = largeBza.copy(
            id = "inv-bza-75",
            invested = 75000.0,
            currentVal = 75000.0,
            date = "2025-07-11",
        )
        db.dao().insertDebt(debt)
        db.dao().insertInvestment(largeBza)
        db.dao().insertInvestment(smallBza)
        db.dao().insertTransaction(
            Transaction(
                id = "trace-250",
                date = "2025-06-06",
                type = "Info",
                amount = 250000.0,
                category = "Investment",
                desc = "Invested in BZA using Kalyani Ammamma loan funds",
                ref = "inv-bza-250",
                tags = "journal_only",
            )
        )
        db.dao().insertTransaction(
            Transaction(
                id = "trace-75-wrong-ref",
                date = "2025-07-11",
                type = "Info",
                amount = 75000.0,
                category = "Investment",
                desc = "Invested in BZA using Kalyani Ammamma loan funds",
                ref = "inv-bza-250",
                tags = "journal_only",
            )
        )

        assertEquals(1, repository.repairDebtFundedInvestmentJournalTraceRefs())
        assertEquals(0, repository.repairDebtFundedInvestmentJournalTraceRefs())

        val largeTraces = db.dao().getTransactionsByRef("inv-bza-250")
            .filter { it.category == "Investment" }
        val smallTraces = db.dao().getTransactionsByRef("inv-bza-75")
            .filter { it.category == "Investment" }
        assertEquals(1, largeTraces.size)
        assertEquals(250000.0, largeTraces.single().amount, 0.0001)
        assertEquals(1, smallTraces.size)
        assertEquals("trace-75-wrong-ref", smallTraces.single().id)
        assertEquals(75000.0, smallTraces.single().amount, 0.0001)
    }

    @Test
    fun `waiving borrower interest reduces receivable without moving cash or paid totals`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 10000.0))
        val borrower = Borrower(
            id = "loan-waive",
            name = "Grace Borrower",
            amount = 36500.0,
            rate = 10.0,
            date = "2025-06-29",
            due = "2026-06-29",
            intType = "Simple Interest",
            sourceAccount = "Personal Cash",
        )
        db.dao().insertBorrower(borrower)

        repository.waiveBorrowerInterest(borrower, 500.0, "Five day grace")

        val updated = db.dao().getBorrowerById("loan-waive")!!
        assertEquals(500.0, updated.interestWaived, 0.0001)
        assertEquals(0.0, updated.paid, 0.0001)
        assertEquals(0.0, updated.paidInterest, 0.0001)
        assertEquals(10000.0, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getAllAuditEventsOnce().count { it.action == "WAIVE_INTEREST" && it.entityId == "loan-waive" })
    }

    @Test
    fun `waiving debt interest reduces liability without creating payment rows`() = runBlocking {
        val debt = Debt(
            id = "debt-waive",
            name = "Grace Creditor",
            amount = 36500.0,
            rate = 10.0,
            date = "2025-06-29",
            due = "2026-06-29",
            intType = "Simple Interest",
        )
        db.dao().insertDebt(debt)

        repository.waiveDebtInterest(debt, 700.0, "Creditor grace")

        val updated = db.dao().getDebtById("debt-waive")!!
        assertEquals(700.0, updated.interestWaived, 0.0001)
        assertEquals(0.0, updated.paid, 0.0001)
        assertEquals(0, db.dao().getDebtPaymentsForDebtOnce("debt-waive").size)
        assertEquals(1, db.dao().getAllAuditEventsOnce().count { it.action == "WAIVE_INTEREST" && it.entityId == "debt-waive" })
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
    fun `account transfer debits source credits destination and preserves total cash`() = runBlocking {
        db.dao().insertAccount(Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 1000.0))
        db.dao().insertAccount(Account(id = "bank", name = "HDFC Bank", type = "Bank", balance = 500.0))
        val transfer = Transaction(
            id = "transfer-1",
            date = "2026-06-15",
            type = "Transfer",
            amount = 250.0,
            fromAcct = "Personal Cash",
            fromAcctId = "cash",
            toAcct = "HDFC Bank",
            toAcctId = "bank",
            category = "Transfer",
            desc = "Move cash to bank",
        )

        repository.insertTransaction(transfer)

        val cash = db.dao().getAccountById("cash")!!
        val bank = db.dao().getAccountById("bank")!!
        val saved = db.dao().getTransactionById("transfer-1")!!
        assertEquals(750.0, cash.balance, 0.0001)
        assertEquals(750.0, bank.balance, 0.0001)
        assertEquals(1500.0, cash.balance + bank.balance, 0.0001)
        assertEquals(Categories.ACCOUNT_TRANSFER, saved.category)
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
    fun `repairing legacy transaction account ids does not move account balances`() = runBlocking {
        db.dao().insertAccount(Account(id = "family", name = "Family Cash", type = "Cash", balance = 164500.0))
        db.dao().insertAccount(Account(id = "business", name = "Business Investment", type = "Cash", balance = 2500000.0))
        db.dao().insertTransaction(
            Transaction(
                id = "legacy-family-loan",
                date = "2026-06-20",
                type = "Expense",
                amount = 200000.0,
                fromAcct = "Family Cash",
                fromAcctId = "",
                category = "Lending",
                desc = "Lent to Ashwini",
            )
        )
        db.dao().insertTransaction(
            Transaction(
                id = "legacy-business-receipt",
                date = "2026-06-20",
                type = "Income",
                amount = 675000.0,
                toAcct = "Business Investment",
                toAcctId = "",
                category = "Debt Received",
                desc = "Debt received",
            )
        )

        assertEquals(2, repository.repairTransactionAccountIds())
        assertEquals(0, repository.repairTransactionAccountIds())

        val familyTxn = db.dao().getTransactionById("legacy-family-loan")!!
        val businessTxn = db.dao().getTransactionById("legacy-business-receipt")!!
        assertEquals("family", familyTxn.fromAcctId)
        assertEquals("business", businessTxn.toAcctId)
        assertEquals(164500.0, db.dao().getAccountById("family")!!.balance, 0.0001)
        assertEquals(2500000.0, db.dao().getAccountById("business")!!.balance, 0.0001)
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
    fun `editing investment from account funded to debt funded reverses account and writes journal trace`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-family", name = "Family Cash", type = "Cash", balance = 1000.0))
        val debt = Debt(id = "debt-1", name = "Kalyani", amount = 675.0, rate = 0.0, date = "2026-06-15")
        val investment = Investment(
            id = "inv-1",
            name = "BZA",
            type = "Business",
            invested = 100.0,
            currentVal = 100.0,
            date = "2026-06-15",
        )
        db.dao().insertDebt(debt)

        repository.insertInvestmentFundedByAccount(investment, "Family Cash", "personal", "acc-family")
        repository.updateInvestmentFundedByExistingDebt(
            investment.copy(invested = 675.0, currentVal = 675.0),
            debt,
            "personal",
        )

        val updatedInvestment = db.dao().getInvestmentById("inv-1")!!
        val trace = db.dao().getTransactionsByRef("inv-1").single { it.category == "Investment" }
        assertEquals(1000.0, db.dao().getAccountById("acc-family")!!.balance, 0.0001)
        assertEquals("existing_debt", updatedInvestment.sourceType)
        assertEquals("debt-1", updatedInvestment.linkedDebtId)
        assertEquals("Info", trace.type)
        assertEquals(675.0, trace.amount, 0.0001)
        assertEquals("", trace.fromAcct)
        assertEquals("journal_only", trace.tags)
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
    fun `full interest only loan payment rolls interest start to next day`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        db.dao().insertBorrower(Borrower(id = "loan-1", name = "Ravi", amount = 100000.0, rate = 12.0, date = "2026-01-01"))
        val interestDue = InterestEngine.calcIntAccrued(100000.0, 12.0, "2026-01-01", "Simple Interest", asOf = "2026-02-01")
        val payment = Payment(
            id = "pay-interest",
            loanId = "loan-1",
            name = "Ravi",
            date = "2026-02-01",
            type = "Interest Only",
            amount = interestDue,
            interest = interestDue,
        )

        repository.insertPaymentWithDest(payment, "Personal Cash", "personal")

        val updated = db.dao().getBorrowerById("loan-1")!!
        assertEquals("2026-02-02", updated.date)
        assertEquals(0.0, updated.paidInterest, 0.0001)
        assertEquals(0.0, updated.paidPrincipal, 0.0001)
        assertEquals(1000.0 + interestDue, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getPaymentsForLoanOnce("loan-1").size)
    }

    @Test
    fun `partial interest only loan payment does not roll interest start`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 1000.0))
        db.dao().insertBorrower(Borrower(id = "loan-1", name = "Ravi", amount = 100000.0, rate = 12.0, date = "2026-01-01"))
        val interestDue = InterestEngine.calcIntAccrued(100000.0, 12.0, "2026-01-01", "Simple Interest", asOf = "2026-02-01")
        val partial = interestDue / 2.0
        val payment = Payment(
            id = "pay-partial",
            loanId = "loan-1",
            name = "Ravi",
            date = "2026-02-01",
            type = "Interest Only",
            amount = partial,
            interest = partial,
        )

        repository.insertPaymentWithDest(payment, "Personal Cash", "personal")

        val updated = db.dao().getBorrowerById("loan-1")!!
        assertEquals("2026-01-01", updated.date)
        assertEquals(partial, updated.paidInterest, 0.0001)
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
    fun `full interest only debt payment rolls interest start to next day`() = runBlocking {
        db.dao().insertAccount(Account(id = "acc-1", name = "Personal Cash", type = "Cash", balance = 5000.0))
        db.dao().insertDebt(Debt(id = "debt-1", name = "Lender", amount = 100000.0, rate = 12.0, date = "2026-01-01"))
        val interestDue = InterestEngine.calcIntAccrued(100000.0, 12.0, "2026-01-01", "Simple Interest", asOf = "2026-02-01")
        val payment = DebtPayment(
            id = "debt-pay-interest",
            debtId = "debt-1",
            name = "Lender",
            date = "2026-02-01",
            type = "Interest Only",
            amount = interestDue,
            interest = interestDue,
        )

        repository.insertDebtPaymentWithSource(payment, "Personal Cash", "personal")

        val updated = db.dao().getDebtById("debt-1")!!
        assertEquals("2026-02-02", updated.date)
        assertEquals(0.0, updated.paidInterest, 0.0001)
        assertEquals(0.0, updated.paidPrincipal, 0.0001)
        assertEquals(5000.0 - interestDue, db.dao().getAccountById("acc-1")!!.balance, 0.0001)
        assertEquals(1, db.dao().getDebtPaymentsForDebtOnce("debt-1").size)
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
