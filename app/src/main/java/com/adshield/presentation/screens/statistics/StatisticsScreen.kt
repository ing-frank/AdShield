package com.adshield.presentation.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.usecase.ChartBar
import com.adshield.domain.usecase.StatsRange
import com.adshield.presentation.components.BackTopBar
import com.adshield.presentation.components.StatCard
import com.adshield.presentation.viewmodel.StatisticsViewModel
import com.adshield.presentation.viewmodel.appViewModel
import com.adshield.util.Formatters

@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = appViewModel { StatisticsViewModel(it.trafficRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar(title = "Estadísticas", onBack = onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text("Últimos ${range.label}".replace("Últimos 24", "Últimas 24")) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Publicidad bloqueada", state.totals.ads, Modifier.weight(1f))
                StatCard("Rastreadores bloqueados", state.totals.trackers, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Amenazas bloqueadas", state.totals.threats, Modifier.weight(1f))
                StatCard("Lista negra propia", state.totals.custom, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total bloqueado", state.totals.blocked, Modifier.weight(1f))
                StatCard("Solicitudes permitidas", state.totals.allowed, Modifier.weight(1f))
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.range == StatsRange.DAY) "Bloqueos por hora" else "Bloqueos por día",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BlockedChart(bars = state.bars)
                }
            }

            Text(
                text = "Los contadores son consultas DNS. Una misma página o aplicación puede " +
                    "generar varias consultas, y las respuestas guardadas en caché no se cuentan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockedChart(bars: List<ChartBar>) {
    val max = bars.maxOfOrNull { it.blocked } ?: 0L
    if (bars.isEmpty() || max == 0L) {
        Text(
            text = "Todavía no hay bloqueos en este periodo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val barColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.outline

    Text(
        text = "Máximo: ${Formatters.count(max)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        val baseline = 1.dp.toPx()
        bars.forEachIndexed { index, bar ->
            val x = index * (barWidth + gap)
            drawRect(color = baseColor, topLeft = Offset(x, size.height - baseline), size = Size(barWidth, baseline))
            if (bar.blocked > 0) {
                val barHeight = (size.height * (bar.blocked.toFloat() / max.toFloat())).coerceAtLeast(baseline * 2)
                drawRect(color = barColor, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight))
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(bars.first(), bars[bars.size / 2], bars.last()).forEach { bar ->
            Text(
                text = bar.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
