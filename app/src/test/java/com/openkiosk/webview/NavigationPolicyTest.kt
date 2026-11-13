package com.openkiosk.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant: the kiosk WebView only shows hosts the owner configured (start URL + playlist),
 * and plain http:// only reaches private/LAN hosts or hosts the owner explicitly wrote as http://.
 * Hosts below use example.com / 192.0.2.x / RFC1918 documentation-style addresses only.
 */
class NavigationPolicyTest {

    private fun policy(vararg urls: String) = NavigationPolicy(urls.toList())

    // ---- host allowlist -------------------------------------------------------------------

    @Test
    fun `configured https host is allowed`() {
        assertTrue(policy("https://example.com/board").allowsNavigation("https://example.com/other?x=1"))
    }

    @Test
    fun `redirect to a host that was not configured is blocked`() {
        val p = policy("https://example.com/board")
        assertFalse(p.allowsNavigation("https://evil.test/phish"))
    }

    @Test
    fun `playlist hosts are all allowed and the rest still blocked`() {
        val p = policy("https://example.com/a", "https://example.org/b")
        assertTrue(p.allowsNavigation("https://example.org/x"))
        assertTrue(p.allowsNavigation("https://example.com/x"))
        assertFalse(p.allowsNavigation("https://example.net/x"))
    }

    @Test
    fun `empty allowlist blocks everything`() {
        assertFalse(policy().allowsNavigation("https://example.com/"))
    }

    @Test
    fun `subdomain of a configured host is allowed`() {
        val p = policy("https://example.com/")
        assertTrue(p.allowsNavigation("https://login.example.com/"))
        assertTrue(p.allowsNavigation("https://a.b.example.com/"))
    }

    @Test
    fun `www and apex of the configured host are interchangeable`() {
        assertTrue(policy("https://www.example.com/").allowsNavigation("https://example.com/"))
        assertTrue(policy("https://example.com/").allowsNavigation("https://www.example.com/"))
    }

