package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.debugtool.util.HexUtil;
import com.debugtool.util.I18n;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * UDP Client - binds a local port and sends/receives UDP datagrams.
 */
public class UdpClientService {

    private final Consumer<String> onEvent;
    private final Gson gson = new Gson();
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    private DatagramSocket socket;
    private volatile boolean bound = false;
    private int localPort;

    public UdpClientService(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

    public void bind(int localPort) {
        if (bound) {
            emit("error", "udpClient", I18n.get("udp.client.already_bound"));
            return;
        }
        this.localPort = localPort;
        logs.clear();

        try {
            socket = new DatagramSocket(localPort);
            bound = true;
            logSystem(I18n.get("udp.client.bound", localPort), "system", "udp.client.bound", String.valueOf(localPort));
            emit("bound", "udpClient", localPort);

            Thread receiveThread = new Thread(() -> {
                byte[] buffer = new byte[65535];
                while (bound) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                        String message = HexUtil.toHexString(data);
                        String peer = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                        log(LogEntry.Direction.RECEIVED, message, peer);
                        emit("messageReceived", "udpClient", gson.toJson(new String[][]{
                            {"from", peer}, {"message", message}
                        }));
                    } catch (Exception e) {
                        if (bound) {
                            emit("error", "udpClient", I18n.get("udp.client.receive_error", e.getMessage()));
                        }
                    }
                }
            }, "UDP-Client-Receiver");
            receiveThread.setDaemon(true);
            receiveThread.start();

        } catch (SocketException e) {
            bound = false;
            logSystem(I18n.get("udp.client.bind_failed", e.getMessage()), "system", "udp.client.bind_failed", e.getMessage());
            emit("error", "udpClient", e.getMessage());
        }
    }

    public void unbind() {
        bound = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        logSystem(I18n.get("udp.client.unbound"), "system", "udp.client.unbound");
        emit("unbound", "udpClient", 0);
    }

    public void send(String targetHost, int targetPort, String message, String encoding, String format) {
        if (!bound || socket == null) {
            emit("error", "udpClient", I18n.get("udp.client.not_bound"));
            return;
        }
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "udpClient", err);
                return;
            }
        }
        try {
            byte[] data;
            if ("hex".equals(format)) {
                data = HexUtil.parseHex(message);
            } else {
                data = message.getBytes(encoding != null ? encoding : "UTF-8");
            }
            InetAddress addr = InetAddress.getByName(targetHost);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, targetPort);
            socket.send(packet);
            log(LogEntry.Direction.SENT, formatMessage(message, format), targetHost + ":" + targetPort);
            emit("messageSent", "udpClient", targetHost + ":" + targetPort);
        } catch (Exception e) {
            emit("error", "udpClient", I18n.get("udp.client.send_failed", e.getMessage()));
        }
    }

    private String formatMessage(String message, String format) {
        if ("hex".equals(format)) {
            try { return HexUtil.toHexString(HexUtil.parseHex(message)); } catch (Exception e) { return message; }
        }
        return message;
    }

    public String getStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("bound", bound);
        obj.addProperty("localPort", localPort);
        return gson.toJson(obj);
    }

    public int getLocalPort() { return localPort; }

    private void log(LogEntry.Direction dir, String msg, String peer) {
        LogEntry entry = new LogEntry(dir, msg, peer);
        logs.add(entry);
        emit("log", "udpClient", entry.toJson());
    }

    private void logSystem(String msg, String peer, String i18nKey, String... args) {
        LogEntry entry = new LogEntry(LogEntry.Direction.SYSTEM, msg, peer, i18nKey, args);
        logs.add(entry);
        emit("log", "udpClient", entry.toJson());
    }

    private void emit(String type, String target, Object data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("target", target);
        obj.add("data", gson.toJsonTree(data));
        onEvent.accept(gson.toJson(obj));
    }
}
