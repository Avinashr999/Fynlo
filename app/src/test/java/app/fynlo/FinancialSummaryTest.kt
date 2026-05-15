package app.fynlo

import app.fynlo.data.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for financial summary calculation logic — verifies the core
 * net worth, asset, and liability calculations that users see on Dashboard.
 */
class FinancialSummaryTest {

    // ── Helper: build a FinancialSummary from raw data ───────────────────────
    private fun calcSummary(
        accounts:    List<Account>     = emptyList(),
        borrowers:   List<Borrower>    = emptyList(),
        investments: List<Investment>  = emptyList(),
        debts:       List<Debt>        = emptyList(),
        transactions: List<Transaction> = emptyList()
    ): FinancialSummary {
        val totalCash        = accounts.sumOf { it.balance }
        val totalInvestments = investments.sumOf { it.currentVal }
        val activeBrws       = borrowers.filter { it.status != "WrittenOff" }
        val totalInterestLoans = activeBrws.filter { it.rate > 0 }.sumOf { b ->
            (b.amount - b.paidPrincipal).coerceAtLeast(0.0)
        }
        val totalHandLoans = activeBrws.filter { it.rate <= 0 }.sumOf { b ->
            (b.amount - b.paidPrincipal).coerceAtLeast(0.0)
        }
        val totalAssets       = totalCash + totalInvestments + totalInterestLoans + totalHandLoans
        val totalDebtPrincipal = debts.sumOf { (it.amount - it.paidPrincipal).coerceAtLeast(0.0) }
        val totalDebtInterest  = 0.0 // simplified for test
        val netWorth           = totalAssets - (totalDebtPrincipal + totalDebtInterest)
        val totalExpenses      = transactions.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome        = transactions.filter { it.type.lowercase() == "income" }.sumOf { it.amount }
        val invGrowth          = investments.sumOf { it.currentVal - (it.invested - it.withdrawn) }

        return FinancialSummary(
            totalCash           = totalCash,
            totalInvestments    = totalInvestments,
            totalInterestLoans  = totalInterestLoans,
            totalHandLoans      = totalHandLoans,
            totalAssets         = totalAssets,
            totalDebtPrincipal  = totalDebtPrincipal,
            totalDebtInterest   = totalDebtInterest,
            netWorth            = netWorth,
            totalExpenses       = totalExpenses,
            totalIncome         = totalIncome,
            investmentGrowth    = invGrowth,
            savingsRate         = if (totalIncome > 0) (totalIncome - totalExpenses) / totalIncome else 0.0,
            avgLendingYield     = if (activeBrws.isNotEmpty()) activeBrws.map { it.rate }.average() else 0.0,
            accountBreakdown    = accounts.associate { it.name to it.balance },
            investmentByType    = investments.groupBy { it.type }.mapValues { e -> e.value.sumOf { it.currentVal } },
            interestLoanMap     = emptyMap(),
            handLoanMap         = emptyMap(),
            accountGrowthMap    = emptyMap(),
            totalBadDebtWriteOffs = 0.0,
            totalInterestExpense  = 0.0,
            totalInterestIncome   = 0.0
        )
    }

    private fun acct(name: String, balance: Double) = Account(
        id = name, name = name, balance = balance, type = "Bank",
        notes = "", projectId = "personal"
    )
    private fun borrower(id: String, amount: Double, paidPrincipal: Double = 0.0, rate: Double = 12.0, status: String = "Active") =
        Borrower(id=id, name="B$id", amount=amount, rate=rate, date="2024-01-01", due="2025-01-01",
            type="Simple Interest", paid=paidPrincipal, paidPrincipal=paidPrincipal, paidInterest=0.0,
            status=status, notes="", phone="", projectId="personal", frozenInterest=0.0,
            defaultDate="", sourceAccount="", withdrawn=0.0)
    private fun invest(id: String, invested: Double, currentVal: Double, withdrawn: Double = 0.0) =
        Investment(id=id, name="I$id", type="Equity", invested=invested, currentVal=currentVal,
            date="2024-01-01", notes="", projectId="personal", withdrawn=withdrawn)
    private fun debt(id: String, amount: Double, paidPrincipal: Double = 0.0) =
        Debt(id=id, name="D$id", amount=amount, rate=8.0, date="2024-01-01", due="2025-01-01",
            type="Reducing Balance", paid=paidPrincipal, paidPrincipal=paidPrincipal, paidInterest=0.0,
            notes="", projectId="personal", lenderName="Bank", withdrawn=0.0)

    // ── Net Worth Tests ───────────────────────────────────────────────────────
    @Test fun `net worth with only cash equals cash balance`() {
        val s = calcSummary(accounts = listOf(acct("Cash", 10_000.0)))
        assertEquals(10_000.0, s.netWorth, 0.01)
    }

    @Test fun `net worth with debt is assets minus debt`() {
        val s = calcSummary(
            accounts = listOf(acct("Bank", 50_000.0)),
            debts    = listOf(debt("loan1", 20_000.0))
        )
        assertEquals(30_000.0, s.netWorth, 0.01)
    }

    @Test fun `net worth can be negative when debts exceed assets`() {
        val s = calcSummary(
            accounts = listOf(acct("Bank", 5_000.0)),
            debts    = listOf(debt("bigLoan", 1_00_000.0))
        )
        assertTrue("Net worth should be negative", s.netWorth < 0)
    }

    @Test fun `zero assets and zero debts = zero net worth`() {
        val s = calcSummary()
        assertEquals(0.0, s.netWorth, 0.0)
    }

