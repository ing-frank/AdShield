package com.adshield.presentation.screens.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adshield.domain.model.DomainRule
import com.adshield.presentation.components.BackTopBar
import com.adshield.presentation.components.ConfirmDialog
import com.adshield.presentation.viewmodel.DomainListViewModel

/** Pantalla común de Lista blanca y Lista negra. */
@Composable
fun DomainListScreen(
    title: String,
    description: String,
    placeholder: String,
    viewModel: DomainListViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formError by viewModel.formError.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DomainRule?>(null) }
    var deleting by remember { mutableStateOf<DomainRule?>(null) }

    Scaffold(
        topBar = { BackTopBar(title = title, onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    viewModel.clearFormError()
                    showForm = true
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar dominio")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Buscar dominio") }
                )
            }
            if (state.rules.isEmpty()) {
                item {
                    Text(
                        text = if (state.total == 0) {
                            "La lista está vacía. Pulsa + para agregar un dominio."
                        } else {
                            "Ningún dominio coincide con la búsqueda."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(state.rules, key = { it.id }) { rule ->
                DomainRow(
                    rule = rule,
                    onToggle = { viewModel.setEnabled(rule, it) },
                    onEdit = {
                        editing = rule
                        viewModel.clearFormError()
                        showForm = true
                    },
                    onDelete = { deleting = rule }
                )
            }
        }
    }

    if (showForm) {
        DomainFormDialog(
            initial = editing?.domain.orEmpty(),
            isEditing = editing != null,
            placeholder = placeholder,
            error = formError,
            onSave = { text -> viewModel.save(text, editing) { showForm = false } },
            onDismiss = { showForm = false }
        )
    }

    deleting?.let { rule ->
        ConfirmDialog(
            title = "Eliminar dominio",
            text = "¿Eliminar «${rule.domain}» de la lista?",
            confirmLabel = "Eliminar",
            onConfirm = {
                viewModel.delete(rule)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun DomainRow(
    rule: DomainRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = rule.domain, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (rule.enabled) "Activo" else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar ${rule.domain}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${rule.domain}")
            }
        }
    }
}

@Composable
private fun DomainFormDialog(
    initial: String,
    isEditing: Boolean,
    placeholder: String,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Editar dominio" else "Agregar dominio") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = error != null,
                placeholder = { Text(placeholder) },
                supportingText = { Text(error ?: "También cubre los subdominios.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) })
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
