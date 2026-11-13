package com.openkiosk.webview

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps the OS-level cleartext switch and the documented policy in step.
 *
 * Android's network security config cannot express "private IP ranges" or hosts the owner types
 * at runtime, so cleartext stays permitted there and NavigationPolicy / WebSocketGuard enforce
 * the real rule (http:// and ws:// only for LAN hosts or hosts the owner wrote as http://).
 * If someone changes the switch, or the READMEs stop describing the rule, this fails and forces
 * the change to be deliberate.
 */
class CleartextPolicyDocsTest {

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val moduleDir = File(requireNotNull(System.getProperty("user.dir")))
    private val repoRoot = moduleDir.parentFile

    private fun xml(file: File): Element {
        assertTrue("missing ${file.path}", file.isFile)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file).documentElement
    }

    @Test
    fun `network security config is one base config with no per-domain exceptions`() {
        val root = xml(File(moduleDir, "src/main/res/xml/network_security_config.xml"))
        assertEquals("network-security-config", root.tagName)
        val base = root.getElementsByTagName("base-config")
        assertEquals(1, base.length)
        assertEquals("true", (base.item(0) as Element).getAttribute("cleartextTrafficPermitted"))
        // A domain-config would suggest a per-host rule the OS cannot enforce for LAN ranges.
        assertEquals(0, root.getElementsByTagName("domain-config").length)
        // Only system trust anchors: no user CAs, no bundled certificates.
        val anchors = root.getElementsByTagName("certificates")
        assertEquals(1, anchors.length)
        assertEquals("system", (anchors.item(0) as Element).getAttribute("src"))
    }

    @Test
    fun `manifest uses the config file and has no second cleartext switch`() {
        val manifest = xml(File(moduleDir, "src/main/AndroidManifest.xml"))
        val app = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals(
            "@xml/network_security_config",
            app.getAttributeNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig")
                .ifEmpty { app.getAttribute("android:networkSecurityConfig") }
        )
        assertFalse(
            "usesCleartextTraffic would duplicate the switch in network_security_config.xml",
            app.hasAttribute("android:usesCleartextTraffic")
        )
    }

    private fun readme(name: String): String {
        val f = File(repoRoot, name)
        assertTrue("missing ${f.path}", f.isFile)
        return f.readText()
    }

    @Test
    fun `English readme says ws follows the http rule`() {
        val text = readme("README.md")
        assertTrue(text.contains("`ws://`"))
        assertTrue(text.contains("WebSocket"))
        assertFalse(
            "the old sentence says WebSockets are unrestricted",
            text.contains("WebSocket connections are not restricted")
        )
        assertNotNull(Regex("WebSocket[^.]*same rule[^.]*`ws://`", RegexOption.IGNORE_CASE).find(text))
    }

    @Test
    fun `Portuguese readme says ws follows the http rule`() {
        val text = readme("README.pt-BR.md")
        assertTrue(text.contains("`ws://`"))
        assertTrue(text.contains("WebSocket"))
        assertFalse(
            "a frase antiga diz que WebSocket não é restringido",
            text.contains("As conexões WebSocket não são restringidas")
        )
        assertNotNull(Regex("WebSocket[^.]*mesma regra[^.]*`ws://`", RegexOption.IGNORE_CASE).find(text))
    }
}
