package com.btk.spm.settings;

/**
 * Every key the app stores in {@code SharedPreferences} or shows on the Settings screen, each carrying
 * its key string.
 *
 * <p>The Settings screen's XML ({@code res/xml/preferences.xml}), the {@link AppPreferences} wrapper
 * and every reader use the same constant (Issue 28), so a preference cannot be written under one
 * spelling and read under another. {@code PreferencesXmlTest} reads the XML on the JVM and fails when
 * a key in it is not one of these, or when a {@link #userFacing()} key is missing from it.
 *
 * <p>The key strings are what is persisted on the device: renaming a constant is free, changing its
 * {@link #key()} loses the user's saved value.
 */
public enum PrefKey {

    /** Whether the daily expiring-soon notification is on (Issue 29, default on). */
    EXPIRY_ALERTS_ENABLED("expiry_alerts_enabled", true),

    /** How many days ahead an item counts as expiring soon (decision 6, 1–14, default 3). */
    EXPIRY_THRESHOLD_DAYS("expiry_threshold_days", true),

    /** Which units quantities are displayed in; display only, never used for matching (decision 5). */
    UNITS_SYSTEM("units_system", true),

    /** Whether expired items still count when matching recipes (decision 6, default off). */
    COUNT_EXPIRED_ITEMS("count_expired_items", true),

    /** The "Send a test alert now" action, which runs the daily check at once; nothing is stored under it. */
    SEND_TEST_ALERT("send_test_alert", true),

    /**
     * The shortcut to the app's notification settings, shown only while the alert cannot be posted
     * (Issue 29); nothing is stored under it.
     */
    NOTIFICATION_SETTINGS("notification_settings", true),

    /** The version name shown under About; nothing is stored under it. */
    ABOUT_VERSION("about_version", true),

    /** The link to the project's repository under About; nothing is stored under it. */
    ABOUT_REPOSITORY("about_repository", true),

    /**
     * The pantry list's order, a {@code SortOrder} stored by its name (Issue 17, default soonest expiry
     * first). Chosen from the Pantry tab's toolbar, so it is stored but not on the Settings screen.
     */
    PANTRY_SORT("pantry_sort", false);

    private final String key;
    private final boolean userFacing;

    PrefKey(String key, boolean userFacing) {
        this.key = key;
        this.userFacing = userFacing;
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

    /**
     * Says whether this key is on the Settings screen. A key that is stored but set somewhere else,
     * such as the pantry's sort order, may stay out of {@code preferences.xml}; every other key must
     * be in it exactly once.
     *
     * @return {@code true} when {@code preferences.xml} must have a preference with this key
     */
    public boolean userFacing() {
        return userFacing;
    }
}
