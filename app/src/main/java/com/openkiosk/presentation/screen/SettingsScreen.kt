package com.openkiosk.presentation.screen

import android.app.Activity
import android.content.Context
import com.openkiosk.data.local.KioskPrefs
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.openkiosk.BuildConfig
import com.openkiosk.R
import com.openkiosk.domain.model.MotionSensitivity
import com.openkiosk.domain.model.PlaylistItem
import com.openkiosk.domain.security.PinPolicy
import com.openkiosk.domain.security.PinVerifyResult
import com.openkiosk.domain.security.SetPinResult
import com.openkiosk.presentation.component.ChoosePinDialog
import com.openkiosk.presentation.component.PinErrorText
import com.openkiosk.presentation.component.SetPinErrorText
import com.openkiosk.presentation.component.rememberLockoutCountdown
import com.openkiosk.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

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
                title = stringResource(R.string.section_playlist)
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlaylistSection(viewModel, playlist)

            SectionDivider()

            // AUTO-REFRESH Section
            SectionHeader(
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                title = stringResource(R.string.section_auto_refresh)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AutoRefreshSection(config.autoRefreshMinutes) { value ->
                viewModel.updateConfig("autoRefreshMinutes", value.toString())
            }

            SectionDivider()

            // SLEEP & WAKE Section
            SectionHeader(
                icon = { Icon(Icons.Default.Close, contentDescription = null) },
                title = stringResource(R.string.section_sleep_wake)
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
                deepSleepEnabled = config.deepSleepEnabled,
                deepSleepStartHour = config.deepSleepStartHour,
                deepSleepEndHour = config.deepSleepEndHour,
                cameraPulseInterval = config.cameraPulseIntervalSeconds,
                onUpdate = { key, value -> viewModel.updateConfig(key, value) }
            )

            SectionDivider()

            // KIOSK Section
            SectionHeader(
                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                title = stringResource(R.string.section_kiosk)
            )
            Spacer(modifier = Modifier.height(8.dp))
            KioskSection(
                lockTaskEnabled = config.lockTaskEnabled,
                pinEnabled = config.pinEnabled,
                onUpdate = { key, value -> viewModel.updateConfig(key, value) },
                onTryEnablePin = { viewModel.tryEnablePinProtection() },
                onEnablePinWith = { viewModel.enablePinProtectionWith(it) },
                onVerifyPin = { viewModel.verifyPin(it) },
                onChangePin = { viewModel.changePin(it) },
                lockoutRemainingMs = { viewModel.pinLockoutRemainingMs() }
            )

            SectionDivider()

            // LANGUAGE Section
            SectionHeader(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = stringResource(R.string.section_language)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LanguageSection()

            SectionDivider()

            // SOBRE Section
            SectionHeader(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = stringResource(R.string.section_about)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.about_github),
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
                Text(stringResource(R.string.action_close))
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
                        contentDescription = stringResource(R.string.action_remove),
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
            Text(stringResource(R.string.suffix_seconds, selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { duration ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.suffix_seconds, duration)) },
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
                label = { Text(stringResource(R.string.label_url)) },
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    } else {
        OutlinedButton(onClick = { showInput = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.action_add_url))
        }
    }
}

