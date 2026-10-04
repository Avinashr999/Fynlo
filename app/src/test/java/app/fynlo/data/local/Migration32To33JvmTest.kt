package app.fynlo.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration32To33JvmTest {
    @Test fun `history provenance migration keeps every existing column and monetary row`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "history-migration-test"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val schemaFile = listOf(File("schemas/app.fynlo.data.local.FynloDatabase/32.json"),
            File("app/schemas/app.fynlo.data.local.FynloDatabase/32.json")).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val sqlite = SQLiteDatabase.openOrCreateDatabase(path, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.execSQL("INSERT INTO net_worth_snapshots VALUES ('2026-10-01', 123.45, 200, 76.55, 'personal', 12345)")
        sqlite.version = 32
        sqlite.close()
        val room = Room.databaseBuilder(context, FynloDatabase::class.java, name)
            .addMigrations(MIGRATION_32_33).allowMainThreadQueries().build()
        try {
            val row = room.dao().getNetWorthSnapshotForDate("2026-10-01")!!
            assertEquals(123.45, row.netWorth, 0.0)
            assertEquals(200.0, row.totalAssets, 0.0)
            assertEquals(76.55, row.totalLiabilities, 0.0)
            assertEquals(12345, row.createdAt)
            assertEquals("LEGACY", row.captureSource)
            assertEquals("", row.originalSnapshotJson)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
