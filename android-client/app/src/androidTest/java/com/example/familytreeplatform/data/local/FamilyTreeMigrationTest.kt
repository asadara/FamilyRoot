package com.example.familytreeplatform.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FamilyTreeMigrationTest {
    private val databaseName = "family-tree-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FamilyTreeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To4PreservesPeopleAndAddsOfflineRelationshipCache() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """INSERT INTO persons
                    (personId, spaceId, fullName, createdAt, lifeStatus, deceasedAt, birthDate, gender)
                    VALUES ('person-1', 'space-1', 'Budi', '2026-07-17', 'ALIVE', NULL, NULL, 'MALE')"""
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4
        ).use { database ->
            database.query("SELECT fullName, version, birthPlace, notes FROM persons WHERE personId = 'person-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Budi", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(null, cursor.getString(2))
                assertEquals(null, cursor.getString(3))
            }
            database.query("SELECT COUNT(*) FROM offline_mutations").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM relationships").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
