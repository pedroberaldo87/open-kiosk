package com.openkiosk.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blocked-page HTML must show what was blocked, stay local and never echo markup. */
class BlockedPageTest {

    private val title = "Address not allowed"
    private val message = "The kiosk configuration does not allow this address."

    @Test
    fun `page shows title, message and the blocked host`() {
        val html = BlockedPage.html(title, message, "idp.example.com")
        assertTrue(html.contains(title))
        assertTrue(html.contains(message))
        assertTrue(html.contains("<code>idp.example.com</code>"))
    }

    @Test
    fun `page without a known host omits the host line`() {
        assertFalse(BlockedPage.html(title, message, null).contains("<code>"))
        assertFalse(BlockedPage.html(title, message, "").contains("<code>"))
    }

    @Test
    fun `page is self contained`() {
        val html = BlockedPage.html(title, message, "idp.example.com")
        assertFalse(html.contains("<script", ignoreCase = true))
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
        assertFalse(html.contains("<img", ignoreCase = true))
        assertFalse(html.contains("<link", ignoreCase = true))
    }

    @Test
    fun `markup in any field is escaped`() {
        val html = BlockedPage.html("<b>t</b>", "a & \"b\"", "<script>alert(1)</script>")
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("<b>t"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("a &amp; &quot;b&quot;"))
    }

    @Test
    fun `escape handles quotes and ampersands`() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", BlockedPage.escape("&<>\"'"))
    }

    // The host shown comes from the policy parser: no userinfo, port, path or query.

    @Test
    fun `host shown is the bare host of the blocked address`() {
        assertEquals("idp.example.com", NavigationPolicy.hostOf("https://user:pw@IDP.example.com.:8443/login?token=abc"))
        assertEquals("xn--bcher-kva.example", NavigationPolicy.hostOf("https://xn--bcher-kva.example/"))
    }

    @Test
    fun `host of an unparsable address is null`() {
        assertNull(NavigationPolicy.hostOf("not a url"))
        assertNull(NavigationPolicy.hostOf("https://exam\tple.com/"))
        assertNull(NavigationPolicy.hostOf(""))
    }
}
