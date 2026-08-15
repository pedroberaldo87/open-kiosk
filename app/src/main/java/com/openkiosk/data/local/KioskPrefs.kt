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

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
