package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.debugtool.util.I18n;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jcraft.jsch.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * SSH Client Service - manages SSH connections using JSch.
 * Terminal I/O is bridged through push events to the frontend.
 */
public class SshClientService {

    private final Consumer<String> onEvent;
    private final Gson gson = new Gson();
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    private Session session;
    private ChannelShell channel;
    private InputStream channelIn;
    private OutputStream channelOut;
    private Thread readThread;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private String host;
    private int port;
    private String username;
    private String password;
    private int cols = 80;
    private int rows = 24;

    public SshClientService(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

    /**
     * Connect to SSH server and open a shell channel.
     */
    public synchronized void connect(String host, int port, String username, String password, int cols, int rows) {
        if (connected.get()) {
            disconnect();
        }
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.cols = cols;
        this.rows = rows;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            // Disable strict host key checking for convenience
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(10000);

            session.connect(10000);

            channel = (ChannelShell) session.openChannel("shell");
            channel.setPtySize(cols, rows, cols * 8, rows * 16);
            channel.setPtyType("xterm-256color");

            channelIn = channel.getInputStream();
            channelOut = channel.getOutputStream();

            channel.connect(5000);

            connected.set(true);
            logSystem(I18n.get("ssh.client.connected", host, port), "system", "ssh.client.connected",
                String.valueOf(host), String.valueOf(port));
            emit("connected", "sshClient", null);

            // Start read thread
            readThread = new Thread(() -> {
                byte[] buffer = new byte[8192];
                int bytesRead;
                try {
                    while (connected.get() && (bytesRead = channelIn.read(buffer)) != -1) {
                        if (bytesRead > 0) {
                            byte[] data = Arrays.copyOf(buffer, bytesRead);
                            // Push terminal data to frontend
                            emit("terminalData", "sshClient",
                                Base64.getEncoder().encodeToString(data));
                        }
                    }
                } catch (IOException e) {
                    if (connected.get()) {
                        logSystem(I18n.get("ssh.client.connection_lost", e.getMessage()),
                            "system", "ssh.client.connection_lost", e.getMessage());
                        emit("error", "sshClient", e.getMessage());
                    }
                } finally {
                    if (connected.get()) {
                        disconnect();
                        emit("disconnected", "sshClient", null);
                    }
                }
            }, "SSH-Read");
            readThread.setDaemon(true);
            readThread.start();

        } catch (JSchException | IOException e) {
            connected.set(false);
            logSystem(I18n.get("ssh.client.connection_failed", e.getMessage()),
                "system", "ssh.client.connection_failed", e.getMessage());
            emit("error", "sshClient", e.getMessage());
            if (cb != null) {
                cb.accept(e.getMessage());
            }
        }
    }

    private Consumer<String> cb;

    public void connect(String host, int port, String username, String password, int cols, int rows, Consumer<String> errorCallback) {
        this.cb = errorCallback;
        connect(host, port, username, password, cols, rows);
    }

    /**
     * Send terminal input (keystrokes) to the SSH shell.
     */
    public void sendTerminalData(byte[] data) {
        if (!connected.get() || channelOut == null) return;
        try {
            channelOut.write(data);
            channelOut.flush();
        } catch (IOException e) {
            logSystem(I18n.get("ssh.client.send_failed", e.getMessage()),
                "system", "ssh.client.send_failed", e.getMessage());
        }
    }

    /**
     * Send terminal input from base64-encoded string.
     */
    public void sendTerminalData(String base64Data) {
        byte[] data = Base64.getDecoder().decode(base64Data);
        sendTerminalData(data);
    }

    /**
     * Resize the PTY when the terminal is resized.
     */
    public void resizePty(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        if (channel != null && connected.get()) {
            channel.setPtySize(cols, rows, cols * 8, rows * 16);
        }
    }

    /**
     * Disconnect the SSH session.
     */
    public synchronized void disconnect() {
        connected.set(false);

        // Close channel first to unblock read thread
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }

        if (readThread != null && readThread.isAlive()) {
            try { readThread.join(2000); } catch (InterruptedException ignored) {}
        }

        channelIn = null;
        channelOut = null;
        channel = null;
        session = null;

        logSystem(I18n.get("ssh.client.disconnected"), "system", "ssh.client.disconnected");
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getHost() { return host; }
    public int getPort() { return port; }

    public String getStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("connected", connected.get());
        if (host != null) obj.addProperty("host", host);
        obj.addProperty("port", port);
        return gson.toJson(obj);
    }

    // --- Helpers ---

    private void logSystem(String msg, String peer, String i18nKey, String... args) {
        LogEntry entry = new LogEntry(LogEntry.Direction.SYSTEM, msg, peer, i18nKey, args);
        logs.add(entry);
        emit("log", "sshClient", entry.toJson());
    }

    private void emit(String type, String target, Object data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("target", target);
        if (data != null) {
            obj.add("data", gson.toJsonTree(data));
        }
        onEvent.accept(gson.toJson(obj));
    }
}
