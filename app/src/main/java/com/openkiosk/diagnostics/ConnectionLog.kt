package com.openkiosk.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ConnectionLog"

// knob de campo: teto de cada arquivo (o atual + uma copia anterior). Subir guarda mais dias de
// historico; ~80 bytes por evento, entao 256 KB sao uns 3.000 eventos por arquivo.
private const val MAX_BYTES = 256 * 1024L

/**
 * Registro de quedas e voltas da pagina e da rede, em arquivo. Existe porque o logcat do Fire
 * guarda so ~2 minutos (o HAL da camera enche o buffer) e o app de producao nao e depuravel:
 * sem isto, "a tela deu erro ontem a noite" nao deixava rastro nenhum.
 */
class ConnectionLog(
    private val file: File,
    private val maxBytes: Long = MAX_BYTES,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val previous: File get() = File(file.path + ".1")

    // Muda a cada evento: a gaveta de ajustes fica montada mesmo fechada, entao rele o arquivo
    // quando isto muda em vez de mostrar a foto de quando o app abriu.
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun record(event: String) {
        Log.i(TAG, event)
        // Falha de disco nao pode derrubar o quiosque: perder uma linha de registro e o menor mal.
        try {
            file.parentFile?.mkdirs()
            if (file.length() >= maxBytes) {
                previous.delete()
                file.renameTo(previous)
            }
            file.appendText("${format.format(Date(now()))}  $event\n")
        } catch (e: IOException) {
            Log.w(TAG, "nao gravou o registro: ${e.message}")
        }
        _version.value++
    }

    /** As [limit] linhas mais recentes, a mais nova primeiro. */
    @Synchronized
    fun recent(limit: Int): List<String> = try {
        listOf(previous, file)
            .filter { it.exists() }
            .flatMap { it.readLines() }
            .takeLast(limit)
            .reversed()
    } catch (e: IOException) {
        Log.w(TAG, "nao leu o registro: ${e.message}")
        emptyList()
    }
}
