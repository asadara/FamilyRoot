package com.example.familytreeplatform.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineMutationDaoTest {
    private lateinit var database: FamilyTreeDatabase
    private lateinit var dao: OfflineMutationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FamilyTreeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.offlineMutationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun conflictIsNotRetriedUntilUserChoosesResolution() = runBlocking {
        val mutation = OfflineMutationEntity(
            mutationId = "mutation-1",
            spaceId = "space-1",
            personId = "person-1",
            mutationType = OfflineMutationType.UPDATE_LIFE_STATUS,
            payloadJson = "{\"lifeStatus\":\"UNKNOWN\"}",
            baseVersion = 1,
            status = OfflineMutationStatus.PENDING,
            attemptCount = 0,
            lastError = null,
            conflictVersion = null,
            conflictPayloadJson = null,
            createdAt = 1L,
            updatedAt = 1L
        )
        dao.upsert(mutation)
        assertEquals(1, dao.listReady().size)

        dao.markConflict(
            mutationId = mutation.mutationId,
            error = "Server changed",
            serverVersion = 2,
            conflictPayloadJson = "{\"version\":2}",
            updatedAt = 2L
        )
        assertTrue(dao.listReady().isEmpty())
        assertEquals(OfflineMutationStatus.CONFLICT, dao.observeForPerson("person-1").first().single().status)

        dao.retryWithVersion(mutation.mutationId, baseVersion = 2, updatedAt = 3L)
        assertEquals(2, dao.listReady().single().baseVersion)
    }

    @Test
    fun profileAndLifeMutationsCoexistAndCanBeRebased() = runBlocking {
        val life = OfflineMutationEntity(
            mutationId = "life-1",
            spaceId = "space-1",
            personId = "person-1",
            mutationType = OfflineMutationType.UPDATE_LIFE_STATUS,
            payloadJson = "{\"lifeStatus\":\"UNKNOWN\"}",
            baseVersion = 1,
            status = OfflineMutationStatus.PENDING,
            attemptCount = 0,
            lastError = null,
            conflictVersion = null,
            conflictPayloadJson = null,
            createdAt = 1L,
            updatedAt = 1L
        )
        val profile = life.copy(
            mutationId = "profile-1",
            mutationType = OfflineMutationType.UPDATE_PROFILE,
            payloadJson = "{\"birthPlace\":\"Bandung\",\"notes\":\"Test\"}",
            createdAt = 2L,
            updatedAt = 2L
        )
        dao.upsert(life)
        dao.upsert(profile)

        assertEquals(2, dao.observeForPerson("person-1").first().size)
        dao.deleteForPersonAndType("person-1", OfflineMutationType.UPDATE_PROFILE)
        assertEquals(OfflineMutationType.UPDATE_LIFE_STATUS, dao.observeForPerson("person-1").first().single().mutationType)

        dao.upsert(profile)
        dao.rebasePendingForPerson("person-1", "life-1", 2, 3L)
        assertEquals(2, dao.observeForPerson("person-1").first().last().baseVersion)
    }

    @Test
    fun privacyClearCanDetectAndRemoveSpaceQueue() = runBlocking {
        val mutation = OfflineMutationEntity(
            mutationId = "privacy-1",
            spaceId = "private-space",
            personId = "person-1",
            mutationType = OfflineMutationType.UPDATE_PROFILE,
            payloadJson = "{}",
            baseVersion = 1,
            status = OfflineMutationStatus.CONFLICT,
            attemptCount = 1,
            lastError = "Conflict",
            conflictVersion = 2,
            conflictPayloadJson = "{}",
            createdAt = 1L,
            updatedAt = 1L
        )
        dao.upsert(mutation)

        assertEquals(1, dao.countForSpace("private-space"))
        dao.deleteBySpace("private-space")
        assertEquals(0, dao.countForSpace("private-space"))
    }
}
