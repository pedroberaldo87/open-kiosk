package com.openkiosk.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openkiosk.R
import com.openkiosk.domain.security.PinLockout
import com.openkiosk.domain.security.PinPolicy
import com.openkiosk.domain.security.PinVerifyResult
import com.openkiosk.domain.security.SetPinResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The dialog never sees the correct PIN: [verifyPin] asks the repository, which counts the
 * attempt and enforces the lockout, so the limit holds across dialog and process restarts.
 */
@Composable
fun PinDialog(
    verifyPin: suspend (String) -> PinVerifyResult,
    lockoutRemainingMs: suspend () -> Long,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val lockout = rememberLockoutCountdown(lockoutRemainingMs)
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || lockout.remainingMs > 0L || pin.length != PinPolicy.PIN_LENGTH) return
        busy = true
        scope.launch {
            when (val result = verifyPin(pin)) {
                is PinVerifyResult.Success -> onSuccess()
                is PinVerifyResult.Wrong -> {
                    error = true
                    lockout.start(result.lockoutMs)
                }
                is PinVerifyResult.LockedOut -> lockout.start(result.remainingMs)
            }
            pin = ""
            busy = false
        }
    }

    LaunchedEffect(pin) {
        if (pin.length == PinPolicy.PIN_LENGTH) submit()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_enter_pin)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= PinPolicy.PIN_LENGTH && value.all { it in '0'..'9' }) {
                            pin = value
                            error = false
                        }
                    },
                    label = { Text(stringResource(R.string.label_pin)) },
                    singleLine = true,
                    enabled = lockout.remainingMs <= 0L,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                PinErrorText(
                    wrongPin = error && lockout.remainingMs <= 0L,
                    lockoutRemainingMs = lockout.remainingMs
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = lockout.remainingMs <= 0L) {
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

/** Asks for a new PIN when protection is being turned on and no usable PIN exists yet. */
@Composable
fun ChoosePinDialog(
    onChoose: suspend (String) -> SetPinResult,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SetPinResult?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_choose_pin)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.message_choose_pin_to_enable),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= PinPolicy.PIN_LENGTH && value.all { it in '0'..'9' }) {
                            pin = value
                            result = null
                        }
                    },
                    label = { Text(stringResource(R.string.hint_enter_new_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = result != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                SetPinErrorText(result)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val r = onChoose(pin)
                    if (r == SetPinResult.OK) onDismiss() else result = r
                }
            }) {
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

/** Remaining lockout, ticking down once a second. The repository stays the source of truth. */
class LockoutCountdown internal constructor(
    private val state: androidx.compose.runtime.MutableLongState
) {
    val remainingMs: Long get() = state.longValue
    fun start(ms: Long) { state.longValue = ms }
}

@Composable
fun rememberLockoutCountdown(initial: suspend () -> Long): LockoutCountdown {
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { state.longValue = initial() }
    val running = state.longValue > 0L
    LaunchedEffect(running) {
        while (state.longValue > 0L) {
            delay(1000)
            state.longValue = (state.longValue - 1000).coerceAtLeast(0L)
        }
    }
    return remember { LockoutCountdown(state) }
}

@Composable
fun PinErrorText(wrongPin: Boolean, lockoutRemainingMs: Long) {
    val text = when {
        lockoutRemainingMs > 0L ->
            stringResource(R.string.error_pin_locked, PinLockout.formatRemaining(lockoutRemainingMs))
        wrongPin -> stringResource(R.string.error_wrong_pin)
        else -> return
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun SetPinErrorText(result: SetPinResult?) {
    val text = when (result) {
        SetPinResult.DEFAULT_REJECTED -> stringResource(R.string.error_pin_default_rejected)
        SetPinResult.INVALID_FORMAT -> stringResource(R.string.error_pin_invalid_format)
        else -> return
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp)
    )
}
