package com.openkiosk.sleep

import android.view.WindowManager
import com.openkiosk.domain.model.ScreenState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Invariante: o painel nasce preto em SLEEP/DEEP_SLEEP, com o brilho CONFIGURADO em DIM
 * (nunca o default), e sem override em ACTIVE.
 */
class BrightnessForStateTest {

    @Test
    fun `active roda sem override de brilho`() {
        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            brightnessFor(ScreenState.ACTIVE, dimBrightness = 0.2f)
        )
    }

    @Test
    fun `dim usa o brilho configurado`() {
        assertEquals(0.35f, brightnessFor(ScreenState.DIM, dimBrightness = 0.35f))
    }

    @Test
    fun `sleep e deep sleep nascem pretos`() {
        assertEquals(0.0f, brightnessFor(ScreenState.SLEEP, dimBrightness = 0.2f))
        assertEquals(0.0f, brightnessFor(ScreenState.DEEP_SLEEP, dimBrightness = 0.2f))
    }
}
