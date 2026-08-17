package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.wedding.WeddingViewModel
import kotlin.math.roundToInt

@Composable
fun WeddingSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    weddingViewModel: WeddingViewModel,
) {
    val settings = uiState.settings
    val weddingState by weddingViewModel.uiState.collectAsStateWithLifecycle()
    var csvDraft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Text("Couple and welcome", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    Text(
        "These details appear on the Wedding Mode welcome screen. Names support accents and international characters.",
        style = MDTheme.type.settingSubtitle,
        color = MDTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(18.dp))

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = settings.weddingPartnerOne,
            onValueChange = viewModel::setWeddingPartnerOne,
            label = { Text("Partner one") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.weddingPartnerTwo,
            onValueChange = viewModel::setWeddingPartnerTwo,
            label = { Text("Partner two") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.weddingWelcomeMessage,
            onValueChange = viewModel::setWeddingWelcomeMessage,
            label = { Text("Welcome message") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.weddingDateText,
            onValueChange = viewModel::setWeddingDateText,
            label = { Text("Wedding date") },
            placeholder = { Text("August 15, 2026") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.weddingLocation,
            onValueChange = viewModel::setWeddingLocation,
            label = { Text("Location") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(34.dp))
    Text("Guest session reset", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    Text(
        "After inactivity, Wedding Mode returns to Welcome and clears the previous guest's search or selection.",
        style = MDTheme.type.settingSubtitle,
        color = MDTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(14.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Slider(
            value = settings.weddingIdleTimeoutSeconds.toFloat(),
            onValueChange = { value ->
                val snapped = (value / 30f).roundToInt() * 30
                viewModel.setWeddingIdleTimeoutSeconds(snapped)
            },
            valueRange = 30f..900f,
            steps = 28,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatIdleTimeout(settings.weddingIdleTimeoutSeconds),
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.textPrimary,
        )
    }

    Spacer(Modifier.height(38.dp))
    Text("Guest seating", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    Text(
        "Paste a CSV guest list. Importing replaces the previous seating list and stores it locally for offline use.",
        style = MDTheme.type.settingSubtitle,
        color = MDTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "${weddingState.seating.data.guests.size} guests | ${weddingState.seating.data.tables.size} tables",
        style = MDTheme.type.caption,
        color = MDTheme.colors.accent,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = csvDraft,
        onValueChange = { csvDraft = it.take(1_500_000) },
        label = { Text("Guest list CSV") },
        placeholder = {
            Text("displayName,tableName,seatNumber,partyName\nMarie Tremblay,Table 12,4,Tremblay")
        },
        supportingText = {
            Text("Required: tableName and either displayName or firstName/lastName. Quoted commas are supported.")
        },
        minLines = 5,
        maxLines = 10,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { weddingViewModel.importCsv(csvDraft) },
            enabled = csvDraft.isNotBlank() && !weddingState.admin.isImporting,
        ) {
            Text(if (weddingState.admin.isImporting) "Importing..." else "Import Guest List")
        }
        if (weddingState.seating.data.guests.isNotEmpty()) {
            TextButton(
                onClick = { confirmClear = true },
                enabled = !weddingState.admin.isImporting,
            ) {
                Text("Clear Seating Data", color = MDTheme.colors.danger)
            }
        }
    }
    weddingState.admin.message?.let { message ->
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MDTheme.type.settingSubtitle,
            color = if (weddingState.admin.messageIsError) MDTheme.colors.danger else MDTheme.colors.accent,
        )
    }
    weddingState.seating.errorMessage?.let { message ->
        Spacer(Modifier.height(10.dp))
        Text(message, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.danger)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear seating data?") },
            text = { Text("This removes every imported guest and table from this device. Wedding profile details are kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        weddingViewModel.clearSeating()
                    },
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun formatIdleTimeout(seconds: Int): String = when {
    seconds < 60 -> "$seconds sec"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} sec"
}
