package com.example.familytreeplatform.data.local

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
class SourceDaoTest {
    private lateinit var database: FamilyTreeDatabase
    private lateinit var dao: SourceDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FamilyTreeDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.sourceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceSyncedKeepsPendingAndRemapMovesLocalSource() = runBlocking {
        dao.upsert(
            source(
                sourceId = "local-mutation-1",
                personId = "local-person",
                pendingMutationId = "mutation-1"
            )
        )
        dao.replaceSynced(
            "local-person",
            listOf(source(sourceId = "server-source", personId = "local-person"))
        )

        assertEquals(2, dao.observeByPerson("local-person").first().size)
        dao.remapPerson("local-person", "server-person")

        val remapped = dao.listByPerson("server-person")
        assertEquals(2, remapped.size)
        assertEquals(
            "mutation-1",
            remapped.first { it.sourceId == "local-mutation-1" }.pendingMutationId
        )
    }

    @Test
    fun deleteByMutationRollsBackOnlyMatchingOptimisticSource() = runBlocking {
        dao.upsert(source(sourceId = "server-source", personId = "person-1"))
        dao.upsert(
            source(
                sourceId = "local-mutation-1",
                personId = "person-1",
                pendingMutationId = "mutation-1"
            )
        )

        dao.deleteByMutation("mutation-1")

        assertEquals(listOf("server-source"), dao.listByPerson("person-1").map { it.sourceId })
    }

    private fun source(
        sourceId: String,
        personId: String,
        pendingMutationId: String? = null
    ) = CachedSourceEntity(
        sourceId = sourceId,
        spaceId = "space-1",
        personId = personId,
        title = "Arsip keluarga",
        type = "STORY",
        url = null,
        note = "Catatan",
        createdAt = "2026-07-29T00:00:00.000Z",
        pendingMutationId = pendingMutationId
    )
}
