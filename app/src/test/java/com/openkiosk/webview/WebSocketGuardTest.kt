package com.openkiosk.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard script is plain JavaScript and the JVM has no engine for it, so these tests pin the
 * properties that matter and that a careless edit would silently lose. The behaviour itself
 * (a replaced WebSocket that asks the bridge) is exercised in the PR by running the script
 * against a mock window.
 */
class WebSocketGuardTest {

    private val script = WebSocketGuard.SCRIPT

    @Test
    fun `script reads the bridge by the name Kotlin registers`() {
        assertTrue(script.contains("window.${WebSocketGuard.BRIDGE_NAME}"))
    }

    @Test
    fun `script asks the bridge with the absolute url and constructs from that same value`() {
        assertTrue(script.contains("bridge.allows(abs)"))
        // The check and the real socket must use the same string, or a toString() that changes
        // between the two calls would slip a different address past the check.
        assertTrue(script.contains("new Native(abs"))
        assertFalse(script.contains("new Native(url"))
    }

    @Test
    fun `script replaces the constructor reachable through the prototype`() {
        assertTrue(script.contains("Native.prototype"))
        assertTrue(script.contains("'constructor'"))
    }

    @Test
    fun `script never uses eval or Function`() {
        assertFalse(script.contains("eval("))
        assertFalse(Regex("new\\s+Function").containsMatchIn(script))
    }

    @Test
    fun `bridge answers from the live policy and rejects null`() {
        var current = NavigationPolicy(listOf("https://example.com/"))
        val bridge = WebSocketPolicyBridge { current }
        assertFalse(bridge.allows(null))
        assertFalse(bridge.allows("ws://example.com/socket"))
        assertTrue(bridge.allows("ws://192.168.1.10/socket"))
        // A playlist edit that adds an http:// host is seen without recreating the bridge.
        current = NavigationPolicy(listOf("http://example.com/"))
        assertTrue(bridge.allows("ws://example.com/socket"))
    }

    @Test
    fun `bridge name is a plain identifier`() {
        assertEquals(true, Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(WebSocketGuard.BRIDGE_NAME))
    }
}
