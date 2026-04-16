package com.guardian.track.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.guardian.track.repository.IncidentRepository
import com.guardian.track.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

data class DashboardUiState(
    val incidentCount: Int = 0,
    val serviceRunning: Boolean = true,
    val lastIncidentType: String = "None"
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val fusedLocation: FusedLocationProviderClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            incidentRepository.getAllIncidents().collect { incidents ->
                _uiState.update { current ->
                    current.copy(
                        incidentCount = incidents.size,
                        lastIncidentType = incidents.firstOrNull()?.type ?: "None"
                    )
                }
            }
        }
    }

    @Suppress("MissingPermission")
    fun triggerManualAlert() {
        viewModelScope.launch {
            try {
                val location = fusedLocation.lastLocation.await()
                incidentRepository.saveAndSync(
                    type = "MANUAL",
                    latitude = location?.latitude ?: 0.0,
                    longitude = location?.longitude ?: 0.0
                )
            } catch (e: Exception) {
                incidentRepository.saveAndSync("MANUAL", 0.0, 0.0)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Composable Screen
// ─────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Guardian Track",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Service Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.serviceRunning) 
                    Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = if (state.serviceRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = statusColor
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (state.serviceRunning) "Monitoring Active" else "Service Stopped",
                    fontWeight = FontWeight.Medium,
                    color=Color.Black,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Incidents", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = state.incidentCount.toString(),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last Type", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = state.lastIncidentType,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Big Red Button
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.triggerManualAlert()
                    NotificationHelper.showIncidentNotification(
                        context, "Manual Alert", "Alert sent to emergency contact."
                    )
                }
            },
            modifier = Modifier.size(200.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(
                "⚠\uFE0F",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Tap for manual alert",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
