package com.example.familytreeplatform.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.familytreeplatform.FamilyTreeApplication
import com.example.familytreeplatform.repository.SyncBatchResult

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as FamilyTreeApplication).container.personRepository
        return when (repository.syncPendingMutations()) {
            SyncBatchResult.COMPLETE -> Result.success()
            SyncBatchResult.RETRY -> Result.retry()
        }
    }
}

object OfflineSyncScheduler {
    private const val UNIQUE_WORK = "family-tree-offline-mutation-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
