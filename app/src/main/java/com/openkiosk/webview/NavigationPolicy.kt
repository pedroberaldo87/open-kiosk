package com.openkiosk.webview

/**
 * RED-phase stub: mirrors the behaviour before the fix (everything is allowed).
 * Replaced by the real implementation in the next commit.
 */
class NavigationPolicy(configuredUrls: Collection<String>) {
    fun allowsNavigation(target: String): Boolean = true

    fun allowsRequest(target: String): Boolean = true

    companion object {
        fun isPrivateHost(host: String): Boolean = false

        fun isCleartextToPublicHost(url: String): Boolean = false
    }
}
