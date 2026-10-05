package com.adshield.presentation.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adshield.domain.model.ProtectionState
import com.adshield.domain.model.StatsTotals
import com.adshield.domain.repository.TrafficRepository
import com.adshield.domain.usecase.StatsAggregator
import com.adshield.vpn.VpnManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class DashboardUiState(
    val protectionState: ProtectionState = ProtectionState.DISABLED,
    val error: String? = null,
    val today: StatsTotals = StatsTotals(),
    val message: String? = null
)

class DashboardViewModel(
    private val vpnManager: VpnManager,
    trafficRepository: TrafficRepository
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        vpnManager.state,
        trafficRepository.observeStatsSince(
            StatsAggregator.startOfToday(System.currentTimeMillis(), ZoneId.systemDefault())
        ),
        message
    ) { vpn, buckets, msg ->
        DashboardUiState(vpn.protection, vpn.error, StatsTotals.of(buckets), msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Intent del permiso de VPN, o null si ya está concedido. */
    fun permissionIntent(): Intent? = try {
        vpnManager.permissionIntent()
    } catch (e: Exception) {
        message.value = "Android no permitió preparar la VPN: ${e.message}"
        null
    }

    fun start() {
        if (!vpnManager.start()) message.value = "No se pudo iniciar el servicio de protección."
    }

    fun stop() = vpnManager.stop()

    fun onVpnPermissionDenied() {
        message.value = "Sin el permiso de VPN no se puede activar la protección."
    }

    fun messageShown() {
        message.value = null
    }
}
