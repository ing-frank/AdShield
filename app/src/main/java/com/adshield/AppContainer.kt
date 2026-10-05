package com.adshield

import android.content.Context
import android.util.Log
import com.adshield.data.database.AppDatabase
import com.adshield.data.preferences.SettingsRepositoryImpl
import com.adshield.data.repository.AppRuleRepositoryImpl
import com.adshield.data.repository.BlacklistRepositoryImpl
import com.adshield.data.repository.BlocklistRepositoryImpl
import com.adshield.data.repository.RuleLoader
import com.adshield.data.repository.TrafficRepositoryImpl
import com.adshield.data.repository.WhitelistRepositoryImpl
import com.adshield.data.system.AndroidSystemStatus
import com.adshield.domain.repository.AppRuleRepository
import com.adshield.domain.repository.BlocklistRepository
import com.adshield.domain.repository.DomainRuleRepository
import com.adshield.domain.repository.SettingsRepository
import com.adshield.domain.repository.TrafficRepository
import com.adshield.domain.usecase.RunDiagnosticsUseCase
import com.adshield.domain.usecase.SaveDomainRuleUseCase
import com.adshield.domain.usecase.SetProtectionModeUseCase
import com.adshield.filter.AdBlockEngine
import com.adshield.filter.RuleManager
import com.adshield.stats.TrafficRecorder
import com.adshield.vpn.VpnManager
import com.adshield.worker.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias manual. El proyecto es pequeño y un único proceso comparte
 * todo (interfaz, servicio VPN y workers), así que no hace falta un framework de inyección.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    val ruleManager = RuleManager()
    val engine = AdBlockEngine(ruleManager)

    val ruleLoader: RuleLoader by lazy {
        RuleLoader(database.blockedDomainDao(), database.allowedDomainDao(), ruleManager, engine)
    }

    val whitelistRepository: DomainRuleRepository by lazy {
        WhitelistRepositoryImpl(database.allowedDomainDao()) { ruleLoader.reloadUserLists() }
    }

    val blacklistRepository: DomainRuleRepository by lazy {
        BlacklistRepositoryImpl(database.blockedDomainDao()) { ruleLoader.reloadUserLists() }
    }

    val blocklistRepository: BlocklistRepository by lazy {
        BlocklistRepositoryImpl(appContext, database, settingsRepository, ruleLoader)
    }

    val trafficRepository: TrafficRepository by lazy {
        TrafficRepositoryImpl(database.statisticsDao(), database.trafficEventDao())
    }

    val appRuleRepository: AppRuleRepository by lazy {
        AppRuleRepositoryImpl(appContext, database.applicationRuleDao())
    }

    val trafficRecorder: TrafficRecorder by lazy {
        TrafficRecorder(database.statisticsDao(), database.trafficEventDao(), settingsRepository, appScope)
    }

    val vpnManager = VpnManager(appContext)

    val updateScheduler = UpdateScheduler(appContext)

    val saveWhitelistRule by lazy { SaveDomainRuleUseCase(whitelistRepository) }
    val saveBlacklistRule by lazy { SaveDomainRuleUseCase(blacklistRepository) }

    val setProtectionMode by lazy {
        SetProtectionModeUseCase(settingsRepository) { updateScheduler.updateWhenConnected() }
    }

    val runDiagnostics: RunDiagnosticsUseCase by lazy {
        RunDiagnosticsUseCase(
            system = AndroidSystemStatus(appContext, database.statisticsDao(), vpnManager),
            protectionState = { vpnManager.state.value.protection },
            protectionError = { vpnManager.state.value.error },
            ruleCounts = { ruleLoader.counts.value },
            sources = { blocklistRepository.currentSources() }
        )
    }

    /** Se llama una vez al arrancar la app. */
    fun start() {
        appScope.launch {
            try {
                val initial = settingsRepository.settings.first()
                ruleLoader.ensureLoaded(initial.mode)
                // Primer arranque: aún no se ha descargado ninguna lista.
                if (database.blocklistSourceDao().all().isEmpty()) updateScheduler.updateWhenConnected()
            } catch (e: Exception) {
                Log.e(TAG, "No se pudieron cargar las reglas", e)
            }
            settingsRepository.settings.map { it.mode }.distinctUntilChanged().collect { mode ->
                if (mode != engine.mode || !ruleManager.loaded) {
                    try {
                        ruleLoader.reloadBlocklists(mode)
                    } catch (e: Exception) {
                        Log.e(TAG, "No se pudo aplicar el modo $mode", e)
                    }
                }
            }
        }
        appScope.launch {
            settingsRepository.settings.map { it.updateFrequency }.distinctUntilChanged().collect { frequency ->
                try {
                    updateScheduler.apply(frequency)
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo programar la actualización", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
