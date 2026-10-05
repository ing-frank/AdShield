package com.adshield.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adshield.domain.model.AppSettings
import com.adshield.domain.model.BlocklistSourceStatus
import com.adshield.domain.model.DiagnosticItem
import com.adshield.domain.model.InstalledApp
import com.adshield.domain.model.ProtectionMode
import com.adshield.domain.model.ProtectionState
import com.adshield.domain.model.RuleCounts
import com.adshield.domain.model.ThemeMode
import com.adshield.domain.model.TrafficEvent
import com.adshield.domain.model.UpdateFrequency
import com.adshield.domain.repository.AppRuleRepository
import com.adshield.domain.repository.BlocklistRepository
import com.adshield.domain.repository.SettingsRepository
import com.adshield.domain.repository.TrafficRepository
import com.adshield.domain.usecase.RunDiagnosticsUseCase
import com.adshield.domain.usecase.SetProtectionModeUseCase
import com.adshield.vpn.VpnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Aplicaciones

data class AppRow(val app: InstalledApp, val isProtected: Boolean)

data class ApplicationsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<AppRow> = emptyList(),
    val protectionActive: Boolean = false
)

class ApplicationsViewModel(
    private val appRules: AppRuleRepository,
    private val vpnManager: VpnManager
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            installed.value = try {
                appRules.installedApps()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val uiState: StateFlow<ApplicationsUiState> = combine(
        installed, appRules.observeExcludedPackages(), query, vpnManager.state
    ) { apps, excluded, text, vpn ->
        val needle = text.trim().lowercase()
        ApplicationsUiState(
            loading = apps == null,
            query = text,
            apps = (apps ?: emptyList())
                .filter { needle.isEmpty() || it.label.lowercase().contains(needle) || it.packageName.contains(needle) }
                .map { AppRow(it, it.packageName !in excluded) },
            protectionActive = vpn.protection == ProtectionState.ACTIVE
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicationsUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun setProtected(packageName: String, isProtected: Boolean) {
        viewModelScope.launch {
            appRules.setExcluded(packageName, !isProtected)
            // La lista de apps excluidas se fija al crear el túnel: hay que recrearlo.
            vpnManager.reconfigureIfActive()
        }
    }
}

// ------------------------------------------------------------------ Actividad

data class ActivityRow(val event: TrafficEvent, val appLabel: String?)

class ActivityViewModel(
    private val trafficRepository: TrafficRepository,
    private val appRules: AppRuleRepository
) : ViewModel() {

    val events: StateFlow<List<ActivityRow>> = trafficRepository.observeRecentEvents(MAX_EVENTS)
        .map { list -> list.map { ActivityRow(it, it.appPackage?.let(appRules::labelOf)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { trafficRepository.clear() }
    }

    private companion object {
        const val MAX_EVENTS = 300
    }
}

// ------------------------------------------------------------------ Protección

data class ProtectionUiState(
    val mode: ProtectionMode = ProtectionMode.NORMAL,
    val counts: RuleCounts = RuleCounts(),
    val sources: List<BlocklistSourceStatus> = emptyList(),
    val updating: Boolean = false,
    val message: String? = null
) {
    val lastUpdate: Long? get() = sources.mapNotNull { it.lastSuccessAt }.maxOrNull()
}

class ProtectionViewModel(
    settings: SettingsRepository,
    private val blocklists: BlocklistRepository,
    ruleCounts: StateFlow<RuleCounts>,
    private val setMode: SetProtectionModeUseCase,
    /** Ámbito de la aplicación: la descarga continúa aunque se cierre la pantalla. */
    private val appScope: CoroutineScope
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProtectionUiState> = combine(
        settings.settings.map { it.mode }, ruleCounts, blocklists.observeSources(), blocklists.updating, message
    ) { mode, counts, sources, updating, msg ->
        ProtectionUiState(mode, counts, sources, updating, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProtectionUiState())

    fun selectMode(mode: ProtectionMode) {
        viewModelScope.launch { setMode(mode) }
    }

    fun updateNow() {
        appScope.launch {
            val summary = try {
                blocklists.updateAll()
            } catch (e: Exception) {
                message.value = "Error al actualizar: ${e.message}"
                return@launch
            }
            message.value = when {
                summary.alreadyRunning -> "Ya hay una actualización en curso."
                summary.updated == 0 -> "No se pudo actualizar ninguna lista. Revisa tu conexión."
                summary.failed == 0 -> "Listas actualizadas: ${summary.updated}."
                else -> "Actualizadas: ${summary.updated}. Con error: ${summary.failed}."
            }
        }
    }

    fun messageShown() {
        message.value = null
    }
}

// ------------------------------------------------------------------ Configuración

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val trafficRepository: TrafficRepository,
    private val setMode: SetProtectionModeUseCase
) : ViewModel() {

    val uiState: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setStartOnBoot(enabled: Boolean) = edit { settings.setStartOnBoot(enabled) }
    fun setUpdateFrequency(frequency: UpdateFrequency) = edit { settings.setUpdateFrequency(frequency) }
    fun setNotificationCounter(enabled: Boolean) = edit { settings.setNotificationCounter(enabled) }
    fun setTheme(theme: ThemeMode) = edit { settings.setTheme(theme) }
    fun setSaveStats(enabled: Boolean) = edit { settings.setSaveStats(enabled) }
    fun setProtectionMode(mode: ProtectionMode) = edit { setMode(mode) }
    fun restoreDefaults() = edit { settings.reset() }
    fun clearHistory() = edit { trafficRepository.clear() }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

// ------------------------------------------------------------------ Diagnóstico

data class DiagnosticsUiState(val running: Boolean = false, val items: List<DiagnosticItem> = emptyList())

class DiagnosticsViewModel(private val runDiagnostics: RunDiagnosticsUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        execute()
    }

    fun execute() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(running = true)
        viewModelScope.launch {
            val items = try {
                runDiagnostics()
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = DiagnosticsUiState(running = false, items = items)
        }
    }
}
