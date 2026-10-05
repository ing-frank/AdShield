package com.adshield.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adshield.AdShieldApp
import com.adshield.domain.model.UpdateFrequency
import java.util.concurrent.TimeUnit

/** Descarga las listas de bloqueo en segundo plano. */
class BlocklistUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AdShieldApp).container
        val summary = container.blocklistRepository.updateAll()
        return when {
            summary.alreadyRunning || summary.updated > 0 -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}

/** Programa las actualizaciones según la frecuencia elegida en Configuración. */
class UpdateScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager get() = WorkManager.getInstance(appContext)

    fun apply(frequency: UpdateFrequency) {
        if (frequency == UpdateFrequency.MANUAL) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val constraints = if (frequency == UpdateFrequency.AUTOMATIC) {
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
        } else {
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        }
        val days = if (frequency == UpdateFrequency.WEEKLY) 7L else 1L
        val request = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Una descarga en cuanto haya conexión (primer arranque o al subir de modo). */
    fun updateWhenConnected() {
        val request = OneTimeWorkRequestBuilder<BlocklistUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC_WORK = "blocklist_periodic_update"
        const val ONE_TIME_WORK = "blocklist_one_time_update"
    }
}
