package com.plushledger.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.plushledger.PlushLedgerApplication

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PlushLedgerApplication
        return runCatching {
            app.container.ledgerRepository.syncNow()
            Result.success()
        }.getOrDefault(Result.retry())
    }
}
