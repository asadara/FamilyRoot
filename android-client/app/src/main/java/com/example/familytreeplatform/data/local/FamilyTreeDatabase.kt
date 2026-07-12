package com.example.familytreeplatform.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PersonEntity::class], version = 1, exportSchema = true)
abstract class FamilyTreeDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
}
