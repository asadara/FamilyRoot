package com.example.familytreeplatform

import android.app.Application
import com.example.familytreeplatform.repository.PersonRepository
import androidx.room.Room
import com.example.familytreeplatform.data.local.FamilyTreeDatabase
import com.example.familytreeplatform.data.local.MIGRATION_1_2
import com.example.familytreeplatform.data.local.MIGRATION_2_3
import com.example.familytreeplatform.data.local.MIGRATION_3_4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FamilyTreeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SessionStore.initialize(this)
        val database = Room.databaseBuilder(this, FamilyTreeDatabase::class.java, "family-tree.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        container = AppContainer(
            database,
            PersonRepository(
                personDao = database.personDao(),
                mutationDao = database.offlineMutationDao(),
                relationshipDao = database.relationshipDao(),
                appContext = this
            )
        )
        applicationScope.launch {
            container.personRepository.restoreSession()
        }
    }
}

class AppContainer(
    val database: FamilyTreeDatabase,
    val personRepository: PersonRepository
)
