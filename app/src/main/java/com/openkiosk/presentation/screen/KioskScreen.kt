package com.openkiosk.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openkiosk.presentation.component.KioskWebView
import com.openkiosk.presentation.component.OfflineScreen
import com.openkiosk.presentation.component.PinDialog
import com.openkiosk.presentation.viewmodel.KioskViewModel
import kotlinx.coroutines.launch

@Composable
fun KioskScreen(viewModel: KioskViewModel) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val needsRefresh by viewModel.needsRefresh.collectAsState()
    val config by viewModel.config.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }

    // Use a key that changes when we need to refresh
    var refreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            refreshKey++
            viewModel.onRefreshConsumed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            SettingsScreen(
                onBack = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isOnline && currentUrl.isBlank()) {
                OfflineScreen()
            } else {
                val urlToLoad = currentUrl.ifBlank { config.startUrl }
                key(refreshKey) {
                    KioskWebView(
                        url = urlToLoad,
                        onUserInteraction = { viewModel.onUserInteraction() },
                        modifier = Modifier.fillMaxSize(),
                        onError = { viewModel.onWebViewError() },
                        onPageLoaded = { viewModel.onWebViewPageLoaded() }
                    )
                }
            }
        }
    }

    // PIN dialog gate for settings drawer
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && !showPinDialog) {
            // Drawer was opened by gesture — close it and show PIN dialog
            drawerState.close()
            showPinDialog = true
        }
    }

    if (showPinDialog) {
        PinDialog(
            correctPin = config.pin,
            onSuccess = {
                showPinDialog = false
                scope.launch { drawerState.open() }
            },
            onDismiss = { showPinDialog = false }
        )
    }
}

@Composable
private fun key(key: Int, content: @Composable () -> Unit) {
    androidx.compose.runtime.key(key) { content() }
}
