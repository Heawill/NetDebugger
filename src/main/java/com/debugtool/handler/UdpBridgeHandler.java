package com.debugtool.handler;

import com.debugtool.service.UdpClientService;
import com.debugtool.service.UdpServerService;
import org.cef.callback.CefQueryCallback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * Handles UDP Server and UDP Client bridge operations.
 */
class UdpBridgeHandler {

    private final ConcurrentHashMap<String, UdpServerService> udpServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UdpClientService> udpClients = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final BiConsumer<String, String> eventPusher;

    UdpBridgeHandler(ExecutorService executor, BiConsumer<String, String> eventPusher) {
        this.executor = executor;
        this.eventPusher = eventPusher;
    }

    void createUdpServer(String id, CefQueryCallback cb) {
        UdpServerService s = new UdpServerService(json -> eventPusher.accept(id, json));
        udpServers.put(id, s);
        BridgeUtils.ok(cb, id);
    }

    void createUdpClient(String id, CefQueryCallback cb) {
        UdpClientService c = new UdpClientService(json -> eventPusher.accept(id, json));
        udpClients.put(id, c);
        BridgeUtils.ok(cb, id);
    }

    void startUdpServer(String id, int port, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id);
        if (s != null) { s.start(port); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void stopUdpServer(String id, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id);
        if (s != null) { s.stop(); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void startUdpClient(String id, int port, CefQueryCallback cb) {
        UdpClientService c = udpClients.get(id);
        if (c != null) { c.bind(port); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void stopUdpClient(String id, CefQueryCallback cb) {
        UdpClientService c = udpClients.get(id);
        if (c != null) { c.unbind(); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void udpServerSend(String id, String host, int port, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id);
        if (s != null) { s.send(host, port, msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void udpServerSendToClient(String id, String clientId, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id);
        if (s != null) { s.sendToClient(clientId, msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void udpServerSendAll(String id, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id);
        if (s != null) { s.sendAll(msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void udpServerForgetClient(String id, String clientId, CefQueryCallback cb) {
        UdpServerService s = udpServers.get(id);
        if (s != null) { s.forgetClient(clientId); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void udpClientSend(String id, String host, int port, String msg, String encoding, String format, CefQueryCallback cb) {
        UdpClientService c = udpClients.get(id);
        if (c != null) { c.send(host, port, msg, encoding, format); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    /**
     * Try to remove an instance by id. Returns true if found and removed.
     */
    boolean removeInstance(String id) {
        UdpServerService us = udpServers.remove(id);
        if (us != null) { us.stop(); return true; }
        UdpClientService uc = udpClients.remove(id);
        if (uc != null) { uc.unbind(); return true; }
        return false;
    }

    void shutdown() {
        for (UdpServerService s : udpServers.values()) s.stop();
        for (UdpClientService c : udpClients.values()) c.unbind();
    }
}
