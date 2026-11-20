package com.openkiosk.presentation

import android.app.Activity
import android.net.ConnectivityManager
import android.net.NetworkRequest
import androidx.lifecycle.LifecycleOwner
import com.openkiosk.data.repository.ConfigRepository
import com.openkiosk.diagnostics.ConnectionLog
import com.openkiosk.domain.PlaylistManager
import com.openkiosk.domain.model.KioskConfig
import com.openkiosk.domain.model.PlaylistItem
import com.openkiosk.domain.model.ScreenState
import com.openkiosk.kiosk.KioskLockManager
import com.openkiosk.power.PowerStateMonitor
import com.openkiosk.presentation.viewmodel.KioskViewModel
import com.openkiosk.sensors.MotionDetectionManager
import com.openkiosk.sensors.SensorWakeManager
import com.openkiosk.sleep.ScreenStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Invariante: pagina que falhou volta a ser tentada quando o quiosque volta para a frente.
 * A pausa cancela a nova tentativa agendada, e o aviso "a internet voltou" so chega com o app na
 * frente — sem isto a tela de erro ficava presa ate a proxima queda (reproduzido no Fire em
 * 2026-11-20: Wi-Fi desligado, tela apagada, Wi-Fi religado, erro continuou na tela).
 */
class KioskViewModelRetryOnResumeTest {

    private val screenStateManager: ScreenStateManager = mock()
    private val configRepository: ConfigRepository = mock()
    private val playlistManager: PlaylistManager = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(configRepository.observeConfig()).thenReturn(flowOf(KioskConfig()))
        whenever(screenStateManager.screenState).thenReturn(MutableStateFlow(ScreenState.ACTIVE))
        whenever(playlistManager.currentItem).thenReturn(MutableStateFlow<PlaylistItem?>(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = KioskViewModel(
        configRepository,
        screenStateManager,
        mock<KioskLockManager>(),
        mock<MotionDetectionManager>(),
        mock<SensorWakeManager>(),
        mock<PowerStateMonitor>(),
        playlistManager,
        mock<ConnectionLog>()
    )

    private fun KioskViewModel.attach() {
        val activity = mock<Activity>()
        whenever(activity.getSystemService(ConnectivityManager::class.java))
            .thenReturn(mock<ConnectivityManager>())
        mockConstruction(NetworkRequest.Builder::class.java) { builder, _ ->
            whenever(builder.addCapability(anyInt())).thenReturn(builder)
        }.use {
            attachActivity(activity, mock<LifecycleOwner>())
        }
    }

    @Test
    fun `voltar para a frente com a pagina em erro pede recarga`() {
        val vm = newViewModel()
        vm.attach()
        vm.onWebViewError(-2, "net::ERR_INTERNET_DISCONNECTED", "https://example.com")
        vm.detachActivity()

        vm.attach()

        assertEquals(true, vm.needsRefresh.value)
    }

    @Test
    fun `voltar para a frente com a pagina boa nao recarrega`() {
        val vm = newViewModel()
        vm.attach()
        vm.onWebViewError(-2, "net::ERR_INTERNET_DISCONNECTED", "https://example.com")
        vm.onWebViewPageLoaded()
        vm.detachActivity()

        vm.attach()

        assertEquals(false, vm.needsRefresh.value)
    }
}
