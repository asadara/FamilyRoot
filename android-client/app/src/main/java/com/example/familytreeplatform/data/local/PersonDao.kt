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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PersonEntity>)

    @Query("DELETE FROM persons WHERE spaceId = :spaceId")
    suspend fun deleteBySpace(spaceId: String)

    @Transaction
    suspend fun replaceSpace(spaceId: String, items: List<PersonEntity>) {
        deleteBySpace(spaceId)
        upsertAll(items)
    }
}
