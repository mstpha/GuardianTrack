package com.guardian.track.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.track.data.local.PreferencesManager
import com.guardian.track.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

data class SettingsUiState(
    val fallThreshold: Float = 15.0f,
    val darkMode: Boolean = false,
    val smsSimulationMode: Boolean = true,
    val emergencyNumber: String = ""
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsManager: PreferencesManager,
    private val secureStorage: SecureStorage
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsManager.fallThreshold,
        prefsManager.darkMode,
        prefsManager.smsSimulationMode,
        prefsManager.emergencyNumber
    ) { threshold, dark, simMode, phone ->
        SettingsUiState(
            fallThreshold = threshold,
            darkMode = dark,
            smsSimulationMode = simMode,
            emergencyNumber = phone
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setFallThreshold(value: Float) {
        viewModelScope.launch { prefsManager.setFallThreshold(value) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setDarkMode(enabled) }
    }

    fun setSmsSimulationMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSmsSimulationMode(enabled) }
    }

    fun setEmergencyNumber(number: String) {
        viewModelScope.launch {
            prefsManager.setEmergencyNumber(number)
            secureStorage.saveEmergencyNumber(number)
        }
    }
}

// ─────────────────────────────────────────────
//  Composable Screen
// ─────────────────────────────────────────────

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        // Fall Threshold
        Column {
            Text(text = "Fall Detection Threshold: ${state.fallThreshold.toInt()} m/s²")
            Slider(
                value = state.fallThreshold,
                onValueChange = { viewModel.setFallThreshold(it) },
                valueRange = 5f..30f,
                steps = 25
            )
        }

        // Dark Mode Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Dark Mode")
            Switch(
                checked = state.darkMode,
                onCheckedChange = { viewModel.setDarkMode(it) }
            )
        }

        // SMS Simulation Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "SMS Simulation Mode")
                Text(
                    text = "If enabled, SMS won't be sent, only logged.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = state.smsSimulationMode,
                onCheckedChange = { viewModel.setSmsSimulationMode(it) }
            )
        }

        // Emergency Number
        var tempNumber by remember(state.emergencyNumber) { mutableStateOf(state.emergencyNumber) }
        
        Column {
            Text(text = "Emergency Phone Number")
            OutlinedTextField(
                value = tempNumber,
                onValueChange = { tempNumber = it.filter { char -> char.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter number") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.setEmergencyNumber(tempNumber) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Number")
            }
        }
    }
}
