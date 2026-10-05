package com.adshield.presentation.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.presentation.components.BackTopBar
import com.adshield.presentation.components.ConfirmDialog
import com.adshield.presentation.viewmodel.ActivityRow
import com.adshield.presentation.viewmodel.ActivityViewModel
import com.adshield.presentation.viewmodel.appViewModel
import com.adshield.util.Formatters

@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    viewModel: ActivityViewModel = appViewModel { ActivityViewModel(it.trafficRepository, it.appRuleRepository) }
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(title = "Actividad", onBack = onBack) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Solo se guarda la hora, el dominio consultado, la decisión y la aplicación. " +
                        "Nunca el contenido de páginas, mensajes ni contraseñas. Se conservan los últimos 2.000 eventos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        text = "Sin actividad registrada. Activa la protección y usa el teléfono unos segundos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                item {
                    OutlinedButton(onClick = { confirmClear = true }) { Text("Borrar historial") }
                }
            }
            items(events, key = { it.event.id }) { row -> EventCard(row) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Borrar historial",
            text = "Se borrarán la actividad y todas las estadísticas guardadas. Esta acción no se puede deshacer.",
            confirmLabel = "Borrar",
            onConfirm = {
                viewModel.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun EventCard(row: ActivityRow) {
    val event = row.event
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = Formatters.eventTime(event.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (event.blocked) "BLOQUEADO" else "PERMITIDO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (event.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Text(text = event.domain, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Categoría: ${event.category?.label ?: "Normal"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Aplicación: ${row.appLabel ?: "No identificada"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
