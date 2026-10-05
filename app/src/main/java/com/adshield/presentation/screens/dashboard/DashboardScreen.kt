package com.adshield.presentation.screens.dashboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.model.ProtectionState
import com.adshield.presentation.components.StatCard
import com.adshield.presentation.navigation.AppDestination
import com.adshield.presentation.viewmodel.DashboardUiState
import com.adshield.presentation.viewmodel.DashboardViewModel
import com.adshield.presentation.viewmodel.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (AppDestination) -> Unit,
    viewModel: DashboardViewModel = appViewModel { DashboardViewModel(it.vpnManager, it.trafficRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    // Paso 2: permiso de VPN (diálogo del sistema). Si ya está concedido se inicia directamente.
    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.start() else viewModel.onVpnPermissionDenied()
    }
    val requestVpn: () -> Unit = {
        val intent = viewModel.permissionIntent()
        if (intent != null) vpnPermission.launch(intent) else viewModel.start()
    }
    // Paso 1 (Android 13+): permiso de notificaciones. Si se rechaza, la protección funciona igual.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestVpn() }

    val onToggle: () -> Unit = {
        when (state.protectionState) {
            ProtectionState.ACTIVE, ProtectionState.STARTING -> viewModel.stop()
            ProtectionState.DISABLED, ProtectionState.ERROR -> {
                val needsNotificationPermission =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                if (needsNotificationPermission) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    requestVpn()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AD SHIELD", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ProtectionCard(state, onToggle) }
            item {
                Text(
                    text = "Hoy",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { StatsGrid(state) }
            item {
                Text(
                    text = "Secciones",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(AppDestination.menuItems, key = { it.route }) { destination ->
                SectionRow(destination = destination, onClick = { onNavigate(destination) })
            }
        }
    }
}

@Composable
private fun ProtectionCard(state: DashboardUiState, onToggle: () -> Unit) {
    val active = state.protectionState == ProtectionState.ACTIVE
    val (statusText, statusColor) = when (state.protectionState) {
        ProtectionState.ACTIVE -> "PROTECCIÓN ACTIVA" to MaterialTheme.colorScheme.primary
        ProtectionState.STARTING -> "INICIANDO…" to MaterialTheme.colorScheme.secondary
        ProtectionState.ERROR -> "ERROR DE PROTECCIÓN" to MaterialTheme.colorScheme.error
        ProtectionState.DISABLED -> "PROTECCIÓN DESACTIVADA" to MaterialTheme.colorScheme.error
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = statusColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = statusText, style = MaterialTheme.typography.titleMedium, color = statusColor)
            }
            if (state.protectionState == ProtectionState.ERROR && state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = if (active || state.protectionState == ProtectionState.STARTING) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = if (active || state.protectionState == ProtectionState.STARTING) {
                        "DETENER PROTECCIÓN"
                    } else {
                        "ACTIVAR PROTECCIÓN"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (active) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Si alguna aplicación deja de funcionar, detén la protección: " +
                        "el teléfono vuelve de inmediato a su conexión normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun StatsGrid(state: DashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Publicidad bloqueada", state.today.ads, Modifier.weight(1f))
            StatCard("Rastreadores bloqueados", state.today.trackers, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Amenazas bloqueadas", state.today.threats, Modifier.weight(1f))
            StatCard("Solicitudes permitidas", state.today.allowed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionRow(destination: AppDestination, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = destination.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = destination.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
