package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Account
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C03b Stage #1b-1 (3.2.87) — id-keyed balance mutation gate.
 *
 * Locks in the rename-safety invariant the new
 * `dao.updateAccountBalanceById` query enables: an account rename mutates
 * `Account.name` but never `Account.id`, so an id-keyed balance update
 * still lands on the right row regardless of what the row's name is
 * called when the update fires.
 *
 * Why this matters: the 3.2.59 "orphan account" bug class was rooted in
 * exactly the opposite shape — every UPDATE was keyed on `WHERE name =
 * :name`, so a rename + balance update on the old name silently affected
 * 0 rows. Stage #1b-1 inverts the relationship at the DAO call sites
 * that have a `fromAcctId` / `toAcctId` available (everywhere the
 * Transaction model carries an id mirror).
 *
 * The name-keyed query is kept as a fallback for legacy orphan rows
 * (Stage #1a's resolver populates ids at write time; rows that pre-date
 * Stage #1a and couldn't be resolved by the v22→v23 migration retain
 * id = "" and route through the legacy path).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AccountBalanceByIdDataIntegrityTest {

    private lateinit var db: FynloDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FynloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(id: String, name: String, balance: Double) {
        db.dao().insertAccount(
            Account(id = id, name = name, type = "Bank", balance = balance,
                projectId = "personal", updatedAt = 0L, createdAt = 0L)
        )
    }

    @Test
    fun `updateAccountBalanceById applies delta and reads back the new balance`() = runBlocking {
        seed("acc-1", "HDFC", balance = 1000.0)

        db.dao().updateAccountBalanceById("acc-1", -100.0)

        val out = db.dao().getAccountById("acc-1")!!
        assertEquals(900.0, out.balance, 0.0001)
    }

    @Test
    fun `updateAccountBalanceById survives a name rename — the post-Stage-1a guarantee`() = runBlocking {
        seed("acc-1", "Original Name", balance = 1000.0)

        // Apply first delta while the name is "Original Name".
        db.dao().updateAccountBalanceById("acc-1", -100.0)

        // Now rename the account (the operation that used to break balance
        // updates: pre-Stage-1b-1, every later update keyed on
        // WHERE name = "Original Name" would match 0 rows).
        val renamed = db.dao().getAccountById("acc-1")!!.copy(name = "Brand New Name")
        db.dao().insertAccount(renamed)

        // Second delta after the rename — id is the immutable handle, so
        // this still lands on the row.
        db.dao().updateAccountBalanceById("acc-1", -50.0)

        val out = db.dao().getAccountById("acc-1")!!
        assertEquals("Renamed account must keep its new name verbatim.",
            "Brand New Name", out.name)
        assertEquals(
            "Both deltas must accumulate on the id-keyed row regardless of the rename.",
            850.0, out.balance, 0.0001,
        )
    }

    @Test
    fun `legacy name-keyed update is silently a no-op after a rename — documents the bug we're closing`() = runBlocking {
        seed("acc-1", "Original Name", balance = 1000.0)

        // Rename.
        db.dao().insertAccount(db.dao().getAccountById("acc-1")!!.copy(name = "Renamed"))

        // The name-keyed query against the OLD name affects 0 rows.
        db.dao().updateAccountBalance("Original Name", -100.0)

        val out = db.dao().getAccountById("acc-1")!!
        assertEquals(
            "Name-keyed update against the old name must NOT change the row. " +
                "Documents the orphan-bug pattern that Stage #1b-1 closes; the " +
                "id-keyed path bypasses this entirely.",
            1000.0, out.balance, 0.0001,
        )
    }

    @Test
    fun `updateAccountBalanceById is a no-op for a nonexistent id`() = runBlocking {
        seed("acc-1", "HDFC", balance = 1000.0)

        db.dao().updateAccountBalanceById("nonexistent-id", -100.0)

        val out = db.dao().getAccountById("acc-1")!!
        assertEquals(
            "Nonexistent id must not bleed the delta into the wrong row.",
            1000.0, out.balance, 0.0001,
        )
    }

    @Test
    fun `updateAccountBalanceById is a no-op for empty id — guards the fallback path entry`() = runBlocking {
        seed("acc-1", "HDFC", balance = 1000.0)

        // The repository's `applyAccountDelta` helper routes empty ids to
        // the name-keyed query; calling the id-keyed query directly with
        // "" must affect 0 rows (no row has id == "").
        db.dao().updateAccountBalanceById("", -100.0)

        val out = db.dao().getAccountById("acc-1")!!
        assertEquals(1000.0, out.balance, 0.0001)
    }

    @Test
    fun `both queries operate on the same row when both id and name resolve to it`() = runBlocking {
        seed("acc-1", "HDFC", balance = 1000.0)

        db.dao().updateAccountBalanceById("acc-1", -100.0)
        db.dao().updateAccountBalance("HDFC",      -50.0)

        val out = db.dao().getAccountById("acc-1")!!
        assertEquals(
            "Both code paths target the same row when name and id are consistent " +
                "(the steady-state shape Stage #1a's resolver maintains).",
            850.0, out.balance, 0.0001,
        )
    }

    @Test
    fun `multiple accounts — id-keyed update only touches the targeted row`() = runBlocking {
        seed("acc-cash", "Cash", balance = 500.0)
        seed("acc-hdfc", "HDFC", balance = 1000.0)
        seed("acc-sbi",  "SBI",  balance = 2000.0)

        db.dao().updateAccountBalanceById("acc-hdfc", -300.0)

        assertEquals(500.0,  db.dao().getAccountById("acc-cash")!!.balance, 0.0001)
        assertEquals(700.0,  db.dao().getAccountById("acc-hdfc")!!.balance, 0.0001)
        assertEquals(2000.0, db.dao().getAccountById("acc-sbi")!!.balance,  0.0001)
    }
}
