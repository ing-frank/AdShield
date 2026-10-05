package com.adshield.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adshield.AdShieldApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Activa la protección al encender el teléfono si el usuario lo eligió en Configuración. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as AdShieldApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settingsRepository.settings.first()
                // Solo si el permiso de VPN ya fue concedido; desde aquí no se puede pedir.
                if (settings.startOnBoot && container.vpnManager.permissionIntent() == null) {
                    container.vpnManager.start()
                }
            } catch (e: Exception) {
                Log.w("BootReceiver", "No se pudo iniciar la protección al arrancar", e)
            } finally {
                pending.finish()
            }
        }
    }
}
