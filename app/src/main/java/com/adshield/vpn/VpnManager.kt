package com.adshield.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.adshield.domain.model.ProtectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Punto único desde el que la interfaz controla la VPN. La interfaz nunca toca
 * el servicio directamente: solo observa [state] y llama a [start] / [stop].
 */
class VpnManager(context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(VpnState())
    val state: StateFlow<VpnState> = _state.asStateFlow()

    /**
     * Devuelve el Intent con el que Android pide el permiso de VPN al usuario,
     * o null si el permiso ya está concedido.
     */
    fun permissionIntent(): Intent? = VpnService.prepare(appContext)

    fun start(): Boolean = try {
        val intent = Intent(appContext, AdShieldVpnService::class.java)
            .setAction(AdShieldVpnService.ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
        true
    } catch (e: Exception) {
        update(VpnState(ProtectionState.ERROR, "No se pudo iniciar el servicio: ${e.message}"))
        false
    }

    /** Detiene la protección de inmediato y devuelve el teléfono a su conexión normal. */
    fun stop() {
        val service = AdShieldVpnService.instance
        if (service != null) service.stopProtection() else update(VpnState())
    }

    /** Vuelve a crear el túnel (por ejemplo, tras cambiar las apps excluidas). */
    fun reconfigureIfActive() {
        AdShieldVpnService.instance?.reconfigure()
    }

    fun isServiceRunning(): Boolean = AdShieldVpnService.instance?.protectionRunning == true

    internal fun update(newState: VpnState) {
        _state.value = newState
    }
}
