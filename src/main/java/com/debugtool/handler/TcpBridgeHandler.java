package com.debugtool.handler;

import com.debugtool.service.TcpClientService;
import com.debugtool.service.TcpServerService;
import org.cef.callback.CefQueryCallback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * Handles TCP Server and TCP Client bridge operations.
 */
class TcpBridgeHandler {

    private final ConcurrentHashMap<String, TcpServerService> tcpServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TcpClientService> tcpClients = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final BiConsumer<String, String> eventPusher;

    TcpBridgeHandler(ExecutorService executor, BiConsumer<String, String> eventPusher) {
        this.executor = executor;
        this.eventPusher = eventPusher;
    }

    void createTcpServer(String id, CefQueryCallback cb) {
        TcpServerService s = new TcpServerService(json -> eventPusher.accept(id, json));
        tcpServers.put(id, s);
        BridgeUtils.ok(cb, id);
    }

    void createTcpClient(String id, CefQueryCallback cb) {
        TcpClientService c = new TcpClientService(json -> eventPusher.accept(id, json));
        tcpClients.put(id, c);
        BridgeUtils.ok(cb, id);
    }

    void startTcpServer(String id, int port, int maxConn, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id);
        if (s != null) { s.start(port, maxConn); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void stopTcpServer(String id, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id);
        if (s != null) { s.stop(); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void startTcpClient(String id, String host, int port, CefQueryCallback cb) {
        TcpClientService c = tcpClients.get(id);
        if (c != null) { c.connect(host, port); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void stopTcpClient(String id, CefQueryCallback cb) {
        TcpClientService c = tcpClients.get(id);
        if (c != null) { c.disconnect(); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void tcpServerSend(String id, String clientId, String msg, String encoding, String format, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id);
        if (s != null) { s.send(clientId, msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void tcpServerSendAll(String id, String msg, String encoding, String format, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id);
        if (s != null) { s.sendAll(msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void tcpServerDisconnectClient(String id, String clientId, CefQueryCallback cb) {
        TcpServerService s = tcpServers.get(id);
        if (s != null) { s.disconnectClient(clientId); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void tcpClientSend(String id, String msg, String encoding, String format, CefQueryCallback cb) {
        TcpClientService c = tcpClients.get(id);
        if (c != null) { c.send(msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    /**
     * Try to remove an instance by id. Returns true if found and removed.
     */
    boolean removeInstance(String id) {
        TcpServerService ts = tcpServers.remove(id);
        if (ts != null) { ts.stop(); return true; }
        TcpClientService tc = tcpClients.remove(id);
        if (tc != null) { tc.disconnect(); return true; }
        return false;
    }

    void shutdown() {
        for (TcpServerService s : tcpServers.values()) s.stop();
        for (TcpClientService c : tcpClients.values()) c.disconnect();
    }
}
