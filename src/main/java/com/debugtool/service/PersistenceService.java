package com.debugtool.service;

import com.debugtool.model.LogEntry;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persistence service for session configs, message logs, and app config.
 * Stores data as JSON files under ~/.netdebugger/ directory.
 */
public class PersistenceService {

    private static final Path DATA_DIR = Paths.get(
        System.getProperty("user.home"), ".netdebugger");
    private static final String SESSIONS_FILE = "sessions.json";
    private static final String CONFIG_FILE = "config.json";
    private static final String LOGS_DIR = "logs";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ReentrantLock logLock = new ReentrantLock();

    // Log save queue: sessionId -> list of LogEntries waiting to be flushed
    private final Map<String, List<LogEntry>> pendingLogs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "persistence-scheduler");
        t.setDaemon(true);
        return t;
    });

    public PersistenceService() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(DATA_DIR.resolve(LOGS_DIR));
        } catch (IOException e) {
            System.err.println("[Persistence] Failed to create data dir: " + e.getMessage());
        }
        // Flush pending logs every 3 seconds
        scheduler.scheduleWithFixedDelay(this::flushPendingLogs, 3, 3, TimeUnit.SECONDS);
    }

    // ==================== Config ====================

    public void saveConfig(Map<String, String> config) {
        try {
            writeJson(DATA_DIR.resolve(CONFIG_FILE), gson.toJsonTree(config));
        } catch (IOException e) {
            System.err.println("[Persistence] saveConfig failed: " + e.getMessage());
        }
    }

    public Map<String, String> loadConfig() {
        try {
            JsonObject obj = readJson(DATA_DIR.resolve(CONFIG_FILE));
            if (obj != null) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String key : obj.keySet()) {
                    JsonElement val = obj.get(key);
                    if (val.isJsonPrimitive()) {
                        map.put(key, val.getAsString());
                    }
                }
                return map;
            }
        } catch (IOException e) {
            System.err.println("[Persistence] loadConfig failed: " + e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    // ==================== Sessions ====================

    public void saveSessions(JsonArray sessions) {
        try {
            JsonObject root = new JsonObject();
            root.add("sessions", sessions);
            writeJson(DATA_DIR.resolve(SESSIONS_FILE), root);
        } catch (IOException e) {
            System.err.println("[Persistence] saveSessions failed: " + e.getMessage());
        }
    }

    public JsonArray loadSessions() {
        try {
            JsonObject root = readJson(DATA_DIR.resolve(SESSIONS_FILE));
            if (root != null && root.has("sessions")) {
                return root.getAsJsonArray("sessions");
            }
        } catch (IOException e) {
            System.err.println("[Persistence] loadSessions failed: " + e.getMessage());
        }
        return new JsonArray();
    }

    // ==================== Logs ====================

    /**
     * Add a log entry to the pending queue. Flushed to disk periodically.
     */
    public void appendLog(String sessionId, LogEntry entry) {
        pendingLogs.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(entry);
    }

    /**
     * Load all log entries for a session from disk.
     */
    public List<LogEntry> loadLogs(String sessionId) {
        Path file = getLogFile(sessionId);
        try {
            JsonObject root = readJson(file);
            if (root != null && root.has("logs")) {
                JsonArray arr = root.getAsJsonArray("logs");
                List<LogEntry> logs = new ArrayList<>(arr.size());
                for (JsonElement el : arr) {
                    try {
                        logs.add(LogEntry.fromJson(el.getAsJsonObject()));
                    } catch (Exception e) {
                        // Skip corrupted entries
                    }
                }
                return logs;
            }
        } catch (IOException e) {
            // File doesn't exist yet, return empty
        }
        return new ArrayList<>();
    }

    /**
     * Force flush all pending log entries to disk.
     */
    public void flushAllLogs() {
        flushPendingLogs();
    }

    private void flushPendingLogs() {
        // Snapshot pending logs to minimize lock contention
        Map<String, List<LogEntry>> snapshot = new HashMap<>();
        for (Map.Entry<String, List<LogEntry>> e : pendingLogs.entrySet()) {
            List<LogEntry> list = e.getValue();
            if (list.isEmpty()) continue;
            synchronized (list) {
                if (list.isEmpty()) continue;
                snapshot.put(e.getKey(), new ArrayList<>(list));
                list.clear();
            }
        }
        for (Map.Entry<String, List<LogEntry>> e : snapshot.entrySet()) {
            appendLogsToFile(e.getKey(), e.getValue());
        }
    }

    private void appendLogsToFile(String sessionId, List<LogEntry> newLogs) {
        Path file = getLogFile(sessionId);
        logLock.lock();
        try {
            // Read existing
            JsonObject root;
            JsonArray existingLogs;
            try {
                root = readJson(file);
            } catch (IOException ex) {
                root = null;
            }
            if (root != null && root.has("logs")) {
                existingLogs = root.getAsJsonArray("logs");
            } else {
                root = new JsonObject();
                root.addProperty("sessionId", sessionId);
                existingLogs = new JsonArray();
                root.add("logs", existingLogs);
            }

            // Append new entries
            for (LogEntry entry : newLogs) {
                existingLogs.add(entry.toJsonElement());
            }

            // Trim to max 5000 entries
            while (existingLogs.size() > 5000) {
                existingLogs.remove(0);
            }

            writeJson(file, root);
        } catch (IOException e) {
            System.err.println("[Persistence] appendLogsToFile failed: " + e.getMessage());
        } finally {
            logLock.unlock();
        }
    }

    /**
     * Completely overwrite logs for a session (used after "clear logs").
     */
    public void clearLogs(String sessionId) {
        Path file = getLogFile(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[Persistence] clearLogs failed: " + e.getMessage());
        }
        // Also clear any pending logs
        List<LogEntry> pending = pendingLogs.get(sessionId);
        if (pending != null) {
            synchronized (pending) {
                pending.clear();
            }
        }
    }

    // ==================== Helpers ====================

    private Path getLogFile(String sessionId) {
        // Sanitize session id for file system
        String safeName = sessionId.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return DATA_DIR.resolve(LOGS_DIR).resolve(safeName + ".json");
    }

    private void writeJson(Path file, JsonElement json) throws IOException {
        String str = gson.toJson(json);
        Files.write(file, str.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private JsonObject readJson(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        String str = Files.readString(file, StandardCharsets.UTF_8);
        if (str.isBlank()) return null;
        return JsonParser.parseString(str).getAsJsonObject();
    }

    public void shutdown() {
        flushAllLogs();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }
}
