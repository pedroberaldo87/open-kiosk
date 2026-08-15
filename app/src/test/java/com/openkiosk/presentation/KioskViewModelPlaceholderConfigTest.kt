package com.openkiosk.presentation

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Invariante: o valor de fabrica do stateIn (deepSleepEnabled = false) NUNCA pode chegar a
 * ScreenStateManager.configure — com o estado guardado em DEEP_SLEEP ele solta a tela e o
 * painel acende de madrugada sem ninguem na frente. So a config do banco decide a agenda.
 */
class KioskViewModelPlaceholderConfigTest {

    private val screenStateManager: ScreenStateManager = mock()
    private val configRepository: ConfigRepository = mock()
    private val kioskLockManager: KioskLockManager = mock()
    private val motionDetectionManager: MotionDetectionManager = mock()
    private val sensorWakeManager: SensorWakeManager = mock()
    private val powerStateMonitor: PowerStateMonitor = mock()
    private val playlistManager: PlaylistManager = mock()

    private val configFlow = MutableSharedFlow<KioskConfig>(replay = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(configRepository.observeConfig()).thenReturn(configFlow)
        whenever(screenStateManager.screenState)
            .thenReturn(MutableStateFlow(ScreenState.DEEP_SLEEP))
        whenever(playlistManager.currentItem)
            .thenReturn(MutableStateFlow<PlaylistItem?>(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `config de fabrica nunca chega a configure antes da emissao do banco`() {
        KioskViewModel(
            configRepository,
            screenStateManager,
            kioskLockManager,
            motionDetectionManager,
            sensorWakeManager,
            powerStateMonitor,
            playlistManager
        )

        configFlow.tryEmit(KioskConfig(deepSleepEnabled = true))

        verify(screenStateManager, never())
            .configure(any(), any(), any(), eq(false), any(), any())
        verify(screenStateManager)
            .configure(any(), any(), any(), eq(true), any(), any())
    }
}
