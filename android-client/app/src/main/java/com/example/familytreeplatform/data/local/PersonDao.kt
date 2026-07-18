package com.example.familytreeplatform.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons WHERE spaceId = :spaceId ORDER BY createdAt DESC")
    fun observeBySpace(spaceId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE personId = :personId LIMIT 1")
    fun observeById(personId: String): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE personId = :personId LIMIT 1")
    suspend fun getById(personId: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE spaceId = :spaceId ORDER BY createdAt DESC")
    suspend fun listBySpace(spaceId: String): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PersonEntity>)

    @Query("DELETE FROM persons WHERE spaceId = :spaceId")
    suspend fun deleteBySpace(spaceId: String)

    @Query("UPDATE persons SET lifeStatus = :lifeStatus, deceasedAt = :deceasedAt WHERE personId = :personId")
    suspend fun updateLifeStatusLocally(personId: String, lifeStatus: String, deceasedAt: String?)

    @Query("UPDATE persons SET lifeStatus = :lifeStatus, deceasedAt = :deceasedAt, version = :version WHERE personId = :personId")
    suspend fun applySyncedLifeStatus(personId: String, lifeStatus: String, deceasedAt: String?, version: Int)

    @Query("UPDATE persons SET birthPlace = :birthPlace, notes = :notes WHERE personId = :personId")
    suspend fun updateProfileLocally(personId: String, birthPlace: String?, notes: String?)

    @Query("UPDATE persons SET birthPlace = :birthPlace, notes = :notes, version = :version WHERE personId = :personId")
    suspend fun applySyncedProfile(personId: String, birthPlace: String?, notes: String?, version: Int)

    @Transaction
    suspend fun replaceSpace(spaceId: String, items: List<PersonEntity>) {
        deleteBySpace(spaceId)
        upsertAll(items)
    }
}
