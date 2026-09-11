package com.margin.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.margin.app.data.db.MarginDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An installed copy of the first release must open on the second without losing anything.
 * Checked against the exported schemas, so a column added without a default fails here rather
 * than on someone's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MarginDatabase::class.java,
    )

    @Test
    fun version1DataSurvivesTheMoveToVersion2() {
        val name = "migration-data"
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO subjects (code, name, shortName, reviewWeight, colorIndex, active) " +
                    "VALUES ('DSA', 'Data Structures and Algorithms', 'DSA', 1.0, 0, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 2, true)
        migrated.query("SELECT name, importance, difficulty FROM subjects WHERE code = 'DSA'").use { cursor ->
            assertTrue("the subject must still be there", cursor.moveToFirst())
            assertEquals("Data Structures and Algorithms", cursor.getString(0))
            assertEquals("new columns take their defaults", 0, cursor.getInt(1))
            assertNotNull(cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    fun theMigratedDatabaseOpensThroughRoom() {
        val name = "migration-open"
        helper.createDatabase(name, 1).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarginDatabase::class.java,
            name,
        ).build()
        // Opening runs the migration; anything Room cannot reconcile throws here.
        database.openHelper.writableDatabase
        database.close()
    }
}
