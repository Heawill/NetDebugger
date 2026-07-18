package com.debugtool.util;

/**
 * Hex string parsing utility.
 * Supports formats: "01 02", "0102", "0x01 0x02", "0x010x02", "\\x01 \\x02", "\\x01\\x02"
 */
public final class HexUtil {

    private HexUtil() {}

    /**
     * Validate whether the given string is a valid hex format.
     * @return null if valid, or an error message string if invalid.
     */
    public static String validateHex(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return I18n.get("hex.empty");
        }
        String cleaned = hex.replaceAll("\\s+", "").replaceAll("0[xX]|\\\\[xX]", "");
        if (cleaned.isEmpty()) {
            return I18n.get("hex.empty");
        }
        if (!cleaned.matches("[0-9a-fA-F]+")) {
            return I18n.get("hex.invalid_chars");
        }
        if (cleaned.length() % 2 != 0) {
            return I18n.get("hex.odd_length", cleaned.length());
        }
        return null;
    }

    /**
     * Parse a hex string to byte array.
     * Removes spaces, "0x"/"0X", "\\x"/"\\X" prefixes, then parses pairs of hex digits.
     */
    public static byte[] parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        String cleaned = hex.replaceAll("\\s+", "").replaceAll("0[xX]|\\\\[xX]", "");
        if (cleaned.isEmpty()) {
            return new byte[0];
        }
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException(I18n.get("hex.odd_length", cleaned.length()));
        }
        byte[] result = new byte[cleaned.length() / 2];
        for (int i = 0; i < cleaned.length(); i += 2) {
            int val = Integer.parseInt(cleaned.substring(i, i + 2), 16);
            result[i / 2] = (byte) val;
        }
        return result;
    }

    /**
     * Convert a byte array to space-separated hex string (e.g. "48 65 6C 6C 6F").
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
