package com.debugtool.handler;

import com.debugtool.model.LogEntry;
import com.debugtool.service.*;
import com.debugtool.util.I18n;
import com.google.gson.*;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefQueryCallback;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JSBridgeHandler {

    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private CefBrowser browser;
    private final AtomicInteger idCounter = new AtomicInteger(1);

    private final ConcurrentHashMap<String, TcpServerService> tcpServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TcpClientService> tcpClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UdpServerService> udpServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UdpClientService> udpClients = new ConcurrentHashMap<>();

    private PersistenceService persistence;
    private volatile boolean restored = false;
    private java.util.function.Consumer<String> themeCallback;

    public void setBrowser(CefBrowser b) { this.browser = b; }
    public void setPersistence(PersistenceService p) { this.persistence = p; }
    public void setThemeCallback(java.util.function.Consumer<String> cb) { this.themeCallback = cb; }

    public void handleCefQuery(String reqJson, CefQueryCallback cb) {
        try {
            JsonObject req = JsonParser.parseString(reqJson).getAsJsonObject();
            String method = req.get("method").getAsString();
            JsonArray args = req.getAsJsonArray("args");
            dispatch(method, args, cb);
        } catch (Exception e) {
            if (cb != null) cb.failure(-1, "Error: " + e.getMessage());
        }
    }

    private void dispatch(String m, JsonArray a, CefQueryCallback cb) {
        String id = a.size() > 0 ? a.get(0).getAsString() : null;
        executor.submit(() -> {
            try {
                switch (m) {
                    case "createTcpServer": createTcpServer(id, cb); break;
                    case "createTcpClient": createTcpClient(id, cb); break;
                    case "createUdpServer": createUdpServer(id, cb); break;
                    case "createUdpClient": createUdpClient(id, cb); break;
                    case "removeInstance": removeInstance(id, cb); break;
                    case "startTcpServer": startTcpServer(id, a.get(1).getAsInt(), a.get(2).getAsInt(), cb); break;
                    case "stopTcpServer": stopTcpServer(id, cb); break;
                    case "startTcpClient": startTcpClient(id, a.get(1).getAsString(), a.get(2).getAsInt(), cb); break;
                    case "stopTcpClient": stopTcpClient(id, cb); break;
                    case "startUdpServer": startUdpServer(id, a.get(1).getAsInt(), cb); break;
                    case "stopUdpServer": stopUdpServer(id, cb); break;
                    case "startUdpClient": startUdpClient(id, a.get(1).getAsInt(), cb); break;
                    case "stopUdpClient": stopUdpClient(id, cb); break;
                    case "tcpServerSend": tcpServerSend(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), a.get(4).getAsString(), cb); break;
                    case "tcpServerSendAll": tcpServerSendAll(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), cb); break;
                    case "tcpServerDisconnectClient": tcpServerDisconnectClient(id, a.get(1).getAsString(), cb); break;
                    case "tcpClientSend": tcpClientSend(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), cb); break;
                    case "udpServerSend": udpServerSend(id, a.get(1).getAsString(), a.get(2).getAsInt(), a.get(3).getAsString(), a.get(4).getAsString(), a.get(5).getAsString(), cb); break;
                    case "udpServerSendToClient": udpServerSendToClient(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), a.get(4).getAsString(), cb); break;
                    case "udpServerSendAll": udpServerSendAll(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), cb); break;
                    case "udpServerForgetClient": udpServerForgetClient(id, a.get(1).getAsString(), cb); break;
                    case "udpClientSend": udpClientSend(id, a.get(1).getAsString(), a.get(2).getAsInt(), a.get(3).getAsString(), a.get(4).getAsString(), a.get(5).getAsString(), cb); break;
                    case "restoreState": restoreState(cb); break;
                    case "persistSessions": persistSessions(a.get(0).getAsString(), cb); break;
                    case "persistConfig": persistConfig(a.get(0).getAsString(), a.get(1).getAsString(), cb); break;
                    case "clearLogs": clearLogs(id, cb); break;
                    case "setLanguage": setLanguage(a.get(0).getAsString(), cb); break;
                    default: if (cb != null) cb.failure(-2, "Unknown: " + m);
                }
            } catch (Exception e) {
                if (cb != null) cb.failure(-5, e.getMessage());
            }
        });
    }

    private void ok(CefQueryCallback c, String r) { if (c != null) c.success(r != null ? r : "ok"); }

    private void createTcpServer(String id, CefQueryCallback cb) {
        TcpServerService s = new TcpServerService(json -> pushEvent(id, "tcpServer", json));
        tcpServers.put(id, s);
        ok(cb, id);
    }
    private void createTcpClient(String id, CefQueryCallback cb) {
        TcpClientService c = new TcpClientService(json -> pushEvent(id, "tcpClient", json));
        tcpClients.put(id, c);
        ok(cb, id);
    }
    private void createUdpServer(String id, CefQueryCallback cb) {
        UdpServerService s = new UdpServerService(json -> pushEvent(id, "udpServer", json));
        udpServers.put(id, s);
        ok(cb, id);
    }
    private void createUdpClient(String id, CefQueryCallback cb) {
        UdpClientService c = new UdpClientService(json -> pushEvent(id, "udpClient", json));
        udpClients.put(id, c);
        ok(cb, id);
    }

    private void removeInstance(String id, CefQueryCallback cb) {
        TcpServerService ts = tcpServers.remove(id); if (ts != null) { ts.stop(); ok(cb, "removed"); return; }
        TcpClientService tc = tcpClients.remove(id); if (tc != null) { tc.disconnect(); ok(cb, "removed"); return; }
        UdpServerService us = udpServers.remove(id); if (us != null) { us.stop(); ok(cb, "removed"); return; }
        UdpClientService uc = udpClients.remove(id); if (uc != null) { uc.unbind(); ok(cb, "removed"); return; }
        if (cb != null) cb.failure(-3, "Not found: " + id);
    }

    private void startTcpServer(String id, int port, int maxConn, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id); if (s != null) { s.start(port, maxConn); ok(cb, null); } else fail(cb, id);
    }
    private void stopTcpServer(String id, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id); if (s != null) { s.stop(); ok(cb, null); } else fail(cb, id);
    }
    private void startTcpClient(String id, String host, int port, CefQueryCallback cb) {
        TcpClientService c = tcpClients.get(id); if (c != null) { c.connect(host, port); ok(cb, null); } else fail(cb, id);
    }
    private void stopTcpClient(String id, CefQueryCallback cb) {
        TcpClientService c = tcpClients.get(id); if (c != null) { c.disconnect(); ok(cb, null); } else fail(cb, id);
    }
    private void startUdpServer(String id, int port, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id); if (s != null) { s.start(port); ok(cb, null); } else fail(cb, id);
    }
    private void stopUdpServer(String id, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id); if (s != null) { s.stop(); ok(cb, null); } else fail(cb, id);
    }
    private void startUdpClient(String id, int port, CefQueryCallback cb) {
        UdpClientService c = udpClients.get(id); if (c != null) { c.bind(port); ok(cb, null); } else fail(cb, id);
    }
    private void stopUdpClient(String id, CefQueryCallback cb) {
        UdpClientService c = udpClients.get(id); if (c != null) { c.unbind(); ok(cb, null); } else fail(cb, id);
    }

    private void tcpServerSend(String id, String clientId, String msg, String encoding, String format, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id); if (s != null) { s.send(clientId, msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }
    private void tcpServerSendAll(String id, String msg, String encoding, String format, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id); if (s != null) { s.sendAll(msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }
    private void tcpServerDisconnectClient(String id, String clientId, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id); if (s != null) { s.disconnectClient(clientId); ok(cb, null); } else fail(cb, id);
    }
    private void tcpClientSend(String id, String msg, String encoding, String format, CefQueryCallback cb) {
        TcpClientService c = tcpClients.get(id); if (c != null) { c.send(msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }
    private void udpServerSend(String id, String host, int port, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id); if (s != null) { s.send(host, port, msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }
    private void udpServerSendToClient(String id, String clientId, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id); if (s != null) { s.sendToClient(clientId, msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }
    private void udpServerSendAll(String id, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id); if (s != null) { s.sendAll(msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }
    private void udpServerForgetClient(String id, String clientId, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id); if (s != null) { s.forgetClient(clientId); ok(cb, null); } else fail(cb, id);
    }
    private void udpClientSend(String id, String host, int port, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpClientService c = udpClients.get(id); if (c != null) { c.send(host, port, msg, encoding, format); ok(cb, null); } else fail(cb, id);
    }

    private void fail(CefQueryCallback cb, String id) { if (cb != null) cb.failure(-3, "Not found: " + id); }

    // ==================== Push Events ====================

    private void pushEvent(String id, String target, String json) {
        JsonObject evt = JsonParser.parseString(json).getAsJsonObject();
        evt.addProperty("id", id);

        // Persist log entries
        String type = evt.has("type") ? evt.get("type").getAsString() : "";
        if ("log".equals(type) && persistence != null) {
            try {
                JsonElement data = evt.get("data");
                JsonObject logObj = data.isJsonPrimitive()
                    ? JsonParser.parseString(data.getAsString()).getAsJsonObject()
                    : data.getAsJsonObject();
                persistence.appendLog(id, LogEntry.fromJson(logObj));
            } catch (Exception e) {
                // Ignore persistence errors for individual log entries
            }
        }

        pushToFrontend(gson.toJson(evt));
    }

    private void pushToFrontend(String json) {
        if (browser != null) {
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            // atob() only handles ASCII/Latin-1; use decodeURIComponent trick to decode UTF-8 bytes
            browser.executeJavaScript(
                "if(window.handleBridgeEvent) window.handleBridgeEvent(decodeURIComponent(atob('"
                    + encoded + "').split('').map(function(c){return'%'+('00'+c.charCodeAt(0).toString(16)).slice(-2)}).join('')));",
                browser.getURL(), 0);
        }
    }

    public void shutdown() {
        for (TcpServerService s : tcpServers.values()) s.stop();
        for (TcpClientService c : tcpClients.values()) c.disconnect();
        for (UdpServerService s : udpServers.values()) s.stop();
        for (UdpClientService c : udpClients.values()) c.unbind();
        executor.shutdownNow();
        if (persistence != null) persistence.shutdown();
    }

    // ==================== Persistence ====================

    /**
     * Push restored state (sessions + logs + config) to the frontend.
     * Called after the bridge is ready.
     */
    public void pushRestoreState() {
        if (persistence == null || restored) return;
        restored = true;

        Map<String, String> config = persistence.loadConfig();
        JsonArray sessions = persistence.loadSessions();
        JsonArray sessionsWithLogs = new JsonArray();

        for (JsonElement el : sessions) {
            JsonObject sessionObj = el.getAsJsonObject();
            String sid = sessionObj.get("id").getAsString();

            // Load logs for this session
            List<LogEntry> logs = persistence.loadLogs(sid);
            JsonArray logsArr = new JsonArray();
            for (LogEntry entry : logs) {
                logsArr.add(entry.toJsonElement());
            }
            sessionObj.add("logs", logsArr);

            sessionsWithLogs.add(sessionObj);
        }

        JsonObject state = new JsonObject();
        state.add("sessions", sessionsWithLogs);
        state.addProperty("theme", config.getOrDefault("theme", "auto"));
        state.addProperty("activeTab", config.getOrDefault("activeTab", "tcpServer"));
        state.addProperty("language", config.getOrDefault("language", I18n.getLocaleTag()));

        pushStateEvent("restoreState", gson.toJson(state));
    }

    private void restoreState(CefQueryCallback cb) {
        restored = false;
        pushRestoreState();
        ok(cb, "ok");
    }

    private void persistSessions(String sessionsJson, CefQueryCallback cb) {
        if (persistence == null) { ok(cb, "ok"); return; }
        try {
            JsonArray arr = JsonParser.parseString(sessionsJson).getAsJsonArray();
            persistence.saveSessions(arr);
            ok(cb, "ok");
        } catch (Exception e) {
            if (cb != null) cb.failure(-1, "persistSessions: " + e.getMessage());
        }
    }

    private void persistConfig(String key, String value, CefQueryCallback cb) {
        if (persistence == null) { ok(cb, "ok"); return; }
        try {
            Map<String, String> config = persistence.loadConfig();
            config.put(key, value);
            persistence.saveConfig(config);
            if ("theme".equals(key) && themeCallback != null) {
                themeCallback.accept(value);
            }
            ok(cb, "ok");
        } catch (Exception e) {
            if (cb != null) cb.failure(-1, "persistConfig: " + e.getMessage());
        }
    }

    private void clearLogs(String sessionId, CefQueryCallback cb) {
        if (persistence != null) {
            persistence.clearLogs(sessionId);
        }
        ok(cb, "ok");
    }

    private void setLanguage(String langTag, CefQueryCallback cb) {
        I18n.setLocaleTag(langTag);
        if (persistence != null) {
            Map<String, String> config = persistence.loadConfig();
            config.put("language", langTag);
            persistence.saveConfig(config);
        }
        ok(cb, "ok");
    }

    private void pushStateEvent(String type, String json) {
        if (browser != null) {
            JsonObject state = new JsonObject();
            state.addProperty("type", type);
            state.addProperty("data", json);
            pushToFrontend(gson.toJson(state));
        }
    }
}