    @Test
    fun `lookalike hosts are blocked`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsNavigation("https://evilexample.com/"))
        assertFalse(p.allowsNavigation("https://example.com.evil.test/"))
        assertFalse(p.allowsNavigation("https://example.co/"))
    }

    @Test
    fun `a sibling subdomain is not allowed when a subdomain was configured`() {
        val p = policy("https://board.example.com/")
        assertTrue(p.allowsNavigation("https://board.example.com/x"))
        assertFalse(p.allowsNavigation("https://example.com/"))
        assertFalse(p.allowsNavigation("https://other.example.com/"))
    }

    @Test
    fun `host comparison ignores case, trailing dot and port`() {
        val p = policy("https://Example.COM/")
        assertTrue(p.allowsNavigation("https://example.com./x"))
        assertTrue(p.allowsNavigation("HTTPS://EXAMPLE.COM:8443/x"))
    }

    @Test
    fun `userinfo tricks cannot smuggle another host`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsNavigation("https://example.com@evil.test/"))
        assertFalse(p.allowsNavigation("https://example.com:pw@evil.test/"))
        assertFalse(p.allowsNavigation("https://example.com\\@evil.test/"))
        assertTrue(p.allowsNavigation("https://evil.test@example.com/"))
    }

    @Test
    fun `control characters, percent escapes and non ascii in the host fail closed`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsNavigation("https://exam\tple.com/"))
        assertFalse(p.allowsNavigation("https://example.com%2eevil.test/"))
        assertFalse(p.allowsNavigation("https://example\u3002com/"))
    }

    @Test
    fun `ip literal in the allowlist matches only that exact address`() {
        val p = policy("https://192.0.2.10/")
        assertTrue(p.allowsNavigation("https://192.0.2.10/x"))
        assertFalse(p.allowsNavigation("https://192.0.2.11/"))
        assertFalse(p.allowsNavigation("https://1.192.0.2.10/"))
        assertFalse(p.allowsNavigation("https://192.0.2.10.evil.test/"))
    }

    @Test
    fun `schemes other than http and https are blocked`() {
        val p = policy("https://example.com/")
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/x.html",
            "intent://example.com/#Intent;scheme=https;end",
            "data:text/html,hi",
            "about:blank",
            "content://example.com/x",
            "ftp://example.com/x",
            "blob:https://example.com/uuid"
        ).forEach { assertFalse(it, p.allowsNavigation(it)) }
    }

    @Test
    fun `blank and unparsable targets are blocked`() {
        val p = policy("https://example.com/")
        listOf("", "   ", "not a url", "https://", "https:///x", "https://:443/", "http://[::1/").forEach {
            assertFalse(it, p.allowsNavigation(it))
        }
    }

    @Test
    fun `urls with characters java net URI rejects still count`() {
        // Grafana style query strings carry [ ] { } | that java.net.URI refuses to parse.
        val p = policy("https://example.com/d?var-x=[a]&q={b}|c")
        assertTrue(p.allowsNavigation("https://example.com/d?var-x=[z]&q={y}|w"))
    }

    // ---- cleartext ------------------------------------------------------------------------

    @Test
    fun `https config redirected to http on the same public host is blocked`() {
        assertFalse(policy("https://example.com/").allowsNavigation("http://example.com/"))
    }

    @Test
    fun `http to a private ip is allowed when that host is configured`() {
        val p = policy("http://192.168.1.20:8123/lovelace")
        assertTrue(p.allowsNavigation("http://192.168.1.20:8123/config"))
    }

    @Test
    fun `http to a private host that was not configured is blocked`() {
        val p = policy("http://192.168.1.20:8123/")
        assertFalse(p.allowsNavigation("http://192.168.1.21/"))
        assertFalse(p.allowsNavigation("http://10.0.0.5/"))
    }

    @Test
    fun `http lan names are allowed when configured`() {
        assertTrue(policy("http://nas.local/").allowsNavigation("http://nas.local/dash"))
        assertTrue(policy("http://grafana.lan:3000/").allowsNavigation("http://grafana.lan:3000/d/1"))
        assertTrue(policy("http://homeassistant:8123/").allowsNavigation("http://homeassistant:8123/x"))
    }

    @Test
    fun `http on a public host is allowed only when the owner configured that host as http`() {
        val configured = policy("http://example.com/board")
        assertTrue(configured.allowsNavigation("http://example.com/board/2"))
        // configured as https only: the http variant stays blocked
        assertFalse(policy("https://example.com/board").allowsNavigation("http://example.com/board"))
        // a different public host over http is still blocked
        assertFalse(configured.allowsNavigation("http://example.org/"))
    }

    @Test
    fun `http configured private dashboard cannot bounce to a public http host`() {
        val p = policy("http://192.168.1.20:8123/")
        assertFalse(p.allowsNavigation("http://example.com/"))
        assertFalse(p.allowsNavigation("https://example.com/"))
    }

    @Test
    fun `https to a configured private host is allowed`() {
        assertTrue(policy("https://192.168.1.20/").allowsNavigation("https://192.168.1.20/x"))
    }

    // ---- sub-resources --------------------------------------------------------------------

    @Test
    fun `subresources over https are not restricted by the host allowlist`() {
        assertTrue(policy("https://example.com/").allowsRequest("https://cdn.example.net/app.js"))
    }

    @Test
    fun `http subresource from a public host is blocked unless configured as http`() {
        assertFalse(policy("https://example.com/").allowsRequest("http://cdn.example.net/app.js"))
        assertFalse(policy("http://192.168.1.20/").allowsRequest("http://cdn.example.net/app.js"))
        assertTrue(policy("http://cdn.example.net/").allowsRequest("http://cdn.example.net/app.js"))
    }

    @Test
    fun `http subresource from a private host is allowed`() {
        assertTrue(policy("http://192.168.1.20/").allowsRequest("http://192.168.1.20:8123/api/x"))
        assertTrue(policy("http://192.168.1.20/").allowsRequest("http://nas.local/x.png"))
    }

    @Test
    fun `non http requests and unparsable http requests`() {
        val p = policy("https://example.com/")
        assertTrue(p.allowsRequest("data:image/png;base64,AAAA"))
        assertTrue(p.allowsRequest("blob:https://example.com/uuid"))
        assertFalse(p.allowsRequest("http://"))
    }

    // ---- private host classification ------------------------------------------------------

    @Test
    fun `private ipv4 ranges`() {
        listOf(
            "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.0.1",
            "192.168.255.254", "127.0.0.1", "127.1.2.3", "169.254.10.20"
        ).forEach { assertTrue(it, NavigationPolicy.isPrivateHost(it)) }
    }

    @Test
    fun `public ipv4 and lookalikes are not private`() {
        listOf(
            "192.0.2.1", "8.8.8.8", "172.15.0.1", "172.32.0.1", "11.0.0.1", "192.167.1.1",
            "169.253.1.1", "100.64.0.1", "0.0.0.0", "256.1.1.1", "10.0.0.1.example.com",
            "010.0.0.1", "0x0a.0.0.1", "10.0.0", "3232235777", "0xc0000201"
        ).forEach { assertFalse(it, NavigationPolicy.isPrivateHost(it)) }
    }

    @Test
    fun `private ipv6`() {
        listOf("::1", "fd00::1", "fc12:3456::1", "fe80::1", "febf::1").forEach {
            assertTrue(it, NavigationPolicy.isPrivateHost(it))
        }
        listOf("2001:db8::1", "fec0::1", "::", "::ffff:192.0.2.1").forEach {
            assertFalse(it, NavigationPolicy.isPrivateHost(it))
        }
    }

    @Test
    fun `lan style names`() {
        listOf(
            "localhost", "kiosk.localhost", "nas.local", "a.b.local", "printer.lan",
            "ha.home.arpa", "svc.internal", "homeassistant", "grafana"
        ).forEach { assertTrue(it, NavigationPolicy.isPrivateHost(it)) }
    }

    @Test
    fun `public names are not private`() {
        listOf("example.com", "local.example.com", "lan.example.com", "evil-local.test", "").forEach {
            assertFalse(it, NavigationPolicy.isPrivateHost(it))
        }
    }

    // ---- warning shown when the owner saves a URL -------------------------------------------

    @Test
    fun `warning applies to http urls on public hosts only`() {
        assertTrue(NavigationPolicy.isCleartextToPublicHost("http://example.com/board"))
        assertTrue(NavigationPolicy.isCleartextToPublicHost("  HTTP://EXAMPLE.COM  "))
        assertTrue(NavigationPolicy.isCleartextToPublicHost("http://192.0.2.7:8080/"))
        assertFalse(NavigationPolicy.isCleartextToPublicHost("http://192.168.1.2/"))
        assertFalse(NavigationPolicy.isCleartextToPublicHost("http://nas.local/"))
        assertFalse(NavigationPolicy.isCleartextToPublicHost("https://example.com/"))
        assertFalse(NavigationPolicy.isCleartextToPublicHost(""))
        assertFalse(NavigationPolicy.isCleartextToPublicHost("example.com"))
    }

    // ---- internationalised hosts ----------------------------------------------------------
    // The owner may type a Unicode host; Chromium hands the WebView the punycode form.

    @Test
    fun `configured idn host matches its punycode form`() {
        val p = policy("https://b\u00fccher.example/shelf")
        assertTrue(p.allowsNavigation("https://xn--bcher-kva.example/"))
        assertTrue(p.allowsNavigation("https://xn--bcher-kva.example./x"))
        assertTrue(p.allowsNavigation("https://XN--BCHER-KVA.example:8443/x"))
        assertTrue(p.allowsNavigation("https://www.xn--bcher-kva.example/"))
        assertTrue(p.allowsNavigation("https://shop.xn--bcher-kva.example/"))
    }

    @Test
    fun `configured idn host is normalised for case and trailing dot`() {
        assertTrue(policy("https://B\u00dcCHER.example./").allowsNavigation("https://xn--bcher-kva.example/"))
        assertTrue(policy("https://www.b\u00fccher.example/").allowsNavigation("https://xn--bcher-kva.example/"))
    }

    @Test
    fun `configured punycode host matches itself and not lookalikes`() {
        val p = policy("https://xn--bcher-kva.example/")
        assertTrue(p.allowsNavigation("https://xn--bcher-kva.example/x"))
        assertFalse(p.allowsNavigation("https://xn--bcher-kva.example.evil.test/"))
        assertFalse(p.allowsNavigation("https://evilxn--bcher-kva.example/"))
        assertFalse(p.allowsNavigation("https://bucher.example/"))
    }

    @Test
    fun `configured idn host does not open lookalike hosts`() {
        val p = policy("https://b\u00fccher.example/")
        assertFalse(p.allowsNavigation("https://bucher.example/"))
        assertFalse(p.allowsNavigation("https://xn--bcher-kva.example.evil.test/"))
        assertFalse(p.allowsNavigation("https://xn--bcher-kva.evil.test/"))
    }

    @Test
    fun `non ascii targets still fail closed even when the idn host is configured`() {
        val p = policy("https://b\u00fccher.example/")
        assertFalse(p.allowsNavigation("https://b\u00fccher.example/"))
        assertFalse(p.allowsNavigation("https://bucher\u3002example/"))
    }

    @Test
    fun `idn host configured as http keeps the cleartext rules`() {
        val p = policy("http://b\u00fccher.example/")
        assertTrue(p.allowsNavigation("http://xn--bcher-kva.example/"))
        assertTrue(p.allowsRequest("http://xn--bcher-kva.example/img.png"))
        assertTrue(NavigationPolicy.isCleartextToPublicHost("http://b\u00fccher.example/"))
        assertFalse(policy("https://b\u00fccher.example/").allowsNavigation("http://xn--bcher-kva.example/"))
    }

    @Test
    fun `an unconvertible configured host is skipped without throwing`() {
        val tooLong = "a".repeat(64) + "\u00fc.example"
        val p = policy("https://$tooLong/", "https://example.com/")
        assertTrue(p.allowsNavigation("https://example.com/"))
        assertFalse(p.allowsNavigation("https://evil.test/"))
        assertFalse(NavigationPolicy.isCleartextToPublicHost("http://$tooLong/"))
    }

    @Test
    fun `default start url stays navigable`() {
        // KioskConfig.startUrl default
        assertEquals(true, policy("https://www.google.com").allowsNavigation("https://www.google.com/search?q=x"))
    }
    // ---- URLs typed without a scheme ------------------------------------------------------

    @Test
    fun `playlist url without scheme is allowed over https`() {
        // Regression: a playlist saved as "example.com" loaded but the allowlist could not
        // parse it, so the kiosk showed "Address not allowed" for its own page.
        val p = policy("example.com")
        assertTrue(p.allowsNavigation("https://example.com/"))
        assertFalse(p.allowsNavigation("http://example.com/"))
    }

    @Test
    fun `url without scheme gets https for public hosts and http for the LAN`() {
        assertEquals("https://example.com", NavigationPolicy.withDefaultScheme("example.com"))
        assertEquals("https://example.com/a?b=1", NavigationPolicy.withDefaultScheme("  example.com/a?b=1 "))
        assertEquals("http://192.168.1.10:8123", NavigationPolicy.withDefaultScheme("192.168.1.10:8123"))
        assertEquals("http://homeassistant.local", NavigationPolicy.withDefaultScheme("homeassistant.local"))
    }

    @Test
    fun `urls that already have a scheme are kept`() {
        assertEquals("http://example.com", NavigationPolicy.withDefaultScheme("http://example.com"))
        assertEquals("https://example.com/x", NavigationPolicy.withDefaultScheme("https://example.com/x"))
        assertEquals("about:blank", NavigationPolicy.withDefaultScheme("about:blank"))
    }

    // ---- WebSocket ------------------------------------------------------------------------
    // The WebView never reports a WebSocket to shouldInterceptRequest; WebSocketGuard asks this
    // method from the page. ws:// follows the same rule as plain http://.

    @Test
    fun `wss is allowed to any host`() {
        val p = policy("https://example.com/")
        assertTrue(p.allowsWebSocket("wss://example.com/socket"))
        assertTrue(p.allowsWebSocket("wss://other.example.net/socket"))
    }

    @Test
    fun `ws to LAN hosts is allowed`() {
        val p = policy("http://192.168.1.10:8123/")
        assertTrue(p.allowsWebSocket("ws://192.168.1.10:8123/api/websocket"))
        assertTrue(p.allowsWebSocket("ws://homeassistant.local:8123/api/websocket"))
        assertTrue(p.allowsWebSocket("ws://10.0.0.5/ws"))
        assertTrue(p.allowsWebSocket("ws://localhost:3000/ws"))
        assertTrue(p.allowsWebSocket("ws://[fd00::1]/ws"))
    }

    @Test
    fun `ws to a public host is blocked even when the host is allowlisted over https`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsWebSocket("ws://example.com/socket"))
        assertFalse(p.allowsWebSocket("ws://evil.test/socket"))
        assertFalse(p.allowsWebSocket("ws://198.51.100.7/socket"))
    }

    @Test
    fun `ws is allowed to a host the owner typed as http`() {
        val p = policy("http://example.com/board")
        assertTrue(p.allowsWebSocket("ws://example.com/socket"))
        assertTrue(p.allowsWebSocket("ws://live.example.com/socket"))
        assertFalse(p.allowsWebSocket("ws://example.net/socket"))
    }

    @Test
    fun `ws lookalikes of LAN names and numeric tricks are blocked`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsWebSocket("ws://192.168.1.10.evil.test/ws"))
        assertFalse(p.allowsWebSocket("ws://3232235777/ws"))
        assertFalse(p.allowsWebSocket("ws://192.168.1.10@evil.test/ws"))
        assertFalse(p.allowsWebSocket("ws://evil.test\\@192.168.1.10/ws"))
    }

    @Test
    fun `http and https forms of a websocket url follow the same rule`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsWebSocket("http://evil.test/socket"))
        assertTrue(p.allowsWebSocket("http://192.168.1.10/socket"))
        assertTrue(p.allowsWebSocket("https://evil.test/socket"))
    }

    @Test
    fun `websocket urls that do not parse or use other schemes fail closed`() {
        val p = policy("https://example.com/")
        assertFalse(p.allowsWebSocket(""))
        assertFalse(p.allowsWebSocket("ws://"))
        assertFalse(p.allowsWebSocket("ftp://192.168.1.10/"))
        assertFalse(p.allowsWebSocket("javascript:alert(1)"))
        assertFalse(p.allowsWebSocket("ws:/192.168.1.10/ws"))
    }
}
