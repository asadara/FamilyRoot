package com.example.familytreeplatform.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonDaoTest {
    private lateinit var database: FamilyTreeDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FamilyTreeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.personDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceSpaceKeepsCachesIsolatedBySpace() = runBlocking {
        dao.replaceSpace(
            "space-a",
            listOf(
                PersonEntity(
                    personId = "person-1",
                    spaceId = "space-a",
                    fullName = "Budi Santoso",
                    createdAt = "2026-07-13T01:00:00.000Z",
                    lifeStatus = "ALIVE",
                    deceasedAt = null,
                    birthDate = null,
                    gender = "MALE"
                )
            )
        )
        dao.replaceSpace(
            "space-b",
            listOf(
                PersonEntity(
                    personId = "person-2",
                    spaceId = "space-b",
                    fullName = "Siti Aminah",
                    createdAt = "2026-07-13T02:00:00.000Z",
                    lifeStatus = "ALIVE",
                    deceasedAt = null,
                    birthDate = null,
                    gender = "FEMALE"
                )
            )
        )
        dao.replaceSpace("space-a", emptyList())

        assertEquals(emptyList<PersonEntity>(), dao.observeBySpace("space-a").first())
        assertEquals("Siti Aminah", dao.observeBySpace("space-b").first().single().fullName)
    }
}
