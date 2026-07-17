package com.debugtool.model;

import com.google.gson.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents a single log entry in the message log.
 * For SYSTEM entries, i18nKey/i18nArgs allow the frontend to re-translate on language switch.
 */
public class LogEntry {
    public enum Direction { SENT, RECEIVED, SYSTEM }

    private final String timestamp;
    private final Direction direction;
    private final String content;
    private final String peer; // client address or "self"

    // Only populated for SYSTEM entries: i18n key for frontend translation
    private final String i18nKey;
    private final String[] i18nArgs;

    private static final Gson gson = new GsonBuilder()
        .serializeNulls()
        .create();

    /** Standard entry (SENT, RECEIVED, or SYSTEM without i18n key). */
    public LogEntry(Direction direction, String content, String peer) {
        this.timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        this.direction = direction;
        this.content = content;
        this.peer = peer;
        this.i18nKey = null;
        this.i18nArgs = null;
    }

    /** SYSTEM entry with i18n key for frontend translation. */
    public LogEntry(Direction direction, String content, String peer, String i18nKey, String... i18nArgs) {
        this.timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        this.direction = direction;
        this.content = content;
        this.peer = peer;
        this.i18nKey = i18nKey;
        this.i18nArgs = i18nArgs.length > 0 ? i18nArgs : null;
    }

    /** Constructor for deserialization (from persisted data). */
    public LogEntry(String timestamp, Direction direction, String content, String peer,
                    String i18nKey, String[] i18nArgs) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.content = content;
        this.peer = peer;
        this.i18nKey = i18nKey;
        this.i18nArgs = (i18nArgs != null && i18nArgs.length > 0) ? i18nArgs : null;
    }

    public String getTimestamp() { return timestamp; }
    public Direction getDirection() { return direction; }
    public String getContent() { return content; }
    public String getPeer() { return peer; }

    public String toJson() {
        return gson.toJson(this);
    }

    /** Serialize as a JsonElement (for batch JSON ops). */
    public JsonElement toJsonElement() {
        return gson.toJsonTree(this);
    }

    /** Deserialize from a JsonObject. Handles pre-i18n entries gracefully. */
    public static LogEntry fromJson(JsonObject obj) {
        String ts = obj.get("timestamp").getAsString();
        Direction dir = Direction.valueOf(obj.get("direction").getAsString());
        String c = obj.has("content") ? obj.get("content").getAsString() : "";
        String p = obj.has("peer") ? obj.get("peer").getAsString() : "";

        String key = null;
        String[] args = null;
        if (obj.has("i18nKey") && !obj.get("i18nKey").isJsonNull()) {
            key = obj.get("i18nKey").getAsString();
            if (obj.has("i18nArgs") && !obj.get("i18nArgs").isJsonNull()) {
                JsonArray arr = obj.getAsJsonArray("i18nArgs");
                args = new String[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    args[i] = arr.get(i).getAsString();
                }
            }
        }
        return new LogEntry(ts, dir, c, p, key, args);
    }
}
