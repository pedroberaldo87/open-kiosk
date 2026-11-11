package com.openkiosk.webview

/**
 * Local page shown when the policy refuses a main-frame load, so a blocked address is visible on
 * the screen instead of a blank page. Self-contained: inline CSS, no scripts, no network.
 *
 * Pure Kotlin so the escaping is unit tested on the JVM; the texts come from string resources.
 */
object BlockedPage {

    /** Returns the HTML for the blocked page. [host] is the blocked host, or null if unknown. */
    fun html(title: String, message: String, host: String?): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>").append(escape(title)).append("</title>")
        append("<style>")
        append("html,body{height:100%;margin:0;background:#1a1a1a;color:#fff;")
        append("font-family:sans-serif;text-align:center}")
        append("body{display:flex;flex-direction:column;align-items:center;justify-content:center;")
        append("padding:0 24px;box-sizing:border-box}")
        append("h1{font-size:26px;margin:0 0 12px}")
        append("p{font-size:18px;margin:0 0 12px;max-width:640px;color:#ddd}")
        append("code{font-size:20px;color:#fff;background:#333;padding:4px 10px;border-radius:6px}")
        append("</style></head><body>")
        append("<h1>").append(escape(title)).append("</h1>")
        append("<p>").append(escape(message)).append("</p>")
        if (!host.isNullOrEmpty()) append("<p><code>").append(escape(host)).append("</code></p>")
        append("</body></html>")
    }

    internal fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
