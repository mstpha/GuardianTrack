package com.guardian.track.ui.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.track.model.Incident
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.util.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository
) : ViewModel() {

    val incidents = incidentRepository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteIncident(id: Long) {
        viewModelScope.launch { incidentRepository.deleteIncident(id) }
    }

    suspend fun getAllForExport() = incidentRepository.getAllForExport()
}

// ─────────────────────────────────────────────
//  Composable Screen
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val incidents by viewModel.incidents.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "History", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = {
                scope.launch {
                    val allIncidents = viewModel.getAllForExport()
                    val success = CsvExporter.export(context, allIncidents)
                    Toast.makeText(
                        context,
                        if (success) "Exported to Documents/" else "Export failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Text("Export CSV")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (incidents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No incidents recorded")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(incidents, key = { it.id }) { incident ->
                    val dismissState = rememberDismissState(
                        confirmStateChange = { state ->
                            if (state == DismissValue.DismissedToEnd || state == DismissValue.DismissedToStart) {
                                viewModel.deleteIncident(incident.id)
                                true
                            } else {
                                false
                            }
                        }
                    )


                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                        background = { /* optional red background + icon */ },
                        dismissContent = {
                            IncidentItem(
                                incident = incident,
                                onDelete = { viewModel.deleteIncident(incident.id) }
                            )
                        }
                    )
                }

            }
        }
    }
}

@Composable
fun IncidentItem(
    incident: Incident,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${incident.formattedDate} ${incident.formattedTime}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = incident.type, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (incident.latitude == 0.0 && incident.longitude == 0.0)
                        "Location unavailable"
                    else
                        "Location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val uri = Uri.parse("https://maps.google.com/?q=${incident.latitude},${incident.longitude}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
                Text(

                    text = if (incident.isSynced) "✓ Synced" else "⏳ Pending",
                    color = if (incident.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
