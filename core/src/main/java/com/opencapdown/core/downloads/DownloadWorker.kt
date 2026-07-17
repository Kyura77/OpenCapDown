package com.opencapdown.core.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.opencapdown.core.OpenCapDownCoreRegistry

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val core = OpenCapDownCoreRegistry.core ?: return Result.retry()
        return try {
            core.processDownloadQueue()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
