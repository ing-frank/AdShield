package com.adshield.domain.usecase

import com.adshield.domain.model.ProtectionMode
import com.adshield.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Cambia el modo de protección. Al subir de nivel pide una descarga de listas,
 * porque los modos superiores usan listas que quizá aún no se han descargado.
 */
class SetProtectionModeUseCase(
    private val settings: SettingsRepository,
    private val requestListUpdate: () -> Unit
) {
    suspend operator fun invoke(mode: ProtectionMode) {
        val current = settings.settings.first().mode
        if (mode == current) return
        settings.setMode(mode)
        if (mode.level > current.level) requestListUpdate()
    }
}
