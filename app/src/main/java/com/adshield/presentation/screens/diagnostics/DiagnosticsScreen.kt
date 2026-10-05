package com.adshield.presentation.screens.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.model.DiagnosticItem
import com.adshield.domain.model.DiagnosticStatus
import com.adshield.presentation.components.BackTopBar
import com.adshield.presentation.viewmodel.DiagnosticsViewModel
import com.adshield.presentation.viewmodel.appViewModel

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = appViewModel { DiagnosticsViewModel(it.runDiagnostics) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar(title = "Diagnóstico", onBack = onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = viewModel::execute,
                enabled = !state.running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (state.running) "COMPROBANDO…" else "EJECUTAR DIAGNÓSTICO",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            state.items.forEach { item -> DiagnosticCard(item) }
            if (state.items.any { it.status != DiagnosticStatus.OK }) {
                Text(
                    text = "Si una aplicación no funciona con la protección activa, detén la protección " +
                        "desde Inicio o desde la notificación y exclúyela en Aplicaciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCard(item: DiagnosticItem) {
    val (icon, tint, description) = when (item.status) {
        DiagnosticStatus.OK -> Triple(Icons.Filled.Check, MaterialTheme.colorScheme.primary, "Correcto")
        DiagnosticStatus.WARNING -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.secondary, "Aviso")
        DiagnosticStatus.ERROR -> Triple(Icons.Filled.Close, MaterialTheme.colorScheme.error, "Problema detectado")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = description, tint = tint)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.detail, style = MaterialTheme.typography.bodyMedium)
                item.fix?.let { fix ->
                    Text(
                        text = "Solución: $fix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
