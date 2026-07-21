package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.debugtool.util.HexUtil;
import com.debugtool.util.I18n;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * UDP Server - listens for UDP datagrams and can reply.
 */
public class UdpServerService {

    private final Consumer<String> onEvent;
    private final Gson gson = new Gson();
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    private DatagramSocket socket;
    private volatile boolean running = false;
    private int port;
    private final Set<String> knownClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, InetSocketAddress> clientAddresses = new ConcurrentHashMap<>();

    public UdpServerService(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

    public synchronized void start(int port) {
        if (running) {
            emit("error", "udpServer", I18n.get("udp.server.already_running"));
            return;
        }
        this.port = port;
        logs.clear();

        try {
            socket = new DatagramSocket(null);
            socket.bind(new java.net.InetSocketAddress(port));
            running = true;
            logSystem(I18n.get("udp.server.started", port), "system", "udp.server.started", String.valueOf(port));
            emit("serverStarted", "udpServer", port);

            Thread receiveThread = new Thread(() -> {
                byte[] buffer = new byte[65535];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                        String message = HexUtil.toHexString(data);
                        String peer = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                        if (knownClients.add(peer)) {
                            clientAddresses.put(peer, new InetSocketAddress(packet.getAddress(), packet.getPort()));
                            emitClientList();
                        }
                        log(LogEntry.Direction.RECEIVED, message, peer);
                        emit("messageReceived", "udpServer", gson.toJson(new String[][]{
                            {"from", peer}, {"message", message}
                        }));
                    } catch (Exception e) {
                        if (running) {
                            logSystem(I18n.get("udp.server.receive_error", e.getMessage()), "system", "udp.server.receive_error", e.getMessage());
                            emit("error", "udpServer", e.getMessage());
                        }
                    }
                }
            }, "UDP-Server-Receiver");
            receiveThread.setDaemon(true);
            receiveThread.start();

        } catch (SocketException e) {
            running = false;
            logSystem(I18n.get("udp.server.start_failed", e.getMessage()), "system", "udp.server.start_failed", e.getMessage());
            emit("error", "udpServer", e.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        knownClients.clear();
        clientAddresses.clear();
        logSystem(I18n.get("udp.server.stopped"), "system", "udp.server.stopped");
        emit("serverStopped", "udpServer", 0);
        emitClientList();
    }

    public void send(String targetHost, int targetPort, String message, String encoding, String format) {
        if (!running || socket == null) {
            emit("error", "udpServer", I18n.get("udp.server.not_running"));
            return;
        }
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "udpServer", err);
                return;
            }
        }
        try {
            byte[] data = buildData(message, encoding, format);
            InetAddress addr = InetAddress.getByName(targetHost);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, targetPort);
            socket.send(packet);
            log(LogEntry.Direction.SENT, formatMessage(message, format), targetHost + ":" + targetPort);
            emit("messageSent", "udpServer", targetHost + ":" + targetPort);
        } catch (Exception e) {
            emit("error", "udpServer", I18n.get("udp.server.send_failed", e.getMessage()));
        }
    }

    public void sendToClient(String clientId, String message, String encoding, String format) {
        InetSocketAddress addr = clientAddresses.get(clientId);
        if (addr == null) {
            emit("error", "udpServer", I18n.get("udp.server.client_not_found", clientId));
            return;
        }
        if (!running || socket == null) {
            emit("error", "udpServer", I18n.get("udp.server.not_running"));
            return;
        }
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "udpServer", err);
                return;
            }
        }
        try {
            byte[] data = buildData(message, encoding, format);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr.getAddress(), addr.getPort());
            socket.send(packet);
            log(LogEntry.Direction.SENT, formatMessage(message, format), clientId);
            emit("messageSent", "udpServer", clientId);
        } catch (Exception e) {
            emit("error", "udpServer", I18n.get("udp.server.send_failed", e.getMessage()));
        }
    }

    public void sendAll(String message, String encoding, String format) {
        if (!running || socket == null) {
            emit("error", "udpServer", I18n.get("udp.server.not_running"));
            return;
        }
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "udpServer", err);
                return;
            }
        }
        byte[] data = buildData(message, encoding, format);
        for (Map.Entry<String, InetSocketAddress> entry : clientAddresses.entrySet()) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length,
                    entry.getValue().getAddress(), entry.getValue().getPort());
                socket.send(packet);
                log(LogEntry.Direction.SENT, formatMessage(message, format), entry.getKey());
            } catch (Exception e) {
                emit("error", "udpServer", I18n.get("udp.server.send_to", entry.getKey(), e.getMessage()));
            }
        }
        emit("messageSentAll", "udpServer", message);
    }

    private byte[] buildData(String message, String encoding, String format) {
        if ("hex".equals(format)) {
            return HexUtil.parseHex(message);
        }
        try { return message.getBytes(encoding != null ? encoding : "UTF-8"); } catch (Exception e) { return message.getBytes(); }
    }

    private String formatMessage(String message, String format) {
        if ("hex".equals(format)) {
            try { return HexUtil.toHexString(HexUtil.parseHex(message)); } catch (Exception e) { return message; }
        }
        return message;
    }

    public void forgetClient(String clientId) {
        knownClients.remove(clientId);
        clientAddresses.remove(clientId);
        emitClientList();
    }

    public String getClientsJson() {
        return gson.toJson(new ArrayList<>(knownClients));
    }

    private void emitClientList() {
        emit("clientList", "udpServer", getClientsJson());
    }

    public String getStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("running", running);
        obj.addProperty("port", port);
        return gson.toJson(obj);
    }

    public int getPort() { return port; }
    public boolean isRunning() { return running; }

    private void log(LogEntry.Direction dir, String msg, String peer) {
        LogEntry entry = new LogEntry(dir, msg, peer);
        logs.add(entry);
        emit("log", "udpServer", entry.toJson());
    }

    private void logSystem(String msg, String peer, String i18nKey, String... args) {
        LogEntry entry = new LogEntry(LogEntry.Direction.SYSTEM, msg, peer, i18nKey, args);
        logs.add(entry);
        emit("log", "udpServer", entry.toJson());
    }

    private void emit(String type, String target, Object data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("target", target);
        obj.add("data", gson.toJsonTree(data));
        onEvent.accept(gson.toJson(obj));
    }
}
