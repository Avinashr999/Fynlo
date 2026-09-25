package app.fynlo.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * JVM (Robolectric) migration test 31 → 32. Builds a real v31 SQLite file from
 * the exported schema (app/schemas/.../31.json), fills it, then opens it with
 * Room at v32 using ONLY MIGRATION_31_32 — Room validates the migrated schema
 * against the v32 entities, and every row must survive with the new defaults.
 * (The instrumented twin lives in androidTest FynloDatabaseMigrationTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration31To32JvmTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-31-32-test.db"

    @After fun tearDown() { ctx.deleteDatabase(dbName) }

    private fun schema31(): JSONObject {
        val f = listOf(
            File("schemas/app.fynlo.data.local.FynloDatabase/31.json"),
            File("app/schemas/app.fynlo.data.local.FynloDatabase/31.json"),
        ).first { it.exists() }
        return JSONObject(f.readText()).getJSONObject("database")
    }

    private fun createV31() {
        ctx.deleteDatabase(dbName)
        val file = ctx.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val schema = schema31()
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            e.optJSONArray("indices")?.let { idx ->
                for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))

        for (n in 1..3) {
            db.execSQL(
                """INSERT INTO borrowers (id,name,phone,peopleId,address,guarantor,amount,rate,date,due,tenure,type,paid,
                   paidPrincipal,paidInterest,interestWaived,status,defaultDate,frozenInterest,sourceAccount,
                   stopInterestAfterDue,notes,projectId,updatedAt,createdAt)
                   VALUES ('b$n','Borrower $n','98$n','','','',${n * 1000}.5,12.0,'2026-01-0$n','',0,'Compound Interest',
                   10.0,7.0,3.0,1.25,'Active','',0.0,'Cash',1,'note $n','personal',$n,$n)"""
            )
            db.execSQL(
                """INSERT INTO debts (id,name,phone,peopleId,type,amount,rate,date,due,tenure,intType,paid,paidPrincipal,
                   paidInterest,interestWaived,status,stopInterestAfterDue,collateral,notes,projectId,updatedAt,createdAt)
                   VALUES ('d$n','Debt $n','','','Bank',${n * 2000}.0,10.0,'2026-02-0$n','',12,'Reducing Balance',
                   5.0,5.0,0.0,0.0,'Active',0,'gold','dn $n','personal',$n,$n)"""
            )
            db.execSQL(
                """INSERT INTO payments (id,loanId,name,date,type,amount,principal,interest,interestPeriodStartDate,
                   interestPeriodEndDate,interestAllocationType,mode,notes,projectId,updatedAt,createdAt)
                   VALUES ('p$n','b$n','Borrower $n','2026-03-0$n','Both',${n}01.37,100.0,$n.37,'','','CURRENT_PERIOD_INTEREST',
                   'UPI','Penalty on this account 1.37','personal',$n,$n)"""
            )
            db.execSQL(
                """INSERT INTO debt_payments (id,debtId,name,date,type,amount,principal,interest,interestPeriodStartDate,
                   interestPeriodEndDate,interestAllocationType,mode,notes,projectId,updatedAt,createdAt)
                   VALUES ('q$n','d$n','Debt $n','2026-03-0$n','Both',$n.5,$n.5,0.0,'','','PRINCIPAL_REPAYMENT',
                   '','','personal',$n,$n)"""
            )
        }
        db.version = 31
        db.close()
    }

    @Test
    fun `migrate 31 to 32 keeps every row and applies defaults`() = runBlocking {
        createV31()
        val room = Room.databaseBuilder(ctx, FynloDatabase::class.java, dbName)
            .addMigrations(MIGRATION_31_32)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = room.dao()
            val borrowers = (1..3).map { dao.getBorrowerById("b$it")!! }
            borrowers.forEachIndexed { i, b ->
                val n = i + 1
                assertEquals("Borrower $n", b.name)
                assertEquals(n * 1000 + 0.5, b.amount, 0.0)
                assertEquals("Compound Interest", b.intType)
                assertEquals(7.0, b.paidPrincipal, 0.0)
                assertEquals(1.25, b.interestWaived, 0.0)
                assertEquals(true, b.stopInterestAfterDue)
                assertEquals("note $n", b.notes)
                assertEquals("Monthly", b.compoundFrequency)
            }
            (1..3).forEach { n ->
                val d = dao.getDebtById("d$n")!!
                assertEquals(n * 2000.0, d.amount, 0.0)
                assertEquals("gold", d.collateral)
                assertEquals("Monthly", d.compoundFrequency)
                val p = dao.getPaymentById("p$n")!!
                assertEquals("b$n", p.loanId)
                assertEquals(n * 100 + 1.37, p.amount, 1e-9)
                assertEquals("Penalty on this account 1.37", p.notes)
                assertEquals(0L, p.penaltyPaise)
                assertEquals(0L, p.roundingPaise)
                val q = dao.getDebtPaymentById("q$n")!!
                assertEquals(n + 0.5, q.amount, 0.0)
                assertEquals(0L, q.penaltyPaise)
                assertEquals(0L, q.roundingPaise)
            }
            assertEquals(3, dao.getPaymentsForLoanOnce("b1").size + dao.getPaymentsForLoanOnce("b2").size + dao.getPaymentsForLoanOnce("b3").size)
            assertEquals(3, (1..3).sumOf { dao.getDebtPaymentsForDebtOnce("d$it").size })
        } finally {
            room.close()
        }
    }
}
