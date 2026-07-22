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

    private final TcpBridgeHandler tcpHandler;
    private final UdpBridgeHandler udpHandler;
    private final SshBridgeHandler sshHandler;

    private PersistenceService persistence;
    private volatile boolean restored = false;
    private java.util.function.Consumer<String> themeCallback;

    public JSBridgeHandler() {
        this.tcpHandler = new TcpBridgeHandler(executor, this::pushEvent);
        this.udpHandler = new UdpBridgeHandler(executor, this::pushEvent);
        this.sshHandler = new SshBridgeHandler(executor, this::pushEvent);
    }

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
                    // === TCP ===
                    case "createTcpServer":         tcpHandler.createTcpServer(id, cb); break;
                    case "createTcpClient":         tcpHandler.createTcpClient(id, cb); break;
                    case "startTcpServer":          tcpHandler.startTcpServer(id, a.get(1).getAsInt(), a.get(2).getAsInt(), cb); break;
                    case "stopTcpServer":           tcpHandler.stopTcpServer(id, cb); break;
                    case "startTcpClient":          tcpHandler.startTcpClient(id, a.get(1).getAsString(), a.get(2).getAsInt(), cb); break;
                    case "stopTcpClient":           tcpHandler.stopTcpClient(id, cb); break;
                    case "tcpServerSend":           tcpHandler.tcpServerSend(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), a.get(4).getAsString(), cb); break;
                    case "tcpServerSendAll":        tcpHandler.tcpServerSendAll(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), cb); break;
                    case "tcpServerDisconnectClient": tcpHandler.tcpServerDisconnectClient(id, a.get(1).getAsString(), cb); break;
                    case "tcpClientSend":           tcpHandler.tcpClientSend(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), cb); break;
                    // === UDP ===
                    case "createUdpServer":         udpHandler.createUdpServer(id, cb); break;
                    case "createUdpClient":         udpHandler.createUdpClient(id, cb); break;
                    case "startUdpServer":          udpHandler.startUdpServer(id, a.get(1).getAsInt(), cb); break;
                    case "stopUdpServer":           udpHandler.stopUdpServer(id, cb); break;
                    case "startUdpClient":          udpHandler.startUdpClient(id, a.get(1).getAsInt(), cb); break;
                    case "stopUdpClient":           udpHandler.stopUdpClient(id, cb); break;
                    case "udpServerSend":           udpHandler.udpServerSend(id, a.get(1).getAsString(), a.get(2).getAsInt(), a.get(3).getAsString(), a.get(4).getAsString(), a.get(5).getAsString(), cb); break;
                    case "udpServerSendToClient":   udpHandler.udpServerSendToClient(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), a.get(4).getAsString(), cb); break;
                    case "udpServerSendAll":        udpHandler.udpServerSendAll(id, a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString(), cb); break;
                    case "udpServerForgetClient":   udpHandler.udpServerForgetClient(id, a.get(1).getAsString(), cb); break;
                    case "udpClientSend":           udpHandler.udpClientSend(id, a.get(1).getAsString(), a.get(2).getAsInt(), a.get(3).getAsString(), a.get(4).getAsString(), a.get(5).getAsString(), cb); break;
                    // === SSH ===
                    case "createSshClient":         sshHandler.createSshClient(id, cb); break;
                    case "connectSsh":              sshHandler.connectSsh(id, a.get(1).getAsString(), a.get(2).getAsInt(), a.get(3).getAsString(), a.get(4).getAsString(), a.get(5).getAsInt(), a.get(6).getAsInt(), cb); break;
                    case "disconnectSsh":           sshHandler.disconnectSsh(id, cb); break;
                    case "sshInput":                sshHandler.sshInput(id, a.get(1).getAsString(), cb); break;
                    case "resizeSshPty":            sshHandler.resizeSshPty(id, a.get(1).getAsInt(), a.get(2).getAsInt(), cb); break;
                    case "sshPaste":                sshHandler.sshPaste(id, cb); break;
                    case "sftpList":                sshHandler.sftpList(id, a.size() > 1 ? a.get(1).getAsString() : "/", cb); break;
                    case "sftpDownload":            sshHandler.sftpDownload(id, a.get(1).getAsString(), a.size()>2 ? a.get(2).getAsString() : "", cb); break;
                    case "sftpUpload":              sshHandler.sftpUpload(id, a.get(1).getAsString(), a.get(2).getAsString(), cb); break;
                    case "sftpDelete":              sshHandler.sftpDelete(id, a.get(1).getAsString(), cb); break;
                    case "sftpRename":              sshHandler.sftpRename(id, a.get(1).getAsString(), a.get(2).getAsString(), cb); break;
                    case "pickAndUpload":           sshHandler.pickAndUpload(id, cb); break;
                    // === Shared ===
                    case "removeInstance":          removeInstance(id, cb); break;
                    case "restoreState":            restoreState(cb); break;
                    case "persistSessions":         persistSessions(a.get(0).getAsString(), cb); break;
                    case "persistConfig":           persistConfig(a.get(0).getAsString(), a.get(1).getAsString(), cb); break;
                    case "clearLogs":               clearLogs(id, cb); break;
                    case "setLanguage":             setLanguage(a.get(0).getAsString(), cb); break;
                    default:                        if (cb != null) cb.failure(-2, "Unknown: " + m);
                }
            } catch (Exception e) {
                if (cb != null) cb.failure(-5, e.getMessage());
            }
        });
    }

    // ==================== Shared Operations ====================

    private void removeInstance(String id, CefQueryCallback cb) {
        if (tcpHandler.removeInstance(id)) { BridgeUtils.ok(cb, "removed"); return; }
        if (udpHandler.removeInstance(id)) { BridgeUtils.ok(cb, "removed"); return; }
        if (sshHandler.removeInstance(id)) { BridgeUtils.ok(cb, "removed"); return; }
        BridgeUtils.fail(cb, id);
    }

    // ==================== Push Events ====================

    private void pushEvent(String id, String json) {
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

    public SshClientService getSshClient(String id) {
        return sshHandler.getSshClient(id);
    }

    public void shutdown() {
        tcpHandler.shutdown();
        udpHandler.shutdown();
        sshHandler.shutdown();
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
        BridgeUtils.ok(cb, "ok");
    }

    private void persistSessions(String sessionsJson, CefQueryCallback cb) {
        if (persistence == null) { BridgeUtils.ok(cb, "ok"); return; }
        try {
            JsonArray arr = JsonParser.parseString(sessionsJson).getAsJsonArray();
            persistence.saveSessions(arr);
            BridgeUtils.ok(cb, "ok");
        } catch (Exception e) {
            if (cb != null) cb.failure(-1, "persistSessions: " + e.getMessage());
        }
    }

    private void persistConfig(String key, String value, CefQueryCallback cb) {
        if (persistence == null) { BridgeUtils.ok(cb, "ok"); return; }
        try {
            Map<String, String> config = persistence.loadConfig();
            config.put(key, value);
            persistence.saveConfig(config);
            if ("theme".equals(key) && themeCallback != null) {
                themeCallback.accept(value);
            }
            BridgeUtils.ok(cb, "ok");
        } catch (Exception e) {
            if (cb != null) cb.failure(-1, "persistConfig: " + e.getMessage());
        }
    }

    private void clearLogs(String sessionId, CefQueryCallback cb) {
        if (persistence != null) {
            persistence.clearLogs(sessionId);
        }
        BridgeUtils.ok(cb, "ok");
    }

    private void setLanguage(String langTag, CefQueryCallback cb) {
        I18n.setLocaleTag(langTag);
        if (persistence != null) {
            Map<String, String> config = persistence.loadConfig();
            config.put("language", langTag);
            persistence.saveConfig(config);
        }
        BridgeUtils.ok(cb, "ok");
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
