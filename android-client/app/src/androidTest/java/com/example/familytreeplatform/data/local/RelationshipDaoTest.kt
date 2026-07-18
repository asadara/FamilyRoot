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
class RelationshipDaoTest {
    private lateinit var database: FamilyTreeDatabase
    private lateinit var dao: RelationshipDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FamilyTreeDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.relationshipDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pendingRelationshipSurvivesNetworkReplacementAndCanBeRolledBack() = runBlocking {
        val pending = CachedRelationshipEntity(
            relationshipId = "local-mutation-1",
            spaceId = "space-1",
            type = "PARENT_CHILD",
            fromPersonId = "parent-1",
            toPersonId = "child-1",
            meta = "BIOLOGICAL",
            startDate = null,
            endDate = null,
            createdAt = "2026-07-18T00:00:00.000Z",
            pendingMutationId = "mutation-1"
        )
        dao.upsert(pending)
        dao.replaceSynced("space-1", emptyList())
        assertEquals("mutation-1", dao.observeBySpace("space-1").first().single().pendingMutationId)

        dao.deleteByMutation("mutation-1")
        assertEquals(emptyList<CachedRelationshipEntity>(), dao.observeBySpace("space-1").first())
    }
}
