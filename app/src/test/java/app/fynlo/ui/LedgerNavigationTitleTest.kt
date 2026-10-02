package app.fynlo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LedgerNavigationTitleTest {
    @Test fun `brand belongs to home only`() {
        assertEquals("Fynlo Ledger", ledgerNavigationTitle("home"))
        listOf("loans_hub", "invest", "reports_hub", "spend", "history", "settings", "profile").forEach {
            assertNotEquals("Fynlo Ledger", ledgerNavigationTitle(it))
        }
    }

    @Test fun `loan deep links retain their screen title`() {
        assertEquals("Loans", ledgerNavigationTitle("loans_hub?tab=1"))
        assertEquals("Loans", ledgerNavigationTitle("loans_hub?tab={tab}"))
        assertEquals("Investments", ledgerNavigationTitle("invest"))
        assertEquals("Ledger", ledgerNavigationTitle("unknown"))
    }
}
