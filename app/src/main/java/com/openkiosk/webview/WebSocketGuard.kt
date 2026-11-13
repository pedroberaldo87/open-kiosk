package com.openkiosk.webview

import android.webkit.JavascriptInterface

/**
 * Applies the plain-http rule to `ws://` WebSockets opened by page script.
 *
 * Why this exists: Android's network security config is one on/off switch for cleartext (it
 * cannot say "LAN only", see res/xml/network_security_config.xml), and the WebView never reports
 * a WebSocket to `shouldInterceptRequest`. So the page-side `WebSocket` constructor is wrapped by
 * a script that runs before any page script, in every frame, and asks [WebSocketPolicyBridge]
 * (which asks [NavigationPolicy.allowsWebSocket]) before the real socket is created.
 *
 * Limits, also stated in the README: it is a policy for the page the owner chose, not a sandbox.
 * A WebSocket made inside a Web Worker, or through an about:blank frame the page creates before
 * the script ran there, is not covered. When the WebView lacks document-start scripts the guard
 * is not installed (the kiosk logs it) and ws:// is unrestricted, as before.
 */
object WebSocketGuard {
    /** Name of the Java object the script reads. The page can see it and call it; it only answers yes/no. */
    const val BRIDGE_NAME = "OpenKioskPolicy"

    /**
     * Runs at document start. The bridge and the native constructor are captured in the closure
     * so later page code cannot swap them. `constructor` on the prototype is pointed at the
     * guarded function, otherwise `new ws.constructor(...)` would reach the original.
     * The real socket is built from the same absolute string that was checked.
     */
    const val SCRIPT = """
(function () {
  var bridge = window.OpenKioskPolicy;
  var Native = window.WebSocket;
  if (!bridge || typeof Native !== 'function' || Native.__openKioskGuard) { return; }
  var URLCtor = window.URL;
  var base = function () { return document.baseURI; };
  var guard = function Guarded(url, protocols) {
    if (!new.target) { return Native.apply(this, arguments); }
    var abs = null;
    try { abs = new URLCtor(String(url), base()).href; } catch (e) { abs = null; }
    if (abs === null) { return Reflect.construct(Native, arguments, new.target); }
    if (!bridge.allows(abs)) {
      throw new DOMException('Blocked by kiosk policy: ' + abs.split('://')[0] + '://', 'SecurityError');
    }
    return arguments.length > 1 ? new Native(abs, protocols) : new Native(abs);
  };
  guard.prototype = Native.prototype;
  ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (k) {
    Object.defineProperty(guard, k, { value: Native[k], enumerable: true });
  });
  Object.defineProperty(guard, '__openKioskGuard', { value: true });
  Object.defineProperty(Native.prototype, 'constructor', { value: guard, writable: true, configurable: true });
  Object.defineProperty(window, 'WebSocket', { value: guard, writable: true, configurable: true });
})();
"""
}

/** Answers "may the page open this WebSocket?" from the live policy (playlist edits apply at once). */
class WebSocketPolicyBridge(private val policy: () -> NavigationPolicy) {
    @JavascriptInterface
    fun allows(url: String?): Boolean = url != null && policy().allowsWebSocket(url)
}
