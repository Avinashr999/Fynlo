package com.example.cashmemo

import com.example.cashmemo.logic.InterestEngine
import org.junit.Assert.*
import org.junit.Test

class InterestEngineTest {

    @Test fun `daysBetween same date returns 0`() = assertEquals(0L, InterestEngine.daysBetween("2024-01-01", "2024-01-01"))
    @Test fun `daysBetween 1 year returns 365`() = assertEquals(365L, InterestEngine.daysBetween("2023-01-01", "2024-01-01"))
    @Test fun `daysBetween end before start returns 0`() = assertEquals(0L, InterestEngine.daysBetween("2024-06-01", "2024-01-01"))
    @Test fun `daysBetween invalid date returns 0`() = assertEquals(0L, InterestEngine.daysBetween("bad-date", "2024-01-01"))

    @Test fun `zero rate returns 0`() = assertEquals(0.0, InterestEngine.calcIntAccrued(10000.0, 0.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01"), 0.0)
    @Test fun `zero amount returns 0`() = assertEquals(0.0, InterestEngine.calcIntAccrued(0.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01"), 0.0)
    @Test fun `empty loanDate returns 0`() = assertEquals(0.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "", "Simple Interest", asOf = "2024-01-01"), 0.0)
    @Test fun `asOf same as loanDate returns 0`() = assertEquals(0.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "2024-01-01", "Simple Interest", asOf = "2024-01-01"), 0.0)
    @Test fun `fully paid loan returns 0 interest`() = assertEquals(0.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", totalPaid = 10000.0, asOf = "2024-01-01"), 0.0)
    @Test fun `overpaid loan returns 0 interest`() = assertEquals(0.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", totalPaid = 15000.0, asOf = "2024-01-01"), 0.0)

    @Test fun `SI 1 year 12 percent on 10000 equals 1200`() {
        assertEquals(1200.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01"), 1.0)
    }
    @Test fun `SI 2 years 10 percent on 50000 equals 10000`() {
        val r = InterestEngine.calcIntAccrued(50000.0, 10.0, "2022-01-01", "Simple Interest", asOf = "2024-01-01")
        assertTrue("Expected ~10000 got $r", r in 9900.0..10100.0)
    }
    @Test fun `SI partial payment reduces principal`() {
        assertEquals(600.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", totalPaid = 5000.0, asOf = "2024-01-01"), 1.0)
    }
    @Test fun `SI 6 months is roughly half of annual`() {
        val annual = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        val half   = InterestEngine.calcIntAccrued(10000.0, 12.0, "2024-01-01", "Simple Interest", asOf = "2024-07-01")
        assertTrue("6m SI ($half) should be ~half of annual ($annual)", half in (annual * 0.45)..(annual * 0.55))
    }

    @Test fun `CI 1 year 12 percent on 10000 equals 1200`() {
        assertEquals(1200.0, InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Compound Interest", asOf = "2024-01-01"), 1.0)
    }
    @Test fun `CI 2 years 12 percent on 10000 equals 2544`() {
        val r = InterestEngine.calcIntAccrued(10000.0, 12.0, "2022-01-01", "Compound Interest", asOf = "2024-01-01")
        assertTrue("Expected 2530-2560 got $r", r in 2530.0..2560.0)
    }
    @Test fun `CI 3 years 10 percent on 50000 equals 16550`() {
        val r = InterestEngine.calcIntAccrued(50000.0, 10.0, "2021-01-01", "Compound Interest", asOf = "2024-01-01")
        assertTrue("Expected 16400-16700 got $r", r in 16400.0..16700.0)
    }
    @Test fun `CI always greater than SI beyond 1 year`() {
        val si = InterestEngine.calcIntAccrued(10000.0, 12.0, "2021-01-01", "Simple Interest", asOf = "2024-01-01")
        val ci = InterestEngine.calcIntAccrued(10000.0, 12.0, "2021-01-01", "Compound Interest", asOf = "2024-01-01")
        assertTrue("CI ($ci) must be > SI ($si)", ci > si)
    }
    @Test fun `CI equals SI at exactly 1 year`() {
        val si = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        val ci = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Compound Interest", asOf = "2024-01-01")
        assertEquals(si, ci, 1.0)
    }

    @Test fun `Reducing Balance interest is less than Simple Interest`() {
        val si = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        val rb = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Reducing Balance", asOf = "2024-01-01")
        assertTrue("Reducing ($rb) should be less than Simple ($si)", rb < si)
    }

    @Test fun `Reducing Balance returns positive interest`() {
        val rb = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Reducing Balance", asOf = "2024-01-01")
        assertTrue("Reducing balance interest should be > 0, got $rb", rb > 0.0)
    }

    @Test fun `overdue loan switches to compound calculation`() {
        val overdue = InterestEngine.calcIntAccrued(10000.0, 12.0, "2022-01-01", "Simple Interest", dueDate = "2023-01-01", asOf = "2024-01-01")
        val ci      = InterestEngine.calcIntAccrued(10000.0, 12.0, "2022-01-01", "Compound Interest", asOf = "2024-01-01")
        assertEquals(ci, overdue, 1.0)
    }
    @Test fun `Both type uses compound calculation`() {
        val both = InterestEngine.calcIntAccrued(10000.0, 12.0, "2022-01-01", "Both", asOf = "2024-01-01")
        val ci   = InterestEngine.calcIntAccrued(10000.0, 12.0, "2022-01-01", "Compound Interest", asOf = "2024-01-01")
        assertEquals(ci, both, 1.0)
    }
    @Test fun `not yet overdue keeps simple interest`() {
        val withFutureDue = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", dueDate = "2030-01-01", asOf = "2024-01-01")
        val plainSI       = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        assertEquals(plainSI, withFutureDue, 0.0)
    }

    @Test fun `outstanding principal plus interest minus paid`() = assertEquals(6200.0, InterestEngine.calcOutstanding(10000.0, 1200.0, 5000.0), 0.0)
    @Test fun `outstanding never below 0`() = assertEquals(0.0, InterestEngine.calcOutstanding(10000.0, 500.0, 20000.0), 0.0)
    @Test fun `outstanding fully paid is 0`() = assertEquals(0.0, InterestEngine.calcOutstanding(10000.0, 1200.0, 11200.0), 0.0)
    @Test fun `outstanding no payments equals principal plus interest`() = assertEquals(11200.0, InterestEngine.calcOutstanding(10000.0, 1200.0, 0.0), 0.0)
}