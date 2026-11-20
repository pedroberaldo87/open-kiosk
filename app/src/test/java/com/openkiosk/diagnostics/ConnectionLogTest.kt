package com.openkiosk.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Invariante: o registro de conexao sobrevive a reinicio do processo (arquivo), mostra o mais
 * recente primeiro e nao cresce sem limite — o tablet fica meses ligado.
 */
class ConnectionLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `grava uma linha com data e hora por evento`() {
        val file = File(tmp.root, "connection-log.txt")
        val log = ConnectionLog(file, maxBytes = 10_000, now = { 0L })

        log.record("network lost")

        val line = file.readLines().single()
        assertTrue(line, line.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}  network lost""")))
    }

    @Test
    fun `recent devolve o mais novo primeiro e respeita o limite`() {
        val log = ConnectionLog(File(tmp.root, "connection-log.txt"), maxBytes = 10_000, now = { 0L })

        log.record("a")
        log.record("b")
        log.record("c")

        assertEquals(listOf("c", "b"), log.recent(2).map { it.substringAfter("  ") })
    }

    @Test
    fun `passando do teto o arquivo vira copia anterior e a leitura junta os dois`() {
        val file = File(tmp.root, "connection-log.txt")
        val log = ConnectionLog(file, maxBytes = 60, now = { 0L })

        repeat(4) { log.record("evento $it") }

        assertTrue(File(file.path + ".1").exists())
        assertEquals(
            listOf("evento 3", "evento 2", "evento 1", "evento 0"),
            log.recent(10).map { it.substringAfter("  ") }
        )
    }

    @Test
    fun `nada gravado devolve lista vazia`() {
        val log = ConnectionLog(File(tmp.root, "connection-log.txt"))

        assertEquals(emptyList<String>(), log.recent(30))
    }
}
