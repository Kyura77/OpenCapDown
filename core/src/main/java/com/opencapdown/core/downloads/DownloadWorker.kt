package com.opencapdown.core.downloads

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

internal class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        // WorkManager background execution — to be implemented
        return Result.success()
    }
}
