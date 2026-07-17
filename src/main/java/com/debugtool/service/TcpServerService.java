package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * TCP Server - accepts multiple client connections.
 */
public class TcpServerService {

    private final Consumer<String> onEvent;
    private final Gson gson = new Gson();
    private final AtomicInteger generation = new AtomicInteger(0);
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;
    private int port;
    private int maxConnections = 10;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    public TcpServerService(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

    public synchronized void start(int port, int maxConnections) {
        if (running) {
            stop();
        }
        generation.incrementAndGet();
        this.port = port;
        this.maxConnections = maxConnections;
        logs.clear();
        clients.clear();

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(port));
            running = true;
            logSystem(I18n.get("tcp.server.started", port), "system", "tcp.server.started", String.valueOf(port));
            emit("serverStarted", "tcpServer", port);

            acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        String clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                        if (clients.size() >= maxConnections) {
                            socket.close();
                            logSystem(I18n.get("tcp.server.rejected", maxConnections, clientId), "system", "tcp.server.rejected", String.valueOf(maxConnections), clientId);
                            emit("error", "tcpServer", I18n.get("tcp.server.rejected", maxConnections, clientId));
                            continue;
                        }
                        ClientHandler handler = new ClientHandler(socket, clientId);
                        clients.put(clientId, handler);
                        new Thread(handler).start();
                        logSystem(I18n.get("tcp.server.client_connected", clientId), clientId, "tcp.server.client_connected", clientId);
                        emit("clientConnected", "tcpServer", clientId);
                        emitClientList();
                    } catch (IOException e) {
                        if (running) {
                            logSystem(I18n.get("tcp.server.accept_error", e.getMessage()), "system", "tcp.server.accept_error", e.getMessage());
                            emit("error", "tcpServer", e.getMessage());
                        }
                    }
                }
            }, "TCP-Server-Accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

        } catch (IOException e) {
            running = false;
            logSystem(I18n.get("tcp.server.start_failed", e.getMessage()), "system", "tcp.server.start_failed", e.getMessage());
            emit("error", "tcpServer", e.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        // Close all client sockets first to trigger handler threads to exit
        for (ClientHandler handler : clients.values()) {
            handler.close();
        }
        clients.clear();
        // Close server socket to unblock accept()
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        // Wait for accept thread to finish
        if (acceptThread != null && acceptThread.isAlive()) {
            try { acceptThread.join(2000); } catch (InterruptedException ignored) {}
        }
        // Bump generation so any late-running handler finally blocks become no-ops
        generation.incrementAndGet();
        logSystem(I18n.get("tcp.server.stopped"), "system", "tcp.server.stopped");
        emit("serverStopped", "tcpServer", 0);
        emitClientList();
    }

    public void send(String clientId, String message, String encoding, String format) {
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "tcpServer", err);
                return;
            }
        }
        ClientHandler handler = clients.get(clientId);
        if (handler != null) {
            handler.send(message, encoding, format);
            log(LogEntry.Direction.SENT, formatMessage(message, format), clientId);
            emit("messageSent", "tcpServer", clientId);
        } else {
            emit("error", "tcpServer", I18n.get("tcp.server.client_not_found", clientId));
        }
    }

    public void sendAll(String message, String encoding, String format) {
        if ("hex".equals(format)) {
            String err = HexUtil.validateHex(message);
            if (err != null) {
                emit("error", "tcpServer", err);
                return;
            }
        }
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            entry.getValue().send(message, encoding, format);
            log(LogEntry.Direction.SENT, formatMessage(message, format), entry.getKey());
        }
        emit("messageSentAll", "tcpServer", message);
    }

    private String formatMessage(String message, String format) {
        if ("hex".equals(format)) {
            try { return HexUtil.toHexString(HexUtil.parseHex(message)); } catch (Exception e) { return message; }
        }
        return message;
    }

    public void disconnectClient(String clientId) {
        ClientHandler handler = clients.remove(clientId);
        if (handler != null) {
            handler.close();
            logSystem(I18n.get("tcp.server.disconnected", clientId), clientId, "tcp.server.disconnected", clientId);
            emit("clientDisconnected", "tcpServer", clientId);
            emitClientList();
        }
    }

    public String getStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("running", running);
        obj.addProperty("port", port);
        obj.addProperty("clientCount", clients.size());
        return gson.toJson(obj);
    }

    public String getClientsJson() {
        return gson.toJson(new ArrayList<>(clients.keySet()));
    }

    public int getPort() { return port; }
    public int getMaxConnections() { return maxConnections; }

    private void emitClientList() {
        emit("clientList", "tcpServer", getClientsJson());
    }

    // --- Inner Client Handler ---

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientId;
        private final int myGeneration;
        private BufferedReader reader;
        private PrintWriter writer;

        ClientHandler(Socket socket, String clientId) {
            this.socket = socket;
            this.clientId = clientId;
            this.myGeneration = generation.get();
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                String line;
                while (running && (line = reader.readLine()) != null) {
                    log(LogEntry.Direction.RECEIVED, line, clientId);
                    emit("messageReceived", "tcpServer", gson.toJson(new String[][]{
                        {"clientId", clientId}, {"message", line}
                    }));
                }
            } catch (IOException e) {
                if (running) {
                    logSystem(I18n.get("tcp.server.client_disconnected", clientId), clientId, "tcp.server.client_disconnected", clientId);
                }
            } finally {
                if (myGeneration == generation.get()) {
                    clients.remove(clientId);
                    emit("clientDisconnected", "tcpServer", clientId);
                    emitClientList();
                }
                close();
            }
        }

        void send(String message, String encoding, String format) {
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
            } catch (IOException ignored) {}
        }

        void close() {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    // --- Helpers ---

    private void log(LogEntry.Direction dir, String msg, String peer) {
        LogEntry entry = new LogEntry(dir, msg, peer);
        logs.add(entry);
        emit("log", "tcpServer", entry.toJson());
    }

    /** SYSTEM log with i18n key for frontend translation. */
    private void logSystem(String msg, String peer, String i18nKey, String... args) {
        LogEntry entry = new LogEntry(LogEntry.Direction.SYSTEM, msg, peer, i18nKey, args);
        logs.add(entry);
        emit("log", "tcpServer", entry.toJson());
    }

    private void emit(String type, String target, Object data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("target", target);
        obj.add("data", gson.toJsonTree(data));
        onEvent.accept(gson.toJson(obj));
    }
}
