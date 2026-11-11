package com.openkiosk.webview

/**
 * Decides what the kiosk WebView may navigate to and which plain-http requests it may make.
 *
 * Pure Kotlin on purpose (no android.net.Uri, no java.net.URI): it is unit tested on the JVM, and
 * both parsers either are stubbed there or reject URLs the browser accepts.
 *
 * Rules:
 *  - Navigation is confined to the hosts of the configured URLs (start URL + playlist). A host
 *    matches itself and its subdomains; `www.` and the apex are interchangeable. IP literals match
 *    only themselves. Ports are ignored.
 *  - Only http and https are navigable.
 *  - https is always fine on an allowlisted host. http is fine only for private/LAN hosts
 *    (RFC 1918, loopback, link-local, .local/.lan/... and single-label names) or for hosts the
 *    owner explicitly wrote as `http://` in the configuration. Anything else is a downgrade an
 *    on-path attacker could rewrite.
 *
 * Every parsing doubt fails closed (blocked), because the WebView normalises URLs in ways a
 * hand-written parser can disagree with.
 */
class NavigationPolicy(configuredUrls: Collection<String>) {

    /** Allowlisted hosts, `www.` stripped. */
    private val allowedHosts: Set<String>

    /** Hosts the owner wrote as `http://`, `www.` stripped. */
    private val cleartextHosts: Set<String>

    init {
        val all = mutableSetOf<String>()
        val clear = mutableSetOf<String>()
        for (raw in configuredUrls) {
            val parsed = parseUrl(raw) ?: continue
            if (parsed.scheme != "http" && parsed.scheme != "https") continue
            val base = parsed.host.removePrefix("www.")
            all += base
            if (parsed.scheme == "http") clear += base
        }
        allowedHosts = all
        cleartextHosts = clear
    }

    /** Main-frame or link navigation, including server redirects. */
    fun allowsNavigation(target: String): Boolean {
        val parsed = parseUrl(target) ?: return false
        if (parsed.scheme != "http" && parsed.scheme != "https") return false
        if (!matchesAny(parsed.host, allowedHosts)) return false
        return parsed.scheme == "https" || cleartextAllowedFor(parsed.host)
    }

    /**
     * Any request the page makes (scripts, images, XHR...). Only plain http is restricted here;
     * https sub-resources (CDNs, fonts) must keep working, and the WebView already refuses http
     * sub-resources inside https pages.
     */
    fun allowsRequest(target: String): Boolean {
        if (!target.trimStart().startsWith("http:", ignoreCase = true)) return true
        val parsed = parseUrl(target) ?: return false
        return cleartextAllowedFor(parsed.host)
    }

    private fun cleartextAllowedFor(host: String): Boolean =
        isPrivateHost(host) || matchesAny(host, cleartextHosts)

    private fun matchesAny(host: String, bases: Set<String>): Boolean =
        bases.any { base ->
            host == base ||
                (!isIpLiteral(base) && (host == "www.$base" || host.endsWith(".$base")))
        }

    private class ParsedUrl(val scheme: String, val host: String)

    companion object {
        private val PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)")
        private val HOST_NAME = Regex("^[a-z0-9._-]+$")
        private val IPV4 = Regex("^(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}$")
        private val IPV6 = Regex("^[0-9a-f:]+$")
        private val SINGLE_LABEL = Regex("^[a-z][a-z0-9_-]*$")
        private val PORT = Regex("^[0-9]*$")

        // Suffixes that never resolve on the public internet (RFC 6761, RFC 6762, RFC 8375,
        // ICANN .internal) plus ".lan", the de-facto home-router suffix.
        private val PRIVATE_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal")

        /**
         * True when [host] can only be reached on the local network: private/loopback/link-local IPs,
         * LAN name suffixes, `localhost` and single-label names. CGNAT (100.64/10, e.g. Tailscale)
         * is deliberately not included; the owner can write such a host as `http://` explicitly.
         */
        fun isPrivateHost(host: String): Boolean {
            val h = host.lowercase().trimEnd('.')
            if (h.isEmpty()) return false
            if (IPV4.matches(h)) return isPrivateIpv4(h)
            if (h.contains(':')) return isPrivateIpv6(h)
            if (h == "localhost") return true
            if (PRIVATE_SUFFIXES.any { h.endsWith(it) }) return true
            // Must start with a letter so numeric tricks (decimal/hex IPv4 such as 3232235777 or
            // 0xc0000201) never count as a "name".
            return SINGLE_LABEL.matches(h)
        }

        /** True for an `http://` URL whose host is not private: the owner should be warned. */
        fun isCleartextToPublicHost(url: String): Boolean {
            val parsed = parseUrl(url) ?: return false
            return parsed.scheme == "http" && !isPrivateHost(parsed.host)
        }

        private fun isIpLiteral(host: String): Boolean = IPV4.matches(host) || host.contains(':')

        private fun isPrivateIpv4(h: String): Boolean {
            val o = h.split('.').map { it.toInt() }
            if (o.any { it > 255 }) return false
            return o[0] == 10 ||
                o[0] == 127 ||
                (o[0] == 172 && o[1] in 16..31) ||
                (o[0] == 192 && o[1] == 168) ||
                (o[0] == 169 && o[1] == 254)
        }

        private fun isPrivateIpv6(h: String): Boolean {
            if (!IPV6.matches(h) || h.count { it == ':' } < 2) return false
            if (h == "::1") return true
            val first = h.substringBefore(':')
            if (first.isEmpty() || first.length > 4) return false
            val value = first.toInt(16)
            // fc00::/7 unique-local, fe80::/10 link-local
            return value in 0xfc00..0xfdff || value in 0xfe80..0xfebf
        }

        private fun parseUrl(raw: String): ParsedUrl? {
            val match = PREFIX.find(raw.trim()) ?: return null
            val scheme = match.groupValues[1].lowercase()
            val authority = match.groupValues[2]
            // Backslash, whitespace/control and non-ASCII characters are normalised by the browser
            // (a backslash acts as a slash, tabs are dropped, U+3002 becomes a dot), so what we
            // would compare is not what it would load. Refuse them.
            if (authority.any { it <= ' ' || it == '\u007f' || it == '\\' || it.code > 0x7e }) return null
            // The browser takes the host after the LAST '@'.
            val hostPort = authority.substringAfterLast('@')
            val host: String
            if (hostPort.startsWith("[")) {
                val end = hostPort.indexOf(']')
                if (end < 0) return null
                host = hostPort.substring(1, end).lowercase()
                val rest = hostPort.substring(end + 1)
                if (rest.isNotEmpty() && !(rest.startsWith(":") && PORT.matches(rest.substring(1)))) return null
                if (!IPV6.matches(host)) return null
            } else {
                val colon = hostPort.lastIndexOf(':')
                val name = if (colon >= 0) hostPort.substring(0, colon) else hostPort
                if (colon >= 0 && !PORT.matches(hostPort.substring(colon + 1))) return null
                host = name.lowercase().trimEnd('.')
                if (!HOST_NAME.matches(host) || host.startsWith(".") || host.contains("..")) return null
            }
            if (host.isEmpty()) return null
            return ParsedUrl(scheme, host)
        }
    }
}
