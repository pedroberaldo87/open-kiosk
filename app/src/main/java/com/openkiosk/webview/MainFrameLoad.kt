package com.openkiosk.webview

/**
 * Se a carga atual do quadro principal deu erro. O WebView chama "terminou de carregar" tambem
 * para a pagina de erro; contar isso como sucesso cancelava a nova tentativa recem-agendada e
 * deixava "Webpage not available" na tela para sempre.
 */
class MainFrameLoad {
    @Volatile
    private var failed = false

    fun started() {
        failed = false
    }

    fun failed() {
        failed = true
    }

    fun finishedOk(): Boolean = !failed
}
