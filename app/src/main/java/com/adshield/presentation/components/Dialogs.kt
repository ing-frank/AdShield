package com.adshield.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Aviso obligatorio antes de activar el modo Máximo. */
@Composable
fun MaximumModeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Modo máximo",
        text = "El modo máximo puede provocar que algunas aplicaciones o sitios web no funcionen correctamente.",
        confirmLabel = "Activar",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
