package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * TCP Client - connects to a remote TCP server.
 */
public class TcpClientService {

    private final Consumer<String> onEvent;
    private final Gson gson = new Gson();
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected = false;
    private String remoteHost;
    private int remotePort;

    public TcpClientService(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

    public void connect(String host, int port) {
        if (connected) {
            emit("error", "tcpClient", I18n.get("tcp.client.already_connected"));
            return;
        }
        this.remoteHost = host;
        this.remotePort = port;
        logs.clear();

        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            connected = true;

            logSystem(I18n.get("tcp.client.connected", host, port), host, "tcp.client.connected", host, String.valueOf(port));
            emit("connected", "tcpClient", host + ":" + port);

            // Read thread
            Thread readThread = new Thread(() -> {
                String line;
                try {
                    while (connected && (line = reader.readLine()) != null) {
                        log(LogEntry.Direction.RECEIVED, line, host + ":" + port);
                        emit("messageReceived", "tcpClient", line);
                    }
                    // readLine() returned null → server closed the connection
                    if (connected) {
                        logSystem(I18n.get("tcp.client.closed_by_server"), "system", "tcp.client.closed_by_server");
                        disconnect();
                    }
                } catch (IOException e) {
                    if (connected) {
                        logSystem(I18n.get("tcp.client.connection_lost", e.getMessage()), "system", "tcp.client.connection_lost", e.getMessage());
                        emit("error", "tcpClient", I18n.get("tcp.client.connection_lost", e.getMessage()));
                        disconnect();
                    }
                }
            }, "TCP-Client-Reader");
            readThread.setDaemon(true);
            readThread.start();

        } catch (IOException e) {
            connected = false;
            logSystem(I18n.get("tcp.client.connection_failed", e.getMessage()), "system", "tcp.client.connection_failed", e.getMessage());
            emit("error", "tcpClient", e.getMessage());
        }
    }

    public void disconnect() {
        connected = false;
        // Close socket first to unblock the read thread's blocking readLine().
        // Must do this before closing reader/writer, because reader.close()
        // contends for BufferedReader's lock while readLine() holds it.
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
        if (reader != null) {
            try { reader.close(); } catch (IOException ignored) {}
        }
        if (writer != null) {
            writer.close();
        }
        logSystem(I18n.get("tcp.client.disconnected"), "system", "tcp.client.disconnected");
        emit("disconnected", "tcpClient", 0);
    }

    public void reconnect() {
        if (remoteHost != null && remotePort > 0) {
            connect(remoteHost, remotePort);
        }
    }

    public void send(String message, String encoding, String format) {
        if (!connected || socket == null) {
            emit("error", "tcpClient", I18n.get("tcp.client.not_connected"));
            return;
        }
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "tcpClient", err);
                return;
            }
        }
        try {
            if ("hex".equals(format)) {
                byte[] data = HexUtil.parseHex(message);
                socket.getOutputStream().write(data);
                socket.getOutputStream().write('\n');
                socket.getOutputStream().flush();
            } else if (writer != null) {
                writer.println(message);
                writer.flush();
            }
        } catch (IOException e) {
            emit("error", "tcpClient", I18n.get("tcp.client.send_failed", e.getMessage()));
            return;
        }
        log(LogEntry.Direction.SENT, formatMessage(message, format), remoteHost + ":" + remotePort);
        emit("messageSent", "tcpClient", message);
    }

    private String formatMessage(String message, String format) {
        if ("hex".equals(format)) {
            try { return HexUtil.toHexString(HexUtil.parseHex(message)); } catch (Exception e) { return message; }
        }
        return message;
    }

    public String getStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("connected", connected);
        obj.addProperty("host", remoteHost);
        obj.addProperty("port", remotePort);
        return gson.toJson(obj);
    }

    private void log(LogEntry.Direction dir, String msg, String peer) {
        LogEntry entry = new LogEntry(dir, msg, peer);
        logs.add(entry);
        emit("log", "tcpClient", entry.toJson());
    }

    private void logSystem(String msg, String peer, String i18nKey, String... args) {
        LogEntry entry = new LogEntry(LogEntry.Direction.SYSTEM, msg, peer, i18nKey, args);
        logs.add(entry);
        emit("log", "tcpClient", entry.toJson());
    }

    private void emit(String type, String target, Object data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("target", target);
        obj.add("data", gson.toJsonTree(data));
        onEvent.accept(gson.toJson(obj));
    }
}
