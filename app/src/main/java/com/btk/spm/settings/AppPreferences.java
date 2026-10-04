package com.btk.spm.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.btk.spm.ui.pantry.SortOrder;

/**
 * The app's settings, read and written by {@link PrefKey}, so no other class names a preference.
 *
 * <p>A thin wrapper over {@link SharedPreferences} holding only what is used so far: the expiring-soon
 * threshold and the pantry's sort order (Issue 17). The Settings screen (Issue 28) adds the rest. It
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
