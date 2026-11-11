package com.openkiosk.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Único lugar que nomeia o arquivo de preferências e suas chaves. O estado de tela é
 * ESCRITO pelo ScreenStateManager e LIDO pelo watchdog em outro arquivo — com o nome
 * repetido em cada um, renomear de um lado faria o watchdog ler sempre vazio (e o
 * default é "acende"), acendendo o painel de madrugada sem ninguém na frente.
 */
object KioskPrefs {
    const val FILE = "open_kiosk_prefs"
    const val KEY_SCREEN_STATE = "screen_state"
    const val KEY_LANGUAGE = "language"

    /**
     * Espelho de KioskConfig.lockTaskEnabled para quem nao le o Room: o watchdog (a cada
     * tick), o BootReceiver e o botao Voltar. Escrito pelo KioskLockManager quando a config
     * real chega. Ausente = desligado: o aparelho e de quem usa, prender e opcional.
     */
    const val KEY_KEEP_IN_FRONT = "keep_in_front"

    fun keepInFront(context: Context): Boolean =
        of(context).getBoolean(KEY_KEEP_IN_FRONT, false)

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
