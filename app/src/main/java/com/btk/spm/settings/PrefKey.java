package com.btk.spm.settings;

/**
 * Every key the app stores in {@code SharedPreferences}, each carrying its key string.
 *
 * <p>The Settings screen's XML, the {@code AppPreferences} wrapper and every reader use the same
 * constant (Issue 28), so a preference cannot be written under one spelling and read under another.
 * The key strings are what is persisted on the device: renaming a constant is free, changing its
 * {@link #key()} loses the user's saved value.
 */
public enum PrefKey {

    /** Whether the daily expiring-soon notification is on (Issue 29). */
    EXPIRY_ALERTS_ENABLED("expiry_alerts_enabled"),

    /** How many days ahead an item counts as expiring soon (decision 6, default 3). */
    EXPIRY_THRESHOLD_DAYS("expiry_threshold_days"),

    /** Which units quantities are displayed in; display only, never used for matching (decision 5). */
    UNITS_SYSTEM("units_system"),

    /** Whether expired items still count when matching recipes (decision 6, default off). */
    COUNT_EXPIRED_ITEMS("count_expired_items");

    private final String key;

    PrefKey(String key) {
        this.key = key;
    }

    /**
     * Returns the string this preference is stored under, as used by {@code SharedPreferences} and
     * by {@code android:key} in the preferences XML.
     *
     * @return the key, lower snake case and never {@code null}
     */
    public String key() {
        return key;
    }
}
