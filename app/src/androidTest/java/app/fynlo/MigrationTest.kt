package app.fynlo

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.local.MIGRATION_3_4
import app.fynlo.data.local.MIGRATION_4_5
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore("Legacy v3-v5 migration smoke tests require historical schema JSONs that are no longer retained; current release migration coverage lives in FynloDatabaseMigrationTest.")
class MigrationTest {

    companion object {
        private const val TEST_DB = "migration_test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FynloDatabase::class.java
    )

    @Test
    fun migrate3to4_addsProjectIdAndUpdatedAtToAllTables() {
        // Create v3 database
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO accounts (id, name, type, balance) VALUES ('a1', 'Cash', 'Cash', 1000.0)")
            execSQL("INSERT INTO borrowers (id, name, amount, rate, date) VALUES ('b1', 'Test', 5000.0, 12.0, '2024-01-01')")
            execSQL("INSERT INTO transactions (id, date, type, amount, category) VALUES ('t1', '2024-01-01', 'Income', 1000.0, 'Salary')")
            close()
        }

        // Run migration 3 -> 4
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // Verify projectId and updatedAt columns exist with correct defaults
        val accountCursor = db.query("SELECT projectId, updatedAt FROM accounts WHERE id = 'a1'")
        assertTrue("Account row exists", accountCursor.moveToFirst())
        assertEquals("personal", accountCursor.getString(0))
        assertEquals(0L, accountCursor.getLong(1))
        accountCursor.close()

        val borrowerCursor = db.query("SELECT projectId, updatedAt FROM borrowers WHERE id = 'b1'")
        assertTrue("Borrower row exists", borrowerCursor.moveToFirst())
        assertEquals("personal", borrowerCursor.getString(0))
        assertEquals(0L, borrowerCursor.getLong(1))
        borrowerCursor.close()

        val txnCursor = db.query("SELECT projectId, updatedAt FROM transactions WHERE id = 't1'")
        assertTrue("Transaction row exists", txnCursor.moveToFirst())
        assertEquals("personal", txnCursor.getString(0))
        assertEquals(0L, txnCursor.getLong(1))
        txnCursor.close()

        // Verify projects table was created with default Personal project
        val projectCursor = db.query("SELECT id, name FROM projects WHERE id = 'personal'")
        assertTrue("Default personal project seeded", projectCursor.moveToFirst())
        assertEquals("Personal", projectCursor.getString(1))
        projectCursor.close()

        db.close()
    }

    @Test
    fun migrate4to5_createsFlowTemplatesTable() {
        // Create v3 then migrate to v4 first
        helper.createDatabase(TEST_DB, 3).close()
        val db4 = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        db4.close()

        // Run migration 4 -> 5
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        // Verify flow_templates table exists and is insertable
        db.execSQL(
            "INSERT INTO flow_templates (id, name, eventType, projectId, updatedAt) " +
            "VALUES ('ft1', 'Monthly Salary', 'Received', 'personal', 0)"
        )
        val cursor = db.query("SELECT id, name, eventType FROM flow_templates WHERE id = 'ft1'")
        assertTrue("FlowTemplate row exists", cursor.moveToFirst())
        assertEquals("Monthly Salary", cursor.getString(1))
        assertEquals("Received", cursor.getString(2))
        cursor.close()

        db.close()
    }

    @Test
    fun migrateAll_3to5_dataIntegrityPreserved() {
        // Create v3 with data
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO accounts (id, name, type, balance) VALUES ('a1', 'HDFC Bank', 'Bank', 45000.0)")
            execSQL("INSERT INTO borrowers (id, name, amount, rate, date) VALUES ('b1', 'Ramesh', 10000.0, 2.0, '2024-01-01')")
            close()
        }

        // Run both migrations in sequence
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_3_4, MIGRATION_4_5)

        // Original data still exists
        val accountCursor = db.query("SELECT name, balance FROM accounts WHERE id = 'a1'")
        assertTrue(accountCursor.moveToFirst())
        assertEquals("HDFC Bank", accountCursor.getString(0))
        assertEquals(45000.0, accountCursor.getDouble(1), 0.0)
        accountCursor.close()

        val borrowerCursor = db.query("SELECT name, amount FROM borrowers WHERE id = 'b1'")
        assertTrue(borrowerCursor.moveToFirst())
        assertEquals("Ramesh", borrowerCursor.getString(0))
        assertEquals(10000.0, borrowerCursor.getDouble(1), 0.0)
        borrowerCursor.close()

        db.close()
    }
}
