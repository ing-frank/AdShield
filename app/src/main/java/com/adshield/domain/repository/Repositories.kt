package com.adshield.domain.repository

import com.adshield.domain.model.AppSettings
import com.adshield.domain.model.BlocklistSourceStatus
import com.adshield.domain.model.DomainRule
import com.adshield.domain.model.InstalledApp
import com.adshield.domain.model.ProtectionMode
import com.adshield.domain.model.StatsBucket
import com.adshield.domain.model.ThemeMode
import com.adshield.domain.model.TrafficEvent
import com.adshield.domain.model.UpdateFrequency
import com.adshield.domain.model.UpdateSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Lista de dominios del usuario. Hay dos implementaciones: lista blanca y lista negra. */
interface DomainRuleRepository {
    fun observe(): Flow<List<DomainRule>>
    suspend fun exists(domain: String): Boolean
    suspend fun add(domain: String)
    suspend fun rename(id: Long, domain: String)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
}

interface BlocklistRepository {
    val updating: StateFlow<Boolean>
    fun observeSources(): Flow<List<BlocklistSourceStatus>>
    suspend fun currentSources(): List<BlocklistSourceStatus>
    suspend fun updateAll(): UpdateSummary
}

interface TrafficRepository {
    fun observeStatsSince(from: Long): Flow<List<StatsBucket>>
    fun observeRecentEvents(limit: Int): Flow<List<TrafficEvent>>
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setMode(mode: ProtectionMode)
    suspend fun setStartOnBoot(enabled: Boolean)
    suspend fun setUpdateFrequency(frequency: UpdateFrequency)
    suspend fun setNotificationCounter(enabled: Boolean)
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setSaveStats(enabled: Boolean)
    suspend fun reset()
}

interface AppRuleRepository {
    fun observeExcludedPackages(): Flow<Set<String>>
    suspend fun excludedPackages(): List<String>
    suspend fun setExcluded(packageName: String, excluded: Boolean)
    suspend fun installedApps(): List<InstalledApp>
    fun labelOf(packageName: String): String
}

/** Consultas al sistema que necesita el diagnóstico. */
interface SystemStatusProvider {
    fun internetAvailable(): Boolean
    fun vpnPermissionGranted(): Boolean
    /** Nombre del servidor de DNS privado en modo estricto, o null si no está activo. */
    fun strictPrivateDnsHost(): String?
    fun serviceRunning(): Boolean
    suspend fun databaseWorking(): Boolean
}
