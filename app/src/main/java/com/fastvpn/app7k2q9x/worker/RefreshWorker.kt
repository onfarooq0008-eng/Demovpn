package com.fastvpn.app7k2q9x.worker

import android.content.Context
import androidx.work.*
import com.fastvpn.app7k2q9x.data.ServerRepository
import java.util.concurrent.TimeUnit

/** Refreshes the server list only. It never keeps the tunnel alive. */
class RefreshWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result { ServerRepository.refresh(force = true); return Result.success() }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("server-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
