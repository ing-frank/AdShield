package com.adshield.domain.usecase

import com.adshield.domain.model.BlocklistSourceStatus
import com.adshield.domain.model.DiagnosticItem
import com.adshield.domain.model.DiagnosticStatus
import com.adshield.domain.model.ProtectionState
import com.adshield.domain.model.RuleCounts
import com.adshield.domain.repository.SystemStatusProvider

/**
 * Comprueba el estado real de cada componente. No simula nada: cada resultado sale
 * de una consulta al sistema, a la base de datos o al estado del servicio.
 */
class RunDiagnosticsUseCase(
    private val system: SystemStatusProvider,
    private val protectionState: () -> ProtectionState,
    private val protectionError: () -> String?,
    private val ruleCounts: () -> RuleCounts,
    private val sources: suspend () -> List<BlocklistSourceStatus>
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): List<DiagnosticItem> {
        val items = ArrayList<DiagnosticItem>()
        val state = protectionState()
        val serviceRunning = system.serviceRunning()

        items += when {
            state == ProtectionState.ACTIVE ->
                DiagnosticItem("VPN", DiagnosticStatus.OK, "Funcionando")
            state == ProtectionState.STARTING ->
                DiagnosticItem("VPN", DiagnosticStatus.WARNING, "Iniciando…", "Espera unos segundos y vuelve a ejecutar el diagnóstico.")
            state == ProtectionState.ERROR ->
                DiagnosticItem("VPN", DiagnosticStatus.ERROR, protectionError() ?: "Error desconocido",
                    "Vuelve a activar la protección desde Inicio. Si otra VPN está activa, desconéctala primero.")
            !system.vpnPermissionGranted() ->
                DiagnosticItem("VPN", DiagnosticStatus.WARNING, "Permiso de VPN no concedido",
                    "Pulsa «Activar protección» en Inicio y acepta el permiso de Android.")
            else ->
                DiagnosticItem("VPN", DiagnosticStatus.WARNING, "Protección desactivada", "Actívala desde Inicio.")
        }

        items += if (system.internetAvailable()) {
            DiagnosticItem("Internet", DiagnosticStatus.OK, "Conectado")
        } else {
            DiagnosticItem("Internet", DiagnosticStatus.ERROR, "Sin conexión",
                "Revisa el Wi‑Fi o los datos móviles. Si el problema solo ocurre con la protección activa, desactívala.")
        }

        val counts = ruleCounts()
        items += when {
            !counts.loaded -> DiagnosticItem("Motor de filtrado", DiagnosticStatus.ERROR, "Reglas no cargadas",
                "Cierra y vuelve a abrir la aplicación.")
            else -> DiagnosticItem("Motor de filtrado", DiagnosticStatus.OK,
                "Funcionando · ${counts.lists} reglas, ${counts.whitelist} en lista blanca, ${counts.blacklist} en lista negra")
        }

        val status = sources()
        val downloaded = status.filter { it.lastSuccessAt != null }
        val newest = downloaded.maxOfOrNull { it.lastSuccessAt ?: 0L }
        val failing = status.filter { it.lastError != null }
        items += when {
            counts.lists == 0 -> DiagnosticItem("Lista de bloqueo", DiagnosticStatus.ERROR, "Sin reglas cargadas",
                "Abre Protección y pulsa «Actualizar ahora».")
            downloaded.isEmpty() -> DiagnosticItem("Lista de bloqueo", DiagnosticStatus.WARNING,
                "Solo está la lista mínima incluida en la app", "Abre Protección y pulsa «Actualizar ahora» con conexión a Internet.")
            newest != null && now - newest > STALE_MS -> DiagnosticItem("Lista de bloqueo", DiagnosticStatus.WARNING,
                "Cargada, pero con más de 14 días sin actualizar", "Abre Protección y pulsa «Actualizar ahora».")
            failing.isNotEmpty() -> DiagnosticItem("Lista de bloqueo", DiagnosticStatus.WARNING,
                "Cargada · ${failing.size} fuente(s) fallaron en el último intento", "Revisa el detalle en Protección.")
            else -> DiagnosticItem("Lista de bloqueo", DiagnosticStatus.OK, "Cargada · ${downloaded.size} fuentes")
        }

        items += if (system.databaseWorking()) {
            DiagnosticItem("Base de datos", DiagnosticStatus.OK, "Funcionando")
        } else {
            DiagnosticItem("Base de datos", DiagnosticStatus.ERROR, "No responde",
                "Reinicia la aplicación. Si continúa, borra los datos de AD Shield en Ajustes de Android.")
        }

        items += when {
            serviceRunning && state == ProtectionState.ACTIVE ->
                DiagnosticItem("Servicio", DiagnosticStatus.OK, "Funcionando")
            !serviceRunning && state == ProtectionState.ACTIVE ->
                DiagnosticItem("Servicio", DiagnosticStatus.ERROR, "El servicio no está en ejecución",
                    "Desactiva y vuelve a activar la protección.")
            else -> DiagnosticItem("Servicio", DiagnosticStatus.WARNING, "Detenido", "Se inicia al activar la protección.")
        }

        system.strictPrivateDnsHost()?.let { host ->
            items += DiagnosticItem("DNS privado de Android", DiagnosticStatus.WARNING,
                "Activo ($host). Las consultas DNS van cifradas a ese servidor y no pasan por AD Shield.",
                "Ajustes → Red e Internet → DNS privado → «Desactivado» o «Automático».")
        }

        return items
    }

    private companion object {
        const val STALE_MS = 14L * 24 * 3_600_000
    }
}
