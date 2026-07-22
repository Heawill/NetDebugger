package com.debugtool.handler;

import org.cef.callback.CefQueryCallback;

/**
 * Shared utility methods for bridge handlers.
 */
final class BridgeUtils {

    private BridgeUtils() {}

    static void ok(CefQueryCallback c, String r) {
        if (c != null) c.success(r != null ? r : "ok");
    }

    static void fail(CefQueryCallback cb, String id) {
        if (cb != null) cb.failure(-3, "Not found: " + id);
    }
}