@Composable
private fun AutoRefreshSection(currentMinutes: Int, onUpdate: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30, 60)
    val disabledLabel = stringResource(R.string.auto_refresh_disabled)
    var expanded by remember { mutableStateOf(false) }

    fun formatLabel(minutes: Int): String =
        if (minutes == 0) disabledLabel else "$minutes min"

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(formatLabel(currentMinutes))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(formatLabel(minutes)) },
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
    deepSleepEnabled: Boolean,
    deepSleepStartHour: Int,
    deepSleepEndHour: Int,
    cameraPulseInterval: Int,
    onUpdate: (String, String) -> Unit
) {
    Text(stringResource(R.string.label_active_to_dim, activeTimeout), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = activeTimeout.toFloat(),
        onValueChange = { onUpdate("activeTimeoutSeconds", it.toInt().toString()) },
        valueRange = 10f..300f,
        modifier = Modifier.fillMaxWidth()
    )

    Text(stringResource(R.string.label_dim_to_sleep, dimTimeout), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = dimTimeout.toFloat(),
        onValueChange = { onUpdate("dimTimeoutSeconds", it.toInt().toString()) },
        valueRange = 10f..600f,
        modifier = Modifier.fillMaxWidth()
    )

    Text(stringResource(R.string.label_dim_brightness, dimBrightness), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = dimBrightness.toFloat(),
        onValueChange = { onUpdate("dimBrightnessPercent", it.toInt().toString()) },
        valueRange = 5f..50f,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    SwitchRow(stringResource(R.string.switch_wake_camera), wakeOnMotion) {
        onUpdate("wakeOnMotion", it.toString())
    }
    SwitchRow(stringResource(R.string.switch_wake_proximity), wakeOnProximity) {
        onUpdate("wakeOnProximity", it.toString())
    }
    SwitchRow(stringResource(R.string.switch_wake_shake), wakeOnShake) {
        onUpdate("wakeOnShake", it.toString())
    }

    Spacer(modifier = Modifier.height(8.dp))

    val sensitivityLabels = mapOf(
        MotionSensitivity.LOW to stringResource(R.string.sensitivity_low),
        MotionSensitivity.MEDIUM to stringResource(R.string.sensitivity_medium),
        MotionSensitivity.HIGH to stringResource(R.string.sensitivity_high)
    )
    val sensitivityEntries = MotionSensitivity.entries
    DropdownRow(
        label = stringResource(R.string.label_camera_sensitivity),
        selected = sensitivityLabels[motionSensitivity] ?: sensitivityLabels[MotionSensitivity.MEDIUM]!!,
        options = sensitivityEntries.map { sensitivityLabels[it]!! },
        onSelect = { label ->
            val index = sensitivityEntries.map { sensitivityLabels[it]!! }.indexOf(label)
            if (index >= 0) onUpdate("motionSensitivity", sensitivityEntries[index].name)
        }
    )

    Spacer(modifier = Modifier.height(4.dp))

    val pulseOptions = listOf(5, 10, 15, 20, 30, 60)
    val pulseLabels = pulseOptions.map { stringResource(R.string.suffix_seconds, it) }
    DropdownRow(
        label = stringResource(R.string.label_camera_pulse),
        selected = stringResource(R.string.suffix_seconds, cameraPulseInterval),
        options = pulseLabels,
        onSelect = { label ->
            val index = pulseLabels.indexOf(label)
            if (index >= 0) onUpdate("cameraPulseIntervalSeconds", pulseOptions[index].toString())
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    SwitchRow(stringResource(R.string.switch_deep_sleep), deepSleepEnabled) {
        onUpdate("deepSleepEnabled", it.toString())
    }

    if (deepSleepEnabled) {
        val hours = (0..23).toList()
        fun formatHour(h: Int): String = "%02d:00".format(h)

        DropdownRow(
            label = stringResource(R.string.label_deep_sleep_start),
            selected = formatHour(deepSleepStartHour),
            options = hours.map { formatHour(it) },
            onSelect = { label ->
                val hour = label.substringBefore(":").toInt()
                onUpdate("deepSleepStartHour", hour.toString())
            }
        )

        DropdownRow(
            label = stringResource(R.string.label_deep_sleep_end),
            selected = formatHour(deepSleepEndHour),
            options = hours.map { formatHour(it) },
            onSelect = { label ->
                val hour = label.substringBefore(":").toInt()
                onUpdate("deepSleepEndHour", hour.toString())
            }
        )
    }
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
private fun LanguageSection() {
    val context = LocalContext.current
    val prefs = KioskPrefs.of(context)
    val currentLang = prefs.getString("language", "auto") ?: "auto"

    val languages = listOf(
        "auto" to stringResource(R.string.language_auto),
        "en" to "English",
        "pt" to "Português",
        "es" to "Español"
    )

    val currentLabel = languages.firstOrNull { it.first == currentLang }?.second
        ?: languages[0].second

    DropdownRow(
        label = stringResource(R.string.label_language),
        selected = currentLabel,
        options = languages.map { it.second },
        onSelect = { label ->
            val langCode = languages.firstOrNull { it.second == label }?.first ?: "auto"
            prefs.edit().putString("language", langCode).apply()
            // Recreate activity to apply new locale
            (context as? Activity)?.recreate()
        }
    )
}

@Composable
private fun KioskSection(
    lockTaskEnabled: Boolean,
    pinEnabled: Boolean,
    onUpdate: (String, String) -> Unit,
    onTryEnablePin: suspend () -> Boolean,
    onEnablePinWith: suspend (String) -> SetPinResult,
    onVerifyPin: suspend (String) -> PinVerifyResult,
    onChangePin: suspend (String) -> SetPinResult,
    lockoutRemainingMs: suspend () -> Long
) {
    val scope = rememberCoroutineScope()
    var showChoosePin by remember { mutableStateOf(false) }

    SwitchRow(stringResource(R.string.switch_lock_task), lockTaskEnabled) {
        onUpdate("lockTaskEnabled", it.toString())
    }

    Spacer(modifier = Modifier.height(8.dp))

    SwitchRow(stringResource(R.string.switch_pin_protection), pinEnabled) { on ->
        if (!on) {
            onUpdate("pinEnabled", "false")
        } else {
            // Turning protection on with the default 0000 is refused: ask for a PIN instead.
            scope.launch { if (!onTryEnablePin()) showChoosePin = true }
        }
    }

    if (showChoosePin) {
        ChoosePinDialog(
            onChoose = onEnablePinWith,
            onDismiss = { showChoosePin = false }
        )
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
                Text(stringResource(R.string.label_current_pin), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "****",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { showPinChange = true }) {
                Text(stringResource(R.string.action_change_pin))
            }
        }

        if (showPinChange) {
            PinChangeDialog(
                onVerify = onVerifyPin,
                onChange = onChangePin,
                lockoutRemainingMs = lockoutRemainingMs,
                onDismiss = { showPinChange = false }
            )
        }
    }
}

@Composable
private fun PinChangeDialog(
    onVerify: suspend (String) -> PinVerifyResult,
    onChange: suspend (String) -> SetPinResult,
    lockoutRemainingMs: suspend () -> Long,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0 = verify current, 1 = enter new
    var input by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var setResult by remember { mutableStateOf<SetPinResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val lockout = rememberLockoutCountdown(lockoutRemainingMs)
    val scope = rememberCoroutineScope()
    val locked = step == 0 && lockout.remainingMs > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) stringResource(R.string.dialog_title_current_pin) else stringResource(R.string.dialog_title_new_pin)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.length <= PinPolicy.PIN_LENGTH && value.all { it in '0'..'9' }) {
                            input = value
                            wrongPin = false
                            setResult = null
                        }
                    },
                    label = { Text(if (step == 0) stringResource(R.string.hint_enter_current_pin) else stringResource(R.string.hint_enter_new_pin)) },
                    singleLine = true,
                    enabled = !locked,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = wrongPin || setResult != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (step == 0) {
                    PinErrorText(wrongPin = wrongPin && !locked, lockoutRemainingMs = lockout.remainingMs)
                } else {
                    SetPinErrorText(setResult)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !locked && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        if (step == 0) {
                            when (val r = onVerify(input)) {
                                is PinVerifyResult.Success -> {
                                    step = 1
                                    wrongPin = false
                                }
                                is PinVerifyResult.Wrong -> {
                                    wrongPin = true
                                    lockout.start(r.lockoutMs)
                                }
                                is PinVerifyResult.LockedOut -> lockout.start(r.remainingMs)
                            }
                            input = ""
                        } else {
                            val r = onChange(input)
                            if (r == SetPinResult.OK) onDismiss() else setResult = r
                        }
                        busy = false
                    }
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
