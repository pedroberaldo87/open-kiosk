package com.openkiosk.di

import android.content.Context
import com.openkiosk.diagnostics.ConnectionLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {

    // Pasta interna: o Fire nega ao adb ate a pasta externa do app, entao a leitura pelo cabo
    // passa pelo dump do servico vigia (ver KioskWatchdogService.dump) e pela gaveta de ajustes.
    @Provides
    @Singleton
    fun provideConnectionLog(@ApplicationContext context: Context): ConnectionLog =
        ConnectionLog(File(context.filesDir, "connection-log.txt"))
}
