package com.adshield.presentation.screens.protection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.model.BlocklistSourceStatus
import com.adshield.domain.model.ProtectionMode
import com.adshield.presentation.components.BackTopBar
import com.adshield.presentation.components.MaximumModeDialog
import com.adshield.presentation.viewmodel.ProtectionUiState
import com.adshield.presentation.viewmodel.ProtectionViewModel
import com.adshield.presentation.viewmodel.appViewModel
import com.adshield.util.Formatters

@Composable
fun ProtectionScreen(
    onBack: () -> Unit,
    viewModel: ProtectionViewModel = appViewModel {
        ProtectionViewModel(it.settingsRepository, it.blocklistRepository, it.ruleLoader.counts, it.setProtectionMode, it.appScope)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmMaximum by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { BackTopBar(title = "Protección", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Modo de protección", style = MaterialTheme.typography.titleMedium)
            ProtectionMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    selected = state.mode == mode,
                    onSelect = {
                        if (mode == ProtectionMode.MAXIMUM && state.mode != ProtectionMode.MAXIMUM) {
                            confirmMaximum = true
                        } else {
                            viewModel.selectMode(mode)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Listas de bloqueo", style = MaterialTheme.typography.titleMedium)
            UpdateCard(state = state, onUpdate = viewModel::updateNow)

            state.sources.forEach { source -> SourceRow(source, state.mode) }

            Text(
                text = "Las listas las mantienen terceros y cada una tiene su propia licencia. " +
                    "Al subir de modo se descargan las listas adicionales cuando haya conexión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmMaximum) {
        MaximumModeDialog(
            onConfirm = {
                viewModel.selectMode(ProtectionMode.MAXIMUM)
                confirmMaximum = false
            },
            onDismiss = { confirmMaximum = false }
        )
    }
}

@Composable
private fun ModeCard(mode: ProtectionMode, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(mode.label.uppercase(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpdateCard(state: ProtectionUiState, onUpdate: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Reglas cargadas: ${Formatters.count(state.counts.lists.toLong())}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Última actualización: " + (state.lastUpdate?.let(Formatters::dateTime) ?: "nunca"),
                style = MaterialTheme.typography.bodyMedium
            )
            val failed = state.sources.count { it.lastError != null }
            Text(
                text = "Estado: " + when {
                    state.updating -> "Actualizando…"
                    state.lastUpdate == null -> "Solo la lista mínima incluida en la app"
                    failed > 0 -> "Actualizado, con $failed fuente(s) con error"
                    else -> "Actualizado"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onUpdate, enabled = !state.updating, modifier = Modifier.fillMaxWidth()) {
                if (state.updating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("ACTUALIZAR AHORA", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SourceRow(status: BlocklistSourceStatus, mode: ProtectionMode) {
    val source = status.source
    val inUse = source.level <= mode.level
    val detail = when {
        !inUse -> "No se usa en el modo actual"
        status.lastError != null && status.lastSuccessAt == null -> "Error: ${status.lastError}"
        status.lastError != null -> "${Formatters.count(status.domainCount.toLong())} dominios · último intento falló: ${status.lastError}"
        status.lastSuccessAt != null -> "${Formatters.count(status.domainCount.toLong())} dominios · ${Formatters.dateTime(status.lastSuccessAt)}"
        else -> "Pendiente de descargar"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(source.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${source.category.label} · desde modo ${ProtectionMode.entries.first { it.level == source.level }.label} · ${source.license}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (inUse && status.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
