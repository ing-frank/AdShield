package com.adshield.stats

import android.util.Log
import com.adshield.data.database.StatisticsDao
import com.adshield.data.database.TrafficEventDao
import com.adshield.data.database.TrafficEventEntity
import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.FilterResult
import com.adshield.domain.repository.SettingsRepository
import com.adshield.domain.usecase.StatsAggregator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Acumula contadores y eventos en memoria y los escribe en Room por lotes cada pocos
 * segundos, para no hacer una escritura por cada consulta DNS.
 *
 * Solo registra: hora, dominio consultado, decisión, categoría y aplicación.
 */
class TrafficRecorder(
    private val statisticsDao: StatisticsDao,
    private val eventDao: TrafficEventDao,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private var allowed = 0L
    private var ads = 0L
    private var trackers = 0L
    private var threats = 0L
    private var custom = 0L
    private val pending = ArrayList<TrafficEventEntity>()
    private var job: Job? = null

    @Volatile
    private var saveStats = true

    private val _sessionBlocked = MutableStateFlow(0L)
    /** Bloqueos desde que se activó la protección; lo usa la notificación. */
    val sessionBlocked: StateFlow<Long> = _sessionBlocked.asStateFlow()

    fun start() {
        _sessionBlocked.value = 0
        job?.cancel()
        job = scope.launch {
            launch { settings.settings.collect { saveStats = it.saveStats } }
            try {
                statisticsDao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron limpiar estadísticas antiguas", e)
            }
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope.launch { flush() }
    }

    fun record(domain: String, result: FilterResult, appPackage: String?) {
        if (result.isBlocked) _sessionBlocked.update { it + 1 }
        if (!saveStats) return
        synchronized(lock) {
            if (!result.isBlocked) {
                allowed++
            } else when (result.category) {
                BlockCategory.ADVERTISING -> ads++
                BlockCategory.TRACKING, BlockCategory.TELEMETRY -> trackers++
                BlockCategory.MALWARE, BlockCategory.PHISHING -> threats++
                else -> custom++
            }
            // Las apps repiten consultas; no se guarda dos veces seguidas el mismo evento.
            val last = pending.lastOrNull()
            val repeated = last != null && last.domain == domain && last.blocked == result.isBlocked
            if (!repeated && pending.size < MAX_PENDING_EVENTS) {
                pending.add(
                    TrafficEventEntity(
                        timestamp = System.currentTimeMillis(),
                        domain = domain,
                        blocked = result.isBlocked,
                        category = result.category?.name,
                        appPackage = appPackage
                    )
                )
            }
        }
    }

    private class Batch(
        val allowed: Long, val ads: Long, val trackers: Long, val threats: Long, val custom: Long,
        val events: List<TrafficEventEntity>
    ) {
        val isEmpty: Boolean get() = allowed + ads + trackers + threats + custom == 0L && events.isEmpty()
    }

    private fun takeBatch(): Batch = synchronized(lock) {
        val batch = Batch(allowed, ads, trackers, threats, custom, ArrayList(pending))
        allowed = 0; ads = 0; trackers = 0; threats = 0; custom = 0
        pending.clear()
        batch
    }

    suspend fun flush() {
        val batch = takeBatch()
        if (batch.isEmpty) return
        try {
            statisticsDao.add(
                StatsAggregator.hourStart(System.currentTimeMillis()),
                batch.allowed, batch.ads, batch.trackers, batch.threats, batch.custom
            )
            if (batch.events.isNotEmpty()) {
                eventDao.insertAll(batch.events)
                eventDao.prune(MAX_STORED_EVENTS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron guardar las estadísticas", e)
        }
    }

    private companion object {
        const val TAG = "TrafficRecorder"
        const val FLUSH_INTERVAL_MS = 5_000L
        const val MAX_PENDING_EVENTS = 300
        const val MAX_STORED_EVENTS = 2_000
        const val RETENTION_MS = 32L * 24 * 3_600_000
    }
}
