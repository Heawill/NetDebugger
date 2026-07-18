package com.debugtool.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Internationalization utility.
 * Loads messages from i18n/messages_*.properties via ResourceBundle.
 * Supports runtime locale switching.
 */
public final class I18n {

    private static final String BASE_NAME = "i18n/messages";
    private static volatile Locale currentLocale;
    private static volatile ResourceBundle bundle;

    static {
        currentLocale = Locale.getDefault();
        reload();
    }

    private I18n() {}

    private static void reload() {
        bundle = ResourceBundle.getBundle(BASE_NAME, currentLocale);
    }

    /**
     * Get the current locale tag (e.g. "zh-CN", "en").
     */
    public static String getLocaleTag() {
        return currentLocale.toLanguageTag();
    }

    /**
     * Set locale at runtime. Triggers bundle reload.
     * @param tag BCP 47 language tag, e.g. "zh-CN", "en"
     */
    public static void setLocaleTag(String tag) {
        currentLocale = Locale.forLanguageTag(tag);
        reload();
    }

    /**
     * Get a message by key (no arguments).
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }

    /**
     * Get a parameterized message by key.
     * Uses java.text.MessageFormat style placeholders: {0}, {1}, ...
     */
    public static String get(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }
}
