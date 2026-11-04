package com.leafguard.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leafguard.app.R
import com.leafguard.app.data.local.Plant
import com.leafguard.app.ui.components.LeafScaffold
import com.leafguard.app.ui.plants.PlantDetailViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Plant detail. Real from Phase 3: shows the stored plant, supports editing name/species/notes,
 * offers a "Scan this plant" entry point, and deletes the plant (with confirm).
 */
@Composable
fun PlantDetailScreen(
    plantId: Long,
    onScanForPlant: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PlantDetailViewModel = hiltViewModel(),
) {
    val plant by viewModel.plant.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LeafScaffold(
        title = plant?.name ?: stringResource(R.string.plant_detail_title),
        onBack = onBack,
    ) { padding ->
        when {
            plant == null && plantId > 0L -> NotFoundState(Modifier.fillMaxSize().padding(padding))
            plantId <= 0L -> NotFoundState(Modifier.fillMaxSize().padding(padding))
            else -> {
                val p = plant!!
                if (editing) {
                    EditPlantContent(
                        plant = p,
                        busy = busy,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onCancel = { editing = false },
                        onSave = { name, species, notes ->
                            viewModel.update(name, species, notes)
                            editing = false
                        },
                    )
                } else {
                    PlantDetailContent(
                        plant = p,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onScan = { onScanForPlant(p.id) },
                        onEdit = { editing = true },
                        onDelete = { showDeleteConfirm = true },
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.plant_detail_delete_title)) },
            text = { Text(stringResource(R.string.plant_detail_delete_body, plant?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.plants_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlantDetailContent(
    plant: Plant,
    modifier: Modifier,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = plant.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(
                    label = stringResource(R.string.plants_field_species),
                    value = plant.species.ifBlank { stringResource(R.string.plant_detail_unspecified) },
                )
                DetailRow(
                    label = stringResource(R.string.plant_detail_added),
                    value = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(plant.createdAt)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.plants_field_notes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = plant.notes.ifBlank { stringResource(R.string.plant_detail_no_notes) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(stringResource(R.string.plant_detail_scan))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Text(stringResource(R.string.action_edit), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.plants_delete))
            }
        }
    }
}

@Composable
private fun EditPlantContent(
    plant: Plant,
    busy: Boolean,
    modifier: Modifier,
    onCancel: () -> Unit,
    onSave: (name: String, species: String, notes: String) -> Unit,
) {
    var name by remember { mutableStateOf(plant.name) }
    var species by remember { mutableStateOf(plant.species) }
    var notes by remember { mutableStateOf(plant.notes) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.plants_field_name)) },
            singleLine = true,
            isError = name.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = species,
            onValueChange = { species = it },
            label = { Text(stringResource(R.string.plants_field_species)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.plants_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = { onSave(name, species, notes) },
                enabled = !busy && name.isNotBlank(),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NotFoundState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.plant_detail_not_found),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
