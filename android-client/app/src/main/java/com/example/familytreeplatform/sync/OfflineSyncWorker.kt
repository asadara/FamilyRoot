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
    private const val IMMEDIATE_WORK = "family-tree-offline-mutation-sync-immediate"

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest()
        )
    }

    /**
     * Wakes a persisted queue without waiting for the backoff of an older worker.
     *
     * This uses a separate unique chain so it cannot cancel a normal sync that may
     * already be committing an idempotent mutation. PersonRepository serializes
     * both workers before they touch the local queue.
     */
    fun enqueueImmediate(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            syncRequest()
        )
    }

    private fun syncRequest() =
        OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
}
