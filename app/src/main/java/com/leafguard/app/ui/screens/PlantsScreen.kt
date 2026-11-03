package com.leafguard.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.leafguard.app.ui.plants.PlantsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * The user's plant list. Real from Phase 3: a live Room-backed list with an add dialog and
 * per-row delete. Tapping a row opens the plant detail.
 */
@Composable
fun PlantsScreen(
    onOpenPlant: (Long) -> Unit,
    viewModel: PlantsViewModel = hiltViewModel(),
) {
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.plantError.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    LeafScaffold(title = stringResource(R.string.nav_plants)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (plants.isEmpty()) {
                EmptyPlantsState(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                PlantsList(
                    plants = plants,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    onOpenPlant = onOpenPlant,
                    onDelete = viewModel::deletePlant,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ExtendedFloatingActionButton(
                onClick = { showAdd = true; viewModel.clearError() },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.plants_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }

    if (showAdd) {
        AddPlantDialog(
            busy = busy,
            nameError = error == PlantsViewModel.PlantsError.NAME_BLANK,
            onDismiss = { showAdd = false; viewModel.clearError() },
            onConfirm = { name, species, notes ->
                viewModel.addPlant(name, species, notes)
                // Close only when not blank; the VM sets the error and we keep the dialog open.
                if (name.isNotBlank()) showAdd = false
            },
        )
    }
}

@Composable
private fun EmptyPlantsState(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Spa,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.plants_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.plants_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PlantsList(
    plants: List<Plant>,
    contentPadding: PaddingValues,
    onOpenPlant: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = plants, key = { it.id }) { plant ->
            PlantRow(plant = plant, onOpen = { onOpenPlant(plant.id) }, onDelete = { onDelete(plant.id) })
        }
    }
}

@Composable
private fun PlantRow(plant: Plant, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plant.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (plant.species.isNotBlank()) {
                    Text(
                        text = plant.species,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.plants_added_on, formatCreatedDate(plant.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.plants_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddPlantDialog(
    busy: Boolean,
    nameError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, species: String, notes: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plants_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.plants_field_name)) },
                    singleLine = true,
                    isError = nameError,
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
                    minLines = 2,
                )
                if (nameError) {
                    Text(
                        text = stringResource(R.string.plants_error_name_blank),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, species, notes) },
                enabled = !busy,
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun formatCreatedDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
