package com.openkiosk.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.openkiosk.domain.model.ScreenState
import com.openkiosk.presentation.component.KioskWebView
import com.openkiosk.presentation.component.OfflineScreen
import com.openkiosk.presentation.component.PinDialog
import com.openkiosk.presentation.viewmodel.KioskViewModel
import com.openkiosk.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun KioskScreen(viewModel: KioskViewModel) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val needsRefresh by viewModel.needsRefresh.collectAsState()
    val config by viewModel.config.collectAsState()
    val screenState by viewModel.screenState.collectAsState()

    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }
    var pinVerified by remember { mutableStateOf(false) }

    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            refreshKey++
            viewModel.onRefreshConsumed()
        }
    }

    // Reset pinVerified when drawer closes
    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed) {
            pinVerified = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !showPinDialog && screenState == ScreenState.ACTIVE,
        drawerContent = {
            SettingsDrawerContent(
                viewModel = settingsViewModel,
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // WebView layer — always rendered (keeps content alive)
            if (!isOnline && currentUrl.isBlank()) {
                OfflineScreen()
            } else {
                val urlToLoad = currentUrl.ifBlank { config.startUrl }
                androidx.compose.runtime.key(refreshKey) {
                    KioskWebView(
                        url = urlToLoad,
                        onUserInteraction = { viewModel.onUserInteraction() },
                        modifier = Modifier.fillMaxSize(),
                        onError = { viewModel.onWebViewError() },
                        onPageLoaded = { viewModel.onWebViewPageLoaded() }
                    )
                }
            }

            // Black overlay — covers everything when SLEEP or DEEP_SLEEP
            // Touch on overlay wakes the screen
            if (screenState == ScreenState.SLEEP || screenState == ScreenState.DEEP_SLEEP) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            viewModel.onUserInteraction()
                        }
                )
            }
        }
    }

    // PIN dialog gate: intercept drawer open gesture (only if PIN enabled)
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && config.pinEnabled && !pinVerified) {
            drawerState.close()
            showPinDialog = true
        }
    }

    if (showPinDialog) {
        PinDialog(
            correctPin = config.pin,
            onSuccess = {
                showPinDialog = false
                pinVerified = true
                scope.launch { drawerState.open() }
            },
            onDismiss = { showPinDialog = false }
        )
    }
}
