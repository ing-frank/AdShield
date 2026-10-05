package com.adshield.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.model.ProtectionMode
import com.adshield.domain.model.ThemeMode
import com.adshield.domain.model.UpdateFrequency
import com.adshield.presentation.components.BackTopBar
import com.adshield.presentation.components.ConfirmDialog
import com.adshield.presentation.components.MaximumModeDialog
import com.adshield.presentation.viewmodel.SettingsViewModel
import com.adshield.presentation.viewmodel.appViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = appViewModel {
        SettingsViewModel(it.settingsRepository, it.trafficRepository, it.setProtectionMode)
    }
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmMaximum by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(title = "Configuración", onBack = onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwitchSetting(
                title = "Protección al iniciar",
                description = "Activa la protección al encender el teléfono. Requiere haber concedido antes el permiso de VPN.",
                checked = settings.startOnBoot,
                onChange = viewModel::setStartOnBoot
            )
            SwitchSetting(
                title = "Notificaciones",
                description = "Muestra el contador de bloqueos en la notificación persistente. La notificación en sí es obligatoria mientras la protección está activa.",
                checked = settings.notificationCounter,
                onChange = viewModel::setNotificationCounter
            )
            SwitchSetting(
                title = "Guardar estadísticas",
                description = "Si lo desactivas, no se registran contadores ni actividad.",
                checked = settings.saveStats,
                onChange = viewModel::setSaveStats
            )

            ChoiceSetting(
                title = "Actualización automática de listas",
                description = settings.updateFrequency.description,
                options = UpdateFrequency.entries,
                selected = settings.updateFrequency,
                label = { it.label },
                onSelect = viewModel::setUpdateFrequency
            )
            ChoiceSetting(
                title = "Modo de protección",
                description = settings.mode.description,
                options = ProtectionMode.entries,
                selected = settings.mode,
                label = { it.label },
                onSelect = { mode ->
                    if (mode == ProtectionMode.MAXIMUM && settings.mode != ProtectionMode.MAXIMUM) {
                        confirmMaximum = true
                    } else {
                        viewModel.setProtectionMode(mode)
                    }
                }
            )
            ChoiceSetting(
                title = "Tema",
                description = null,
                options = ThemeMode.entries,
                selected = settings.theme,
                label = { it.label },
                onSelect = viewModel::setTheme
            )

            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurar configuración")
            }
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Borrar estadísticas y actividad")
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restaurar configuración",
            text = "Todos los ajustes volverán a sus valores iniciales. Las listas blanca y negra no se modifican.",
            confirmLabel = "Restaurar",
            onConfirm = {
                viewModel.restoreDefaults()
                confirmRestore = false
            },
            onDismiss = { confirmRestore = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Borrar estadísticas y actividad",
            text = "Se borrarán todos los contadores y el registro de actividad. Esta acción no se puede deshacer.",
            confirmLabel = "Borrar",
            onConfirm = {
                viewModel.clearHistory()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
    if (confirmMaximum) {
        MaximumModeDialog(
            onConfirm = {
                viewModel.setProtectionMode(ProtectionMode.MAXIMUM)
                confirmMaximum = false
            },
            onDismiss = { confirmMaximum = false }
        )
    }
}

@Composable
private fun SwitchSetting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSetting(
    title: String,
    description: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(label(option)) }
                    )
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
