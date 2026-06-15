package app.fynlo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.DeletedRemoteDoc
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

    // C01 Sprint 1 Stage 2 (decisions/2026-05-26-c01-fix-strategy.md):
    // updateBorrowerPaid{Amount,Principal,Interest} were the direct
    // paid-mutating writers that broke the invariant paid == SUM(payments).
    // The `payments` table is now the single source of truth — callers
    // insertPayment / deletePayment and then rebuildBorrowerPaidFromPayments().
    //
    // seedPaidPrincipalFromPaid and seedDebtPaidPrincipalFromPaid were
    // unused (their logic lives inline in MIGRATION_11_12 / 12_13 / 15_16).
    // Removed as dead code in the same pass.

    @Query("""UPDATE borrowers SET sourceAccount = COALESCE(
        (SELECT fromAcct FROM transactions
         WHERE ref = borrowers.id AND type = 'Expense'
         AND category IN ('Lending', 'Loan', 'Transfer')
         AND fromAcct != ''
         ORDER BY updatedAt ASC LIMIT 1), 
        (SELECT fromAcct FROM transactions
         WHERE ref = borrowers.id AND type = 'Expense'
         AND fromAcct != ''
         ORDER BY updatedAt ASC LIMIT 1),
        sourceAccount)
        WHERE sourceAccount = '' """)
    suspend fun backfillBorrowerSourceAccount()

    // recalculateBorrowerPaid removed by C01 Sprint 1 Stage 2 — its SQL
    // (UPDATE borrowers SET paid = paidPrincipal + paidInterest) is the
    // destructive query the entire C01 ADR is about. Use
    // rebuildBorrowerPaidFromPayments() instead.

    // C01 fix (decisions/2026-05-26-c01-fix-strategy.md): drop the WHERE EXISTS
    // gate (which kept legacy `paid > 0 && no payments` rows un-rebuilt and
    // therefore destroyed by the now-removed recalculateBorrowerPaid query),
    // and wrap each sum in COALESCE(...,0) so brand-new borrowers with zero
    // payments correctly get paid = 0. After the v15→v16 backfill migration,
    // every borrower with `paid > 0` has at least one payment row, so SUM is
    // never NULL for those.
    @Query("""UPDATE borrowers SET
        paid          = COALESCE((SELECT SUM(amount) FROM payments WHERE loanId = borrowers.id), 0),
        paidPrincipal = COALESCE((SELECT SUM(CASE WHEN type='Interest Only' THEN 0 WHEN principal > 0 THEN principal ELSE amount END) FROM payments WHERE loanId = borrowers.id), 0),
        paidInterest  = COALESCE((SELECT SUM(CASE WHEN type='Interest Only' AND interest=0 THEN amount ELSE interest END) FROM payments WHERE loanId = borrowers.id), 0)""")
    suspend fun rebuildBorrowerPaidFromPayments()

    @Query("""UPDATE debts SET
        paid          = COALESCE((SELECT SUM(amount) FROM debt_payments WHERE debtId = debts.id), 0),
        paidPrincipal = COALESCE((SELECT SUM(CASE WHEN type='Interest Only' THEN 0 WHEN principal > 0 THEN principal ELSE amount END) FROM debt_payments WHERE debtId = debts.id), 0),
        paidInterest  = COALESCE((SELECT SUM(CASE WHEN type='Interest Only' AND interest=0 THEN amount ELSE interest END) FROM debt_payments WHERE debtId = debts.id), 0)""")
    suspend fun rebuildDebtPaidFromDebtPayments()

    // recalculateDebtPaid removed by C01 Sprint 1 Stage 2 (twin of
    // recalculateBorrowerPaid). Use rebuildDebtPaidFromDebtPayments().

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

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): Transaction?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): Account?

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): Account?

    @Query("UPDATE accounts SET balance = balance + :amount WHERE name = :name")
    suspend fun updateAccountBalance(name: String, amount: Double)

    /**
     * C03b Stage #1b-1 (3.2.87) — id-keyed balance mutation. The
     * authoritative path for new code: an account rename can't orphan
     * the update because the id never changes. Callers fall back to the
     * name-based variant only for legacy rows whose `fromAcctId` /
     * `toAcctId` is still empty (orphan from before Stage #1a's resolver
     * could populate it).
     */
    @Query("UPDATE accounts SET balance = balance + :amount WHERE id = :id")
    suspend fun updateAccountBalanceById(id: String, amount: Double)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedRemoteDoc(doc: DeletedRemoteDoc)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_remote_docs WHERE collection = :collection AND id = :id)")
    suspend fun isRemoteDocDeleted(collection: String, id: String): Boolean

    // ─── Investments ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM investments")
    fun getAllInvestments(): Flow<List<Investment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: Investment)

    @Query("SELECT * FROM investments WHERE id = :id LIMIT 1")
    suspend fun getInvestmentById(id: String): Investment?

    @Delete
    suspend fun deleteInvestment(investment: Investment)

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun deleteInvestmentById(id: String)

    // ─── Debts ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM debts")
    fun getAllDebts(): Flow<List<Debt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt)

    // updateDebtPaid{Amount,Principal,Interest} removed by C01 Sprint 1
    // Stage 2 (debts twin of the borrower removals above).

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getDebtById(id: String): Debt?

    @Delete
    suspend fun deleteDebt(debt: Debt)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtById(id: String)

    // ─── People ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeople(): Flow<List<Person>>

    /**
     * C03b Stage #3 (3.2.90) — lookup-by-phone for the
     * `findOrCreatePersonId` resolver in FinanceRepository. Returns
     * the first match (phone is the dedup key); empty phone always
     * returns null so empty-phone borrowers don't accidentally link
     * to each other.
     */
    @Query("SELECT * FROM people WHERE phone = :phone AND phone != '' LIMIT 1")
    suspend fun getPersonByPhone(phone: String): Person?

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

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM debt_payments")
    suspend fun deleteAllDebtPayments()

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

    @Update
    suspend fun updateRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction)

    @Query("SELECT * FROM recurring_transactions ORDER BY name ASC")
    suspend fun getAllRecurringTransactionsOnce(): List<app.fynlo.data.model.RecurringTransaction>

    // ─── Net Worth Snapshots ──────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetWorthSnapshot(s: app.fynlo.data.model.NetWorthSnapshot)

    @Query("SELECT * FROM net_worth_snapshots WHERE projectId = :pid ORDER BY date DESC LIMIT :limit")
    fun getNetWorthSnapshots(pid: String, limit: Int = 90): kotlinx.coroutines.flow.Flow<List<app.fynlo.data.model.NetWorthSnapshot>>

    @Query("SELECT * FROM net_worth_snapshots WHERE projectId = :pid ORDER BY date DESC")
    suspend fun getNetWorthSnapshotsOnce(pid: String): List<app.fynlo.data.model.NetWorthSnapshot>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNetWorthSnapshot(snapshot: app.fynlo.data.model.NetWorthSnapshot)

    // ─── Investment Valuations ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValuation(v: app.fynlo.data.model.InvestmentValuation)

    @Query("SELECT * FROM investment_valuations WHERE investmentId = :invId ORDER BY date DESC")
    fun getValuationsForInvestment(invId: String): kotlinx.coroutines.flow.Flow<List<app.fynlo.data.model.InvestmentValuation>>

    @Query("SELECT * FROM investment_valuations")
    fun getAllValuations(): kotlinx.coroutines.flow.Flow<List<app.fynlo.data.model.InvestmentValuation>>

    @Query("SELECT * FROM investment_valuations")
    suspend fun getAllValuationsOnce(): List<app.fynlo.data.model.InvestmentValuation>

    @Query("DELETE FROM investment_valuations WHERE investmentId = :invId")
    suspend fun deleteValuationsForInvestment(invId: String)

    @Query("DELETE FROM investment_valuations")
    suspend fun deleteAllValuations()

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAllRecurringTransactions()

    @Query("DELETE FROM net_worth_snapshots")
    suspend fun deleteAllNetWorthSnapshots()

    @Query("DELETE FROM flow_templates")
    suspend fun deleteAllFlowTemplates()

    // ─── Delete-by-id (for Firestore REMOVED sync events) ─────────────────────
    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePaymentById(id: String)

    @Query("DELETE FROM debt_payments WHERE id = :id")
    suspend fun deleteDebtPaymentById(id: String)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudgetByCategory(category: String)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalById(id: String)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteRecurringById(id: String)
}
