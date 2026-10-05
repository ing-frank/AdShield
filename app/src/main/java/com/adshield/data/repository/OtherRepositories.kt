package com.adshield.data.repository

import android.content.Context
import android.content.Intent
import com.adshield.data.database.ApplicationRuleDao
import com.adshield.data.database.ApplicationRuleEntity
import com.adshield.data.database.StatisticsDao
import com.adshield.data.database.TrafficEventDao
import com.adshield.domain.model.BlockCategory
import com.adshield.domain.model.InstalledApp
import com.adshield.domain.model.StatsBucket
import com.adshield.domain.model.TrafficEvent
import com.adshield.domain.repository.AppRuleRepository
import com.adshield.domain.repository.TrafficRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TrafficRepositoryImpl(
    private val statisticsDao: StatisticsDao,
    private val eventDao: TrafficEventDao
) : TrafficRepository {

    override fun observeStatsSince(from: Long): Flow<List<StatsBucket>> =
        statisticsDao.observeSince(from).map { rows ->
            rows.map { StatsBucket(it.hourStart, it.allowed, it.ads, it.trackers, it.threats, it.custom) }
        }

    override fun observeRecentEvents(limit: Int): Flow<List<TrafficEvent>> =
        eventDao.observeRecent(limit).map { rows ->
            rows.map {
                TrafficEvent(it.id, it.timestamp, it.domain, it.blocked, BlockCategory.fromName(it.category), it.appPackage)
            }
        }

    override suspend fun clear() {
        statisticsDao.clear()
        eventDao.clear()
    }
}

class AppRuleRepositoryImpl(
    context: Context,
    private val dao: ApplicationRuleDao
) : AppRuleRepository {

    private val appContext = context.applicationContext
    private val labels = ConcurrentHashMap<String, String>()

    override fun observeExcludedPackages(): Flow<Set<String>> = dao.observeExcluded().map { it.toSet() }

    override suspend fun excludedPackages(): List<String> = dao.excludedPackages()

    override suspend fun setExcluded(packageName: String, excluded: Boolean) {
        dao.upsert(ApplicationRuleEntity(packageName, excluded, System.currentTimeMillis()))
    }

    /**
     * Aplicaciones con icono en el lanzador. Android 11+ limita la visibilidad de paquetes;
     * el manifiesto declara la consulta MAIN/LAUNCHER, que es la forma permitida de obtenerlas.
     */
    override suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != appContext.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                val label = info.applicationInfo.loadLabel(pm).toString()
                labels[info.packageName] = label
                InstalledApp(info.packageName, label)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
