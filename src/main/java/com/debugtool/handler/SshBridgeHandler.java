package com.debugtool.handler;

import com.debugtool.service.SshClientService;
import org.cef.callback.CefQueryCallback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * Handles SSH Client and SFTP bridge operations.
 */
class SshBridgeHandler {

    private final ConcurrentHashMap<String, SshClientService> sshClients = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final BiConsumer<String, String> eventPusher;

    SshBridgeHandler(ExecutorService executor, BiConsumer<String, String> eventPusher) {
        this.executor = executor;
        this.eventPusher = eventPusher;
    }

    void createSshClient(String id, CefQueryCallback cb) {
        SshClientService s = new SshClientService(json -> eventPusher.accept(id, json));
        sshClients.put(id, s);
        BridgeUtils.ok(cb, id);
    }

    void connectSsh(String id, String host, int port, String username, String password, int cols, int rows, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) {
            s.connect(host, port, username, password, cols, rows, errorMsg -> {
                if (cb != null) cb.failure(-1, errorMsg);
            });
            if (cb != null) cb.success("ok");
        } else {
            BridgeUtils.fail(cb, id);
        }
    }

    void disconnectSsh(String id, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.disconnect(); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sshInput(String id, String base64Data, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.sendTerminalData(base64Data); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sshPaste(String id, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s == null || !s.isConnected()) {
            if (cb != null) cb.failure(-1, "Not connected");
            return;
        }
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                if (text != null && !text.isEmpty()) {
                    s.sendTerminalData(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    BridgeUtils.ok(cb, null);
                } else {
                    if (cb != null) cb.failure(-1, "Clipboard is empty");
                }
            } else {
                if (cb != null) cb.failure(-1, "Clipboard does not contain text");
            }
        } catch (Exception e) {
            if (cb != null) cb.failure(-2, "Clipboard read failed: " + e.getMessage());
        }
    }

    void resizeSshPty(String id, int cols, int rows, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.resizePty(cols, rows); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sftpList(String id, String path, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.sftpList(path); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sftpDownload(String id, String remotePath, String dlDir, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.sftpDownload(remotePath, dlDir); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sftpUpload(String id, String remotePath, String base64Data, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.sftpUpload(remotePath, base64Data); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sftpDelete(String id, String remotePath, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s != null) { s.sftpDelete(remotePath); BridgeUtils.ok(cb, null); }
        else BridgeUtils.fail(cb, id);
    }

    void sftpRename(String id, String oldPath, String newName, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s == null || !s.isConnected()) { BridgeUtils.fail(cb, id); return; }
        SshClientService svc = s;
        String dir = oldPath.contains("/") ? oldPath.substring(0, oldPath.lastIndexOf('/')) : "/";
        String newPath = (dir.equals("/") ? "/" : dir + "/") + newName;
        svc.sftpRename(oldPath, newPath);
        BridgeUtils.ok(cb, null);
    }

    void pickAndUpload(String id, CefQueryCallback cb) {
        SshClientService s = sshClients.get(id);
        if (s == null || !s.isConnected()) { BridgeUtils.fail(cb, id); return; }
        BridgeUtils.ok(cb, null);
        // Open native OS file picker in AWT thread, upload in executor thread
        java.awt.EventQueue.invokeLater(() -> {
            java.awt.Frame[] frames = java.awt.Frame.getFrames();
            java.awt.Frame parent = frames.length > 0 ? frames[0] : null;
            java.awt.FileDialog fd = new java.awt.FileDialog(parent, "Select File to Upload", java.awt.FileDialog.LOAD);
            fd.setVisible(true);
            String selectedFile = fd.getFile();
            String selectedDir = fd.getDirectory();
            if (selectedFile != null && selectedDir != null) {
                java.io.File f = new java.io.File(selectedDir, selectedFile);
                if (f.isFile()) {
                    executor.submit(() -> {
                        try {
                            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
                            String name = f.getName();
                            s.sftpUploadStream(new java.io.ByteArrayInputStream(data), name, data.length);
                        } catch (Exception e) {
                            s.emitError("Upload failed: " + e.getMessage());
                        }
                    });
                }
            } else {
                s.emitUploadCancelled();
            }
        });
    }

    /**
     * Try to remove an instance by id. Returns true if found and removed.
     */
    boolean removeInstance(String id) {
        SshClientService ss = sshClients.remove(id);
        if (ss != null) { ss.disconnect(); return true; }
        return false;
    }

    SshClientService getSshClient(String id) {
        return sshClients.get(id);
    }

    void shutdown() {
        for (SshClientService s : sshClients.values()) s.disconnect();
    }
}
