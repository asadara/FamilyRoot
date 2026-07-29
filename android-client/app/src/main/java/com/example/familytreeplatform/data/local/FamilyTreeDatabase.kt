package com.example.familytreeplatform.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PersonEntity::class,
        OfflineMutationEntity::class,
        CachedRelationshipEntity::class,
        CachedSourceEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class FamilyTreeDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun offlineMutationDao(): OfflineMutationDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun sourceDao(): SourceDao
}
