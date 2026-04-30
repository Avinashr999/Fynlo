package com.example.cashmemo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cashmemo.data.model.Account
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.data.model.Budget
import com.example.cashmemo.data.model.Debt
import com.example.cashmemo.data.model.DebtPayment
import com.example.cashmemo.data.model.Goal
import com.example.cashmemo.data.model.Investment
import com.example.cashmemo.data.model.Payment
import com.example.cashmemo.data.model.Person
import com.example.cashmemo.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CashMemoDao {
    @Query("SELECT * FROM borrowers")
    fun getAllBorrowers(): Flow<List<Borrower>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrower(borrower: Borrower)

    @Query("UPDATE borrowers SET paid = paid + :amount WHERE id = :borrowerId")
    suspend fun updateBorrowerPaidAmount(borrowerId: String, amount: Double)

    @Delete
    suspend fun deleteBorrower(borrower: Borrower)

    @Query("SELECT * FROM payments WHERE loanId = :loanId")
    fun getPaymentsForLoan(loanId: String): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPayment)

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): Account?

    @Query("UPDATE accounts SET balance = balance + :amount WHERE name = :name")
    suspend fun updateAccountBalance(name: String, amount: Double)

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM investments")
    fun getAllInvestments(): Flow<List<Investment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: Investment)

    @Delete
    suspend fun deleteInvestment(investment: Investment)

    @Query("SELECT * FROM debts")
    fun getAllDebts(): Flow<List<Debt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt)

    @Query("UPDATE debts SET paid = paid + :amount WHERE id = :debtId")
    suspend fun updateDebtPaidAmount(debtId: String, amount: Double)

    @Delete
    suspend fun deleteDebt(debt: Debt)

    // People / Contact Management
    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeople(): Flow<List<Person>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    // Budgets & Goals
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<Budget>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)
    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("SELECT * FROM goals")
    fun getAllGoals(): Flow<List<Goal>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal)
    @Delete
    suspend fun deleteGoal(goal: Goal)

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
}