package com.btk.spm.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.btk.spm.ui.pantry.SortOrder;

/**
 * The app's settings, read and written by {@link PrefKey}, so no other class names a preference.
 *
 * <p>A thin wrapper over {@link SharedPreferences} holding only what is used so far: the expiring-soon
 * threshold and the pantry's sort order (Issue 17), and whether expired items count when matching
 * (Issue 23), read once or observed live through {@link #observeBoolean(PrefKey)} (Issue 26). The
 * Settings screen (Issue 28) adds the rest. It
 * reads the file {@code PreferenceManager} uses for the default preferences, so that screen and this
 * class will see the same values.
 *
 * <p>Every read has a default and survives a bad stored value: a value of the wrong type, or a sort
 * order an older or newer build wrote under a name this build does not know, falls back to the
 * default instead of crashing the screen that asked.
 */
public final class AppPreferences {

    /** Days ahead that still count as expiring soon when the user has chosen none (decision 6). */
    public static final int DEFAULT_EXPIRY_THRESHOLD_DAYS = 3;

    /** Whether expired items count when matching, when the user has chosen nothing: they do not (decision 6). */
    public static final boolean DEFAULT_COUNT_EXPIRED_ITEMS = false;

    /** The pantry's order when the user has chosen none: what to use first at the top. */
    public static final SortOrder DEFAULT_PANTRY_SORT = SortOrder.EXPIRY_SOONEST;

    private final SharedPreferences preferences;

    /**
     * Wraps {@code preferences}; a test passes its own.
     *
     * @param preferences where the settings are stored
     */
    public AppPreferences(@NonNull SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * Returns the settings of this app, in the default preferences file.
     *
     * @param context any context of this app
     * @return the app's preferences
     */
    @NonNull
    public static AppPreferences from(@NonNull Context context) {
        Context app = context.getApplicationContext();
        // The name PreferenceManager.getDefaultSharedPreferences uses, so Issue 28's screen shares it
        return new AppPreferences(app.getSharedPreferences(app.getPackageName() + "_preferences", Context.MODE_PRIVATE));
    }

    /**
     * Returns how many days ahead an item counts as expiring soon.
     *
     * @return the stored threshold, or {@link #DEFAULT_EXPIRY_THRESHOLD_DAYS} when none is stored or the
     *     stored value is not a whole number of 0 or more
     */
    public int getExpiryThresholdDays() {
        try {
            int days = preferences.getInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), DEFAULT_EXPIRY_THRESHOLD_DAYS);
            return days >= 0 ? days : DEFAULT_EXPIRY_THRESHOLD_DAYS;
        } catch (ClassCastException storedAsAnotherType) {
            return DEFAULT_EXPIRY_THRESHOLD_DAYS;
        }
    }

    /**
     * Returns whether expired items still count when recipes are matched (decision 6). The Suggested
     * Recipes screen passes it to the matcher as {@code MatchOptions.includeExpired}; the Settings
     * screen that changes it is Issue 28, so until then this is the default.
     *
     * @return the stored choice, or {@link #DEFAULT_COUNT_EXPIRED_ITEMS} when none is stored or the
     *     stored value is not a boolean
     */
    public boolean isCountExpiredItems() {
        return readBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
    }

    /**
     * Returns a boolean setting as {@code LiveData}: its current value as soon as it is observed, then
     * a new value every time the setting changes, from the Settings screen or anywhere else.
     *
     * <p>This is how a change reaches a screen that is already open. The Recipes tab observes
     * {@link PrefKey#COUNT_EXPIRED_ITEMS} through it, so turning the setting on runs the matcher again
     * and a recipe that needs an expired item appears (decision 6). The listener is registered only
     * while something observes, and is held by the returned object, because {@code SharedPreferences}
     * keeps its listeners weakly and would otherwise drop one nobody else references.
     *
     * @param key a boolean setting; only {@link PrefKey#COUNT_EXPIRED_ITEMS} so far (Issue 28 adds the others)
     * @return the observed value, falling back to the setting's default as {@link #isCountExpiredItems()} does
     * @throws IllegalArgumentException if {@code key} is not a boolean setting this class knows a default for
     */
    @NonNull
    public LiveData<Boolean> observeBoolean(@NonNull PrefKey key) {
        defaultOf(key); // refuse a key with no boolean default now, not on the first change
        return new BooleanPreference(key);
    }

    /** Reads a boolean setting, or its default when none is stored or the stored value is another type. */
    private boolean readBoolean(@NonNull PrefKey key) {
        boolean fallback = defaultOf(key);
        try {
            return preferences.getBoolean(key.key(), fallback);
        } catch (ClassCastException storedAsAnotherType) {
            return fallback;
        }
    }

    /** The default of each boolean setting; the others are not booleans, or not decided yet. */
    private static boolean defaultOf(@NonNull PrefKey key) {
        if (key == PrefKey.COUNT_EXPIRED_ITEMS) {
            return DEFAULT_COUNT_EXPIRED_ITEMS;
        }
        throw new IllegalArgumentException(key + " has no boolean default");
    }

    /**
     * One boolean setting as {@code LiveData}. It listens to {@code SharedPreferences} only while it
     * has an active observer, and holds its listener in a field, the strong reference
     * {@code SharedPreferences} does not keep.
     */
    private final class BooleanPreference extends LiveData<Boolean> {

        private final PrefKey key;

        /** Called on the main thread; {@code changedKey} is {@code null} when the whole file was cleared. */
        private final SharedPreferences.OnSharedPreferenceChangeListener listener;

        BooleanPreference(@NonNull PrefKey key) {
            this.key = key;
            this.listener = (prefs, changedKey) -> {
                if (changedKey == null || changedKey.equals(key.key())) {
                    publish();
                }
            };
        }

        @Override
        protected void onActive() {
            preferences.registerOnSharedPreferenceChangeListener(listener);
            // A change made while nobody listened is caught up here; an unchanged value is not sent again
            publish();
        }

        @Override
        protected void onInactive() {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }

        /** Sends the stored value, unless it is the one already sent. */
        private void publish() {
            boolean now = readBoolean(key);
            @Nullable Boolean sent = getValue();
            if (sent == null || sent != now) {
                setValue(now);
            }
        }
    }

    /**
     * Returns the order the pantry list was last shown in.
     *
     * @return the stored order, or {@link #DEFAULT_PANTRY_SORT} when none is stored or the stored name
     *     is not a {@link SortOrder} this build knows
     */
    @NonNull
    public SortOrder getPantrySort() {
        try {
            String name = preferences.getString(PrefKey.PANTRY_SORT.key(), null);
            return name == null ? DEFAULT_PANTRY_SORT : SortOrder.valueOf(name);
        } catch (IllegalArgumentException | ClassCastException unknownOrWrongType) {
            return DEFAULT_PANTRY_SORT;
        }
    }

    /**
     * Remembers the pantry's order by its enum name, never its ordinal, so reordering
     * {@link SortOrder} cannot change what a saved choice means. Written in the background.
     *
     * @param order the order the user chose
     */
    public void setPantrySort(@NonNull SortOrder order) {
        preferences.edit().putString(PrefKey.PANTRY_SORT.key(), order.name()).apply();
    }
}
