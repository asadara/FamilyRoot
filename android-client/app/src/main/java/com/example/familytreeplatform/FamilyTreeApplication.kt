package com.example.familytreeplatform

import android.app.Application
import com.example.familytreeplatform.repository.PersonRepository
import androidx.room.Room
import com.example.familytreeplatform.data.local.FamilyTreeDatabase

class FamilyTreeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SessionStore.initialize(this)
        val database = Room.databaseBuilder(this, FamilyTreeDatabase::class.java, "family-tree.db").build()
        container = AppContainer(database, PersonRepository(database.personDao()))
    }
}

class AppContainer(
    val database: FamilyTreeDatabase,
    val personRepository: PersonRepository
)
