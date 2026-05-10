package app.fynlo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Goal
import app.fynlo.data.model.Investment
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Person
import app.fynlo.data.model.Project
import app.fynlo.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FynloDao {

    // ─── Borrowers ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM borrowers")
    fun getAllBorrowers(): Flow<List<Borrower>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrower(borrower: Borrower)

    @Query("UPDATE borrowers SET paid = paid + :amount WHERE id = :borrowerId")
    suspend fun updateBorrowerPaidAmount(borrowerId: String, amount: Double)

    @Query("UPDATE borrowers SET paidPrincipal = paidPrincipal + :amount, paid = paid + :amount WHERE id = :borrowerId")
    suspend fun updateBorrowerPaidPrincipal(borrowerId: String, amount: Double)

    @Query("UPDATE borrowers SET paidInterest = paidInterest + :amount, paid = paid + :amount WHERE id = :borrowerId")
    suspend fun updateBorrowerPaidInterest(borrowerId: String, amount: Double)

    @Query("UPDATE borrowers SET paidPrincipal = paid WHERE paidPrincipal = 0 AND paidInterest = 0 AND paid > 0")
    suspend fun seedPaidPrincipalFromPaid()

    @Query("UPDATE debts SET paidPrincipal = paid WHERE paidPrincipal = 0 AND paidInterest = 0 AND paid > 0")
    suspend fun seedDebtPaidPrincipalFromPaid()

    @Query("UPDATE borrowers SET paid = paidPrincipal + paidInterest")
    suspend fun recalculateBorrowerPaid()

    @Query("UPDATE debts SET paid = paidPrincipal + paidInterest")
    suspend fun recalculateDebtPaid()

    @Query("UPDATE borrowers SET status = :status, defaultDate = :defaultDate, frozenInterest = :frozenInterest WHERE id = :id")
    suspend fun updateBorrowerDefaultStatus(id: String, status: String, defaultDate: String, frozenInterest: Double)

    @Query("SELECT * FROM borrowers WHERE id = :id LIMIT 1")
    suspend fun getBorrowerById(id: String): Borrower?

    @Delete
    suspend fun deleteBorrower(borrower: Borrower)

    @Query("DELETE FROM borrowers WHERE id = :id")
    suspend fun deleteBorrowerById(id: String)

    // ─── Payments ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM payments WHERE loanId = :loanId")
    fun getPaymentsForLoan(loanId: String): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    // ─── Debt Payments ────────────────────────────────────────────────────────

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId")
    fun getDebtPayments(debtId: String): Flow<List<DebtPayment>>

    @Query("SELECT * FROM debt_payments ORDER BY date DESC")
    fun getAllDebtPayments(): Flow<List<DebtPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPayment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Delete
    suspend fun deleteDebtPayment(payment: DebtPayment)

    @Query("SELECT * FROM payments WHERE loanId = :loanId")
    suspend fun getPaymentsForLoanOnce(loanId: String): List<Payment>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId")
    suspend fun getDebtPaymentsForDebtOnce(debtId: String): List<DebtPayment>

    @Query("SELECT * FROM transactions WHERE ref = :ref")
    suspend fun getTransactionsByRef(ref: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE desc = :desc")
    suspend fun getTransactionsByDesc(desc: String): List<Transaction>

    // ─── Accounts ─────────────────────────────────────────────────────────────

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): Account?

    @Query("UPDATE accounts SET balance = balance + :amount WHERE name = :name")
    suspend fun updateAccountBalance(name: String, amount: Double)

    // ─── Transactions ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsList(): List<Transaction>

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsList(): List<Account>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    // ─── Investments ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM investments")
    fun getAllInvestments(): Flow<List<Investment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: Investment)

    @Delete
    suspend fun deleteInvestment(investment: Investment)

    // ─── Debts ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM debts")
    fun getAllDebts(): Flow<List<Debt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt)

    @Query("UPDATE debts SET paid = paid + :amount WHERE id = :debtId")
    suspend fun updateDebtPaidAmount(debtId: String, amount: Double)

    @Query("UPDATE debts SET paidPrincipal = paidPrincipal + :amount, paid = paid + :amount WHERE id = :debtId")
    suspend fun updateDebtPaidPrincipal(debtId: String, amount: Double)

    @Query("UPDATE debts SET paidInterest = paidInterest + :amount, paid = paid + :amount WHERE id = :debtId")
    suspend fun updateDebtPaidInterest(debtId: String, amount: Double)

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: String): Debt?

    @Delete
    suspend fun deleteDebt(debt: Debt)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtById(id: String)

    // ─── People ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeople(): Flow<List<Person>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    // ─── Budgets ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<Budget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    // ─── Goals ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM goals")
    fun getAllGoals(): Flow<List<Goal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    // ─── Projects (new in v2.0) ───────────────────────────────────────────────

    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    fun getAllProjects(): Flow<List<Project>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): Project?

    // ─── Bulk delete (restore / wipe) ─────────────────────────────────────────

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM borrowers")
    suspend fun deleteAllBorrowers()

    @Query("DELETE FROM investments")
    suspend fun deleteAllInvestments()

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()

    @Query("DELETE FROM people")
    suspend fun deleteAllPeople()

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()
    // ─── Legacy projectId normalization ───────────────────────────────────────
    // Fixes records created before v2.0 (empty projectId) or with the
    // placeholder "personal" string — updates them to the real project UUID.

    @Query("UPDATE accounts      SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeAccountProjectIds(pid: String)

    @Query("UPDATE transactions  SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeTransactionProjectIds(pid: String)

    @Query("UPDATE borrowers     SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeBorrowerProjectIds(pid: String)

    @Query("UPDATE investments   SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeInvestmentProjectIds(pid: String)

    @Query("UPDATE debts         SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeDebtProjectIds(pid: String)

    @Query("UPDATE people        SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizePeopleProjectIds(pid: String)

    @Query("UPDATE payments      SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizePaymentProjectIds(pid: String)

    @Query("UPDATE debt_payments SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeDebtPaymentProjectIds(pid: String)

    @Query("UPDATE budgets       SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeBudgetProjectIds(pid: String)

    @Query("UPDATE goals         SET projectId = :pid WHERE projectId = '' OR projectId = 'personal'")
    suspend fun normalizeGoalProjectIds(pid: String)

    // Recurring transactions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction)

    @Delete
    suspend fun deleteRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction)

    @Query("SELECT * FROM recurring_transactions ORDER BY name ASC")
    fun getAllRecurringTransactions(): kotlinx.coroutines.flow.Flow<List<app.fynlo.data.model.RecurringTransaction>>

    @Query("UPDATE recurring_transactions SET lastRun = :date WHERE id = :id")
    suspend fun updateRecurringLastRun(id: String, date: String)

    // ─── Net Worth Snapshots ──────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetWorthSnapshot(s: app.fynlo.data.model.NetWorthSnapshot)

    @Query("SELECT * FROM net_worth_snapshots WHERE projectId = :pid ORDER BY date DESC LIMIT :limit")
    fun getNetWorthSnapshots(pid: String, limit: Int = 90): kotlinx.coroutines.flow.Flow<List<app.fynlo.data.model.NetWorthSnapshot>>

    // ─── Investment Valuations ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValuation(v: app.fynlo.data.model.InvestmentValuation)

    @Query("SELECT * FROM investment_valuations WHERE investmentId = :invId ORDER BY date DESC")
    fun getValuationsForInvestment(invId: String): kotlinx.coroutines.flow.Flow<List<app.fynlo.data.model.InvestmentValuation>>

    @Query("DELETE FROM investment_valuations WHERE investmentId = :invId")
    suspend fun deleteValuationsForInvestment(invId: String)
}
