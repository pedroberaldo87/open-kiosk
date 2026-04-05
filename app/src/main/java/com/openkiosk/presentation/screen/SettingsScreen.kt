package com.openkiosk.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openkiosk.domain.model.MotionSensitivity
import com.openkiosk.domain.model.PlaylistItem
import com.openkiosk.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsDrawerContent(
    viewModel: SettingsViewModel,
    onClose: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val playlist by viewModel.playlist.collectAsState()

    Surface(
        modifier = Modifier
            .width(350.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // PLAYLIST Section
            SectionHeader(
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                title = "PLAYLIST"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlaylistSection(viewModel, playlist)

            SectionDivider()

            // AUTO-REFRESH Section
            SectionHeader(
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                title = "AUTO-REFRESH"
            )
            Spacer(modifier = Modifier.height(8.dp))
            AutoRefreshSection(config.autoRefreshMinutes) { value ->
                viewModel.updateConfig("autoRefreshMinutes", value.toString())
            }

            SectionDivider()

            // SLEEP & WAKE Section
            SectionHeader(
                icon = { Icon(Icons.Default.Close, contentDescription = null) },
                title = "SLEEP & WAKE"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SleepWakeSection(
                activeTimeout = config.activeTimeoutSeconds,
                dimTimeout = config.dimTimeoutSeconds,
                dimBrightness = config.dimBrightnessPercent,
                wakeOnMotion = config.wakeOnMotion,
                wakeOnProximity = config.wakeOnProximity,
                wakeOnShake = config.wakeOnShake,
                motionSensitivity = config.motionSensitivity,
                cameraPolling = config.cameraPollingIntervalSeconds,
                onUpdate = { key, value -> viewModel.updateConfig(key, value) }
            )

            SectionDivider()

            // KIOSK Section
            SectionHeader(
                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                title = "KIOSK"
            )
            Spacer(modifier = Modifier.height(8.dp))
            KioskSection(
                lockTaskEnabled = config.lockTaskEnabled,
                pinEnabled = config.pinEnabled,
                pin = config.pin,
                onUpdate = { key, value -> viewModel.updateConfig(key, value) }
            )

            SectionDivider()

            // SOBRE Section
            SectionHeader(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = "SOBRE"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Open Kiosk v1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "github.com/pedroberaldo87/OPEN-KIOSK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Footer
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fechar")
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: @Composable () -> Unit, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PlaylistSection(
    viewModel: SettingsViewModel,
    playlist: List<PlaylistItem>
) {
    val durationOptions = listOf(10, 15, 30, 60, 120, 300)

    playlist.forEach { item ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.url,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                DurationDropdown(
                    selected = item.durationSeconds,
                    options = durationOptions,
                    onSelect = { duration ->
                        viewModel.updatePlaylistItem(item.copy(durationSeconds = duration))
                    }
                )
                IconButton(onClick = { viewModel.removePlaylistItem(item) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remover",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    AddUrlRow(onAdd = { url, duration -> viewModel.addPlaylistItem(url, duration) })
}

@Composable
private fun DurationDropdown(
    selected: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("${selected}s")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { duration ->
                DropdownMenuItem(
                    text = { Text("${duration}s") },
                    onClick = {
                        onSelect(duration)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AddUrlRow(onAdd: (String, Int) -> Unit) {
    var showInput by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    if (showInput) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    if (url.isNotBlank()) {
                        onAdd(url.trim(), 30)
                        url = ""
                        showInput = false
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        }
    } else {
        OutlinedButton(onClick = { showInput = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Adicionar URL")
        }
    }
}

@Composable
private fun AutoRefreshSection(currentMinutes: Int, onUpdate: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30, 60)
    val labels = mapOf(
        0 to "Desativado",
        5 to "5 min",
        10 to "10 min",
        15 to "15 min",
        30 to "30 min",
        60 to "60 min"
    )
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(labels[currentMinutes] ?: "$currentMinutes min")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(labels[minutes] ?: "$minutes min") },
                    onClick = {
                        onUpdate(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SleepWakeSection(
    activeTimeout: Int,
    dimTimeout: Int,
    dimBrightness: Int,
    wakeOnMotion: Boolean,
    wakeOnProximity: Boolean,
    wakeOnShake: Boolean,
    motionSensitivity: MotionSensitivity,
    cameraPolling: Int,
    onUpdate: (String, String) -> Unit
) {
    Text("Ativo → DIM: ${activeTimeout}s", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = activeTimeout.toFloat(),
        onValueChange = { onUpdate("activeTimeoutSeconds", it.toInt().toString()) },
        valueRange = 10f..300f,
        modifier = Modifier.fillMaxWidth()
    )

    Text("DIM → Sleep: ${dimTimeout}s", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = dimTimeout.toFloat(),
        onValueChange = { onUpdate("dimTimeoutSeconds", it.toInt().toString()) },
        valueRange = 10f..600f,
        modifier = Modifier.fillMaxWidth()
    )

    Text("Brilho DIM: ${dimBrightness}%", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = dimBrightness.toFloat(),
        onValueChange = { onUpdate("dimBrightnessPercent", it.toInt().toString()) },
        valueRange = 5f..50f,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    SwitchRow("Acordar por câmera", wakeOnMotion) {
        onUpdate("wakeOnMotion", it.toString())
    }
    SwitchRow("Acordar por proximidade", wakeOnProximity) {
        onUpdate("wakeOnProximity", it.toString())
    }
    SwitchRow("Acordar por movimento", wakeOnShake) {
        onUpdate("wakeOnShake", it.toString())
    }

    Spacer(modifier = Modifier.height(8.dp))

    val sensitivityLabels = mapOf(
        MotionSensitivity.LOW to "Baixa",
        MotionSensitivity.MEDIUM to "Média",
        MotionSensitivity.HIGH to "Alta"
    )
    DropdownRow(
        label = "Sensibilidade da câmera",
        selected = sensitivityLabels[motionSensitivity] ?: "Média",
        options = MotionSensitivity.entries.map { sensitivityLabels[it]!! },
        onSelect = { label ->
            val sensitivity = sensitivityLabels.entries.first { it.value == label }.key
            onUpdate("motionSensitivity", sensitivity.name)
        }
    )

    Spacer(modifier = Modifier.height(4.dp))

    val pollingOptions = listOf(1, 2, 3, 5, 10)
    DropdownRow(
        label = "Polling câmera",
        selected = "${cameraPolling}s",
        options = pollingOptions.map { "${it}s" },
        onSelect = { label ->
            val seconds = label.removeSuffix("s").toInt()
            onUpdate("cameraPollingIntervalSeconds", seconds.toString())
        }
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DropdownRow(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KioskSection(
    lockTaskEnabled: Boolean,
    pinEnabled: Boolean,
    pin: String,
    onUpdate: (String, String) -> Unit
) {
    SwitchRow("Lock Task Mode", lockTaskEnabled) {
        onUpdate("lockTaskEnabled", it.toString())
    }

    Spacer(modifier = Modifier.height(8.dp))

    SwitchRow("Proteção por PIN", pinEnabled) {
        onUpdate("pinEnabled", it.toString())
    }

    if (pinEnabled) {
        Spacer(modifier = Modifier.height(8.dp))

        var showPinChange by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PIN atual", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "****",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { showPinChange = true }) {
                Text("Alterar PIN")
            }
        }

        if (showPinChange) {
            PinChangeDialog(
                currentPin = pin,
                onConfirm = { newPin ->
                    onUpdate("pin", newPin)
                    showPinChange = false
                },
                onDismiss = { showPinChange = false }
            )
        }
    }
}

@Composable
private fun PinChangeDialog(
    currentPin: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0 = verify current, 1 = enter new
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) "PIN atual" else "Novo PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it.isDigit() }) {
                            input = value
                            error = false
                        }
                    },
                    label = { Text(if (step == 0) "Digite o PIN atual" else "Digite o novo PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        text = "PIN incorreto",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (step == 0) {
                    if (input == currentPin) {
                        step = 1
                        input = ""
                        error = false
                    } else {
                        error = true
                        input = ""
                    }
                } else {
                    if (input.length == 4) {
                        onConfirm(input)
                    }
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
