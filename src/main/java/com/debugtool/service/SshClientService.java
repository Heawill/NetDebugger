package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.debugtool.util.I18n;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jcraft.jsch.*;

import java.io.*;
import java.nio.file.*;
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

    private static int httpPort = 0;

    public static void setHttpPort(int port) { httpPort = port; }

    public SshClientService(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

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

            // Inject PROMPT_COMMAND for SFTP path sync
            try {
                String hook = "export PROMPT_COMMAND='[[ $PWD != $_ND_PWD ]] && { printf \"\\033]777;pwd;${PWD}\\007\"; _ND_PWD=$PWD; }'\n";
                channelOut.write(hook.getBytes("UTF-8"));
                channelOut.flush();
                Thread.sleep(100);
            } catch (Exception ignored) {}

            connected.set(true);
            logSystem(I18n.get("ssh.client.connected", host, port), "system", "ssh.client.connected",
                String.valueOf(host), String.valueOf(port));
            emit("connected", "sshClient", null);

            readThread = new Thread(() -> {
                byte[] buffer = new byte[8192];
                int bytesRead;
                try {
                    while (connected.get() && (bytesRead = channelIn.read(buffer)) != -1) {
                        if (bytesRead > 0) {
                            byte[] data = Arrays.copyOf(buffer, bytesRead);
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

    public void sendTerminalData(String base64Data) {
        byte[] data = Base64.getDecoder().decode(base64Data);
        sendTerminalData(data);
    }

    public void resizePty(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        if (channel != null && connected.get()) {
            channel.setPtySize(cols, rows, cols * 8, rows * 16);
        }
    }

    public synchronized void disconnect() {
        connected.set(false);

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

    // ================== SFTP ==================

    private String sftpCurrentDir = "/";

    public void sftpList(String path) {
        if (!connected.get() || session == null || !session.isConnected()) {
            emit("error", "sshClient", "Not connected");
            return;
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(5000);

            if (path == null || path.isEmpty()) path = sftpCurrentDir;
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(path);
            JsonObject result = new JsonObject();
            result.addProperty("path", path);

            com.google.gson.JsonArray files = new com.google.gson.JsonArray();
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) continue;
                JsonObject file = new JsonObject();
                file.addProperty("name", name);
                file.addProperty("isDir", entry.getAttrs().isDir());
                file.addProperty("size", entry.getAttrs().getSize());
                file.addProperty("mtime", entry.getAttrs().getMTime());
                file.addProperty("permissions", entry.getAttrs().getPermissionsString());
                files.add(file);
            }
            result.add("files", files);
            sftpCurrentDir = path;
            emit("sftpList", "sshClient", gson.toJson(result));

        } catch (SftpException | JSchException e) {
            emit("error", "sshClient", "SFTP list failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    /**
     * Download via SFTP to temp dir, serve via HTTP. Avoids blocking UI with large Base64.
     */
    public void sftpDownload(String remotePath, String dlDir) {
        if (!connected.get() || session == null || !session.isConnected()) {
            emit("error", "sshClient", "Not connected");
            return;
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(5000);

            String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            String dir = (dlDir != null && !dlDir.isEmpty()) ? dlDir :
                Paths.get(System.getProperty("user.home"), "Downloads").toString();
            Path dlPath = Paths.get(dir);
            Files.createDirectories(dlPath);
            Path file = dlPath.resolve(fileName);

            // Use progress monitor for real-time percentage
            final String fname = fileName;
            sftpChannel.get(remotePath, file.toString(), new SftpProgressMonitor() {
                private long total = -1;
                private long transferred = 0;
                private int lastPct = -1;

                @Override public void init(int op, String src, String dest, long max) {
                    total = max;
                    emitProgress(fname, total, 0);
                }
                @Override public boolean count(long bytes) {
                    transferred += bytes;
                    if (total > 0) {
                        int pct = (int) (transferred * 100 / total);
                        if (pct != lastPct) {
                            lastPct = pct;
                            emitProgress(fname, total, transferred);
                        }
                    }
                    return true;
                }
                @Override public void end() {
                    emitProgress(fname, total, total);
                }
            }, ChannelSftp.OVERWRITE);

            JsonObject result = new JsonObject();
            result.addProperty("name", fileName);
            long size = Files.size(file);
            result.addProperty("size", size);
            result.addProperty("path", file.toString());
            emit("sftpFileData", "sshClient", gson.toJson(result));

//            new Thread(() -> {
//                try { java.awt.Desktop.getDesktop().open(file.toFile()); }
//                catch (Exception ignored) {}
//            }, "SftpOpen").start();

        } catch (Exception e) {
            emit("error", "sshClient", "SFTP download failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    private void emitProgress(String fileName, long total, long transferred) {
        JsonObject p = new JsonObject();
        p.addProperty("name", fileName);
        p.addProperty("total", total);
        p.addProperty("transferred", transferred);
        emit("sftpProgress", "sshClient", gson.toJson(p));
    }

    public void sftpUpload(String remotePath, String base64Data) {
        if (!connected.get() || session == null || !session.isConnected()) {
            emit("error", "sshClient", "Not connected");
            return;
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(5000);

            byte[] data = Base64.getDecoder().decode(base64Data);
            String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            final String fname = fileName;
            sftpChannel.put(new ByteArrayInputStream(data), remotePath, new SftpProgressMonitor() {
                private long total = data.length;
                private long transferred = 0;
                private int lastPct = -1;
                @Override public void init(int op, String src, String dest, long max) {
                    emitProgress(fname, total, 0);
                }
                @Override public boolean count(long bytes) {
                    transferred += bytes;
                    int pct = (int)(transferred * 100 / total);
                    if (pct != lastPct) { lastPct = pct; emitProgress(fname, total, transferred); }
                    return true;
                }
                @Override public void end() { emitProgress(fname, total, total); }
            }, ChannelSftp.OVERWRITE);

            emit("sftpUploaded", "sshClient", remotePath);
            sftpList(sftpCurrentDir);

        } catch (Exception e) {
            emit("error", "sshClient", "SFTP upload failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    /**
     * Upload via HTTP stream with progress (called from /upload/ endpoint).
     */
    public void sftpUploadStream(InputStream in, String fileName, long totalSize) {
        if (!connected.get() || session == null || !session.isConnected()) {
            emit("error", "sshClient", "Not connected");
            return;
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(5000);

            String remotePath = sftpCurrentDir.equals("/") ? "/" + fileName : sftpCurrentDir + "/" + fileName;
            final String fname = fileName;
            sftpChannel.put(in, remotePath, new SftpProgressMonitor() {
                private long total = totalSize;
                private long transferred = 0;
                private int lastPct = -1;
                @Override public void init(int op, String src, String dest, long max) {
                    emitProgress(fname, total, 0);
                }
                @Override public boolean count(long bytes) {
                    transferred += bytes;
                    if (total > 0) {
                        int pct = (int)(transferred * 100 / total);
                        if (pct != lastPct) { lastPct = pct; emitProgress(fname, total, transferred); }
                    }
                    return true;
                }
                @Override public void end() { emitProgress(fname, total, transferred > 0 ? transferred : total); }
            }, ChannelSftp.OVERWRITE);

            emit("sftpUploaded", "sshClient", remotePath);
            sftpList(sftpCurrentDir);

        } catch (Exception e) {
            emit("error", "sshClient", "SFTP upload failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    public void sftpDelete(String remotePath) {
        if (!connected.get() || session == null || !session.isConnected()) {
            emit("error", "sshClient", "Not connected");
            return;
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(5000);
            deleteRecursive(sftpChannel, remotePath);
            emit("sftpDeleted", "sshClient", remotePath);
            sftpList(sftpCurrentDir);
        } catch (Exception e) {
            emit("error", "sshClient", "SFTP delete failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    private void deleteRecursive(ChannelSftp sftp, String path) throws Exception {
        SftpATTRS attrs = sftp.stat(path);
        if (attrs.isDir()) {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftp.ls(path);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) continue;
                String childPath = path.endsWith("/") ? path + name : path + "/" + name;
                deleteRecursive(sftp, childPath);
            }
            sftp.rmdir(path);
        } else {
            sftp.rm(path);
        }
    }

    public void sftpRename(String oldPath, String newPath) {
        if (!connected.get() || session == null || !session.isConnected()) {
            emit("error", "sshClient", "Not connected");
            return;
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(5000);
            sftpChannel.rename(oldPath, newPath);
            emit("sftpRenamed", "sshClient", oldPath + "|" + newPath);
            sftpList(sftpCurrentDir);
        } catch (Exception e) {
            emit("error", "sshClient", "SFTP rename failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
        }
    }

    public String getStatusJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("connected", connected.get());
        if (host != null) obj.addProperty("host", host);
        obj.addProperty("port", port);
        return gson.toJson(obj);
    }

    public void emitError(String msg) {
        emit("error", "sshClient", msg);
    }

    public void emitUploadCancelled() {
        emit("uploadCancelled", "sshClient", null);
    }

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
