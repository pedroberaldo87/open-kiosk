package com.openkiosk.presentation

import android.app.Activity
import android.net.ConnectivityManager
import android.net.NetworkRequest
import androidx.lifecycle.LifecycleOwner
import com.openkiosk.data.repository.ConfigRepository
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Invariante: desmarcar um sensor nas configuracoes PARA o que ja esta rodando. Sem isso
 * o ouvinte continua acordando o painel ate a tela trocar de estado sozinha.
 */
class KioskViewModelSensorOffTest {

    private val screenStateManager: ScreenStateManager = mock()
    private val configRepository: ConfigRepository = mock()
    private val kioskLockManager: KioskLockManager = mock()
    private val motionDetectionManager: MotionDetectionManager = mock()
    private val sensorWakeManager: SensorWakeManager = mock()
    private val powerStateMonitor: PowerStateMonitor = mock()
    private val playlistManager: PlaylistManager = mock()

    private val configFlow = MutableStateFlow(KioskConfig())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(configRepository.observeConfig()).thenReturn(configFlow)
        whenever(screenStateManager.screenState)
            .thenReturn(MutableStateFlow(ScreenState.SLEEP))
        whenever(playlistManager.currentItem)
            .thenReturn(MutableStateFlow<PlaylistItem?>(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `desligar camera e sensores nas configuracoes para os que rodam`() {
        val vm = KioskViewModel(
            configRepository,
            screenStateManager,
            kioskLockManager,
            motionDetectionManager,
            sensorWakeManager,
            powerStateMonitor,
            playlistManager
        )

        val activity = mock<Activity>()
        whenever(activity.getSystemService(ConnectivityManager::class.java))
            .thenReturn(mock<ConnectivityManager>())
        mockConstruction(NetworkRequest.Builder::class.java) { builder, _ ->
            whenever(builder.addCapability(anyInt())).thenReturn(builder)
        }.use {
            vm.attachActivity(activity, mock<LifecycleOwner>())

            configFlow.value = KioskConfig(
                wakeOnMotion = false,
                wakeOnProximity = false,
                wakeOnShake = false
            )
        }

        verify(sensorWakeManager).stop()
        verify(motionDetectionManager).stop()
    }
}