    // ── Asset Breakdown Tests ─────────────────────────────────────────────────
    @Test fun `totalCash sums all account balances`() {
        val s = calcSummary(accounts = listOf(acct("A", 10_000.0), acct("B", 25_000.0), acct("C", 5_000.0)))
        assertEquals(40_000.0, s.totalCash, 0.01)
    }

    @Test fun `totalInvestments sums current values not invested`() {
        val s = calcSummary(investments = listOf(
            invest("i1", invested = 50_000.0, currentVal = 65_000.0),
            invest("i2", invested = 30_000.0, currentVal = 28_000.0)
        ))
        assertEquals(93_000.0, s.totalInvestments, 0.01)
    }

    @Test fun `investmentGrowth is profit across portfolio`() {
        val s = calcSummary(investments = listOf(
            invest("i1", invested = 50_000.0, currentVal = 65_000.0),
            invest("i2", invested = 30_000.0, currentVal = 28_000.0)
        ))
        assertEquals(13_000.0, s.investmentGrowth, 0.01) // +15k -2k
    }

    @Test fun `investmentGrowth accounts for withdrawals`() {
        // Invested 50k, withdrew 10k, current value is 45k → real growth = 45-(50-10) = 5k
        val s = calcSummary(investments = listOf(
            invest("i1", invested = 50_000.0, currentVal = 45_000.0, withdrawn = 10_000.0)
        ))
        assertEquals(5_000.0, s.investmentGrowth, 0.01)
    }

    // ── Lending Tests ─────────────────────────────────────────────────────────
    @Test fun `totalInterestLoans sums only rate>0 borrowers`() {
        val s = calcSummary(borrowers = listOf(
            borrower("b1", 10_000.0, rate = 12.0),  // interest loan
            borrower("b2", 5_000.0,  rate = 0.0)    // hand loan
        ))
        assertEquals(10_000.0, s.totalInterestLoans, 0.01)
        assertEquals(5_000.0,  s.totalHandLoans,     0.01)
    }

    @Test fun `paid principal reduces outstanding in assets`() {
        val s = calcSummary(borrowers = listOf(
            borrower("b1", amount = 10_000.0, paidPrincipal = 3_000.0, rate = 12.0)
        ))
        assertEquals(7_000.0, s.totalInterestLoans, 0.01)
    }

    @Test fun `written-off borrowers excluded from assets`() {
        val s = calcSummary(borrowers = listOf(
            borrower("b1", 10_000.0, status = "Active"),
            borrower("b2", 5_000.0,  status = "WrittenOff") // should be excluded
        ))
        assertEquals(10_000.0, s.totalInterestLoans, 0.01)
    }

    // ── Debt Tests ────────────────────────────────────────────────────────────
    @Test fun `debt principal reduces after payment`() {
        val s = calcSummary(debts = listOf(
            debt("d1", amount = 1_00_000.0, paidPrincipal = 20_000.0)
        ))
        assertEquals(80_000.0, s.totalDebtPrincipal, 0.01)
    }

    @Test fun `fully paid debt contributes 0 to liabilities`() {
        val s = calcSummary(debts = listOf(
            debt("d1", amount = 10_000.0, paidPrincipal = 10_000.0)
        ))
        assertEquals(0.0, s.totalDebtPrincipal, 0.01)
    }

    // ── Income/Expense Tests ──────────────────────────────────────────────────
    private fun tx(type: String, amount: Double) = Transaction(
        id = java.util.UUID.randomUUID().toString(), type = type, category = "Test",
        amount = amount, date = "2024-05-01", fromAcct = "Bank", toAcct = "",
        desc = "", notes = "", tags = "", projectId = "personal"
    )

    @Test fun `totalIncome sums only income type transactions`() {
        val s = calcSummary(transactions = listOf(
            tx("income",  50_000.0),
            tx("expense", 20_000.0),
            tx("Income",  10_000.0) // case-insensitive
        ))
        assertEquals(60_000.0, s.totalIncome,   0.01)
        assertEquals(20_000.0, s.totalExpenses, 0.01)
    }

    @Test fun `savingsRate is 0 when no income`() {
        val s = calcSummary(transactions = listOf(tx("expense", 5_000.0)))
        assertEquals(0.0, s.savingsRate, 0.0)
    }

    @Test fun `savingsRate is positive when income exceeds expenses`() {
        val s = calcSummary(transactions = listOf(
            tx("income",  50_000.0),
            tx("expense", 30_000.0)
        ))
        assertEquals(0.4, s.savingsRate, 0.01) // (50k-30k)/50k = 40%
    }

    // ── Account Breakdown ─────────────────────────────────────────────────────
    @Test fun `accountBreakdown maps name to balance`() {
        val s = calcSummary(accounts = listOf(acct("HDFC", 42_000.0), acct("SBI", 8_000.0)))
        assertEquals(42_000.0, s.accountBreakdown["HDFC"] ?: 0.0, 0.01)
        assertEquals(8_000.0,  s.accountBreakdown["SBI"]  ?: 0.0, 0.01)
    }

    @Test fun `investmentByType groups correctly`() {
        val s = calcSummary(investments = listOf(
            invest("i1", 10_000.0, 12_000.0).copy(type = "Equity"),
            invest("i2", 20_000.0, 22_000.0).copy(type = "Equity"),
            invest("i3", 50_000.0, 52_000.0).copy(type = "FD")
        ))
        assertEquals(34_000.0, s.investmentByType["Equity"] ?: 0.0, 0.01)
        assertEquals(52_000.0, s.investmentByType["FD"]     ?: 0.0, 0.01)
    }
}
