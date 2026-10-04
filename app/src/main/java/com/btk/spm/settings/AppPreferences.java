package com.btk.spm.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.preference.PreferenceManager;

import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.ui.pantry.SortOrder;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The app's settings, read by {@link PrefKey}, so no other class names a preference.
 *
 * <p>A typed wrapper over the default {@link SharedPreferences} file, the one the Settings screen's
 * {@code PreferenceFragmentCompat} writes to (Issue 28). Four settings change what the app does, and
 * each has one reader here:
 * <ul>
 *   <li>{@link #isExpiryAlertsEnabled()}: whether the daily expiring-soon check posts anything (Issue 29);</li>
 *   <li>{@link #getExpiryThresholdDays()}: how many days ahead is "expiring soon", for the pantry badges
 *       and the alert (decision 6);</li>
 *   <li>{@link #getUnitsSystem()}: metric or imperial amounts on screen, display only (decision 5);</li>
 *   <li>{@link #isCountExpiredItems()}: whether expired items count when matching (decision 6).</li>
 * </ul>
 * The pantry's sort order (Issue 17) is stored here too, though it is chosen on the Pantry tab.
 *
 * <p>The defaults live in this class only, as constants, and {@link #defaultValue(PrefKey)} hands
 * them to {@code PreferencesXmlTest}, which fails if {@code preferences.xml} declares another. Every
 * read survives a bad stored value: the wrong type, a number out of range, or an enum name this build
 * does not know falls back to the default instead of crashing the screen that asked.
 *
 * <p>A screen that is already open hears a change through {@link #observeBoolean(PrefKey)},
 * {@link #observeInt(PrefKey)} or {@link #observeUnitsSystem()}: {@code LiveData} over a change
 * listener, so turning <i>Count expired items</i> on runs the matcher again and moving the threshold
 * re-badges the pantry without leaving the tab.
 */
public final class AppPreferences {

    /** Expiring-soon alerts are on until the user turns them off. */
    public static final boolean DEFAULT_EXPIRY_ALERTS_ENABLED = true;

    /** Days ahead that still count as expiring soon when the user has chosen none (decision 6). */
    public static final int DEFAULT_EXPIRY_THRESHOLD_DAYS = 3;

    /** The shortest threshold the Settings screen's seek bar offers: tomorrow. */
    public static final int MIN_EXPIRY_THRESHOLD_DAYS = 1;

    /** The longest threshold the seek bar offers: two weeks. */
    public static final int MAX_EXPIRY_THRESHOLD_DAYS = 14;

    /** Amounts are shown in grams, kilograms, millilitres and litres until the user chooses imperial. */
    public static final UnitsSystem DEFAULT_UNITS_SYSTEM = UnitsSystem.METRIC;

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
     * Returns the settings of this app, in the default preferences file that the Settings screen writes.
     *
     * @param context any context of this app
     * @return the app's preferences
     */
    @NonNull
    public static AppPreferences from(@NonNull Context context) {
        return new AppPreferences(PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()));
    }

    /**
     * Returns the default of a setting, as {@code preferences.xml} must declare it in
     * {@code android:defaultValue}: a {@code Boolean}, an {@code Integer}, or an enum's name.
     *
     * @param key any key
     * @return the default, or {@code null} for the Settings screen's actions, which store nothing
     */
    @Nullable
    public static Object defaultValue(@NonNull PrefKey key) {
        // A switch expression, so a new PrefKey does not compile until its default is decided here
        return switch (key) {
            case EXPIRY_ALERTS_ENABLED -> DEFAULT_EXPIRY_ALERTS_ENABLED;
            case EXPIRY_THRESHOLD_DAYS -> DEFAULT_EXPIRY_THRESHOLD_DAYS;
            case UNITS_SYSTEM -> DEFAULT_UNITS_SYSTEM.name();
            case COUNT_EXPIRED_ITEMS -> DEFAULT_COUNT_EXPIRED_ITEMS;
            case PANTRY_SORT -> DEFAULT_PANTRY_SORT.name();
            case SEND_TEST_ALERT, ABOUT_VERSION, ABOUT_REPOSITORY -> null;
        };
    }

    /**
     * Returns whether the daily expiring-soon notification is on (Issue 29).
     *
     * @return the stored choice, or {@link #DEFAULT_EXPIRY_ALERTS_ENABLED} when none is stored or the
     *     stored value is not a boolean
     */
    public boolean isExpiryAlertsEnabled() {
        return readBoolean(PrefKey.EXPIRY_ALERTS_ENABLED);
    }

    /**
     * Returns how many days ahead an item counts as expiring soon: the pantry badges and the alert
     * both pass it to {@code ExpiryRules}.
     *
     * @return the stored threshold, or {@link #DEFAULT_EXPIRY_THRESHOLD_DAYS} when none is stored, the
     *     stored value is not a whole number, or it is outside {@link #MIN_EXPIRY_THRESHOLD_DAYS}–
     *     {@link #MAX_EXPIRY_THRESHOLD_DAYS}
     */
    public int getExpiryThresholdDays() {
        return readInt(PrefKey.EXPIRY_THRESHOLD_DAYS);
    }

    /**
     * Returns the units amounts are shown in. Display only: a match never depends on it (decision 5).
     *
     * @return the stored system, or {@link #DEFAULT_UNITS_SYSTEM} when none is stored or the stored
     *     name is not a {@link UnitsSystem} this build knows
     */
    @NonNull
    public UnitsSystem getUnitsSystem() {
        return readEnum(PrefKey.UNITS_SYSTEM, UnitsSystem.class, DEFAULT_UNITS_SYSTEM);
    }

    /**
     * Returns whether expired items still count when recipes are matched (decision 6). The Recipes
     * tab and the detail screen pass it to the matcher as {@code MatchOptions.includeExpired}.
     *
     * @return the stored choice, or {@link #DEFAULT_COUNT_EXPIRED_ITEMS} when none is stored or the
     *     stored value is not a boolean
     */
    public boolean isCountExpiredItems() {
        return readBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
    }

    /**
     * Returns the order the pantry list was last shown in.
     *
     * @return the stored order, or {@link #DEFAULT_PANTRY_SORT} when none is stored or the stored name
     *     is not a {@link SortOrder} this build knows
     */
    @NonNull
    public SortOrder getPantrySort() {
        return readEnum(PrefKey.PANTRY_SORT, SortOrder.class, DEFAULT_PANTRY_SORT);
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

    /**
     * Returns a boolean setting as {@code LiveData}: its current value as soon as it is observed, then
     * a new value every time the setting changes, from the Settings screen or anywhere else.
     *
     * <p>This is how a change reaches a screen that is already open. The Recipes tab observes
     * {@link PrefKey#COUNT_EXPIRED_ITEMS} through it, so turning the setting on runs the matcher again
     * and a recipe that needs an expired item appears (decision 6).
     *
     * @param key a boolean setting: {@link PrefKey#EXPIRY_ALERTS_ENABLED} or {@link PrefKey#COUNT_EXPIRED_ITEMS}
     * @return the observed value, falling back to the setting's default as the typed getters do
     * @throws IllegalArgumentException if {@code key} is not a boolean setting
     */
    @NonNull
    public LiveData<Boolean> observeBoolean(@NonNull PrefKey key) {
        booleanDefault(key); // refuse a key that is not a boolean now, not on the first change
        return new PreferenceLiveData<>(key, () -> readBoolean(key));
    }

    /**
     * Returns a whole-number setting as {@code LiveData}, current value first and then each change. The
     * Pantry tab observes {@link PrefKey#EXPIRY_THRESHOLD_DAYS} through it, so moving the seek bar
     * re-badges the rows without leaving the tab.
     *
     * @param key a whole-number setting: {@link PrefKey#EXPIRY_THRESHOLD_DAYS}, the only one
     * @return the observed value, falling back to the default as {@link #getExpiryThresholdDays()} does
     * @throws IllegalArgumentException if {@code key} is not a whole-number setting
     */
    @NonNull
    public LiveData<Integer> observeInt(@NonNull PrefKey key) {
        intDefault(key);
        return new PreferenceLiveData<>(key, () -> readInt(key));
    }

    /**
     * Returns the units preference as {@code LiveData}, current value first and then each change, so
     * the pantry list and the detail screen redraw their amounts when the user switches system.
     *
     * @return the observed system, falling back to the default as {@link #getUnitsSystem()} does
     */
    @NonNull
    public LiveData<UnitsSystem> observeUnitsSystem() {
        return new PreferenceLiveData<>(PrefKey.UNITS_SYSTEM, this::getUnitsSystem);
    }

    /** Reads a boolean setting, or its default when none is stored or the stored value is another type. */
    private boolean readBoolean(@NonNull PrefKey key) {
        boolean fallback = booleanDefault(key);
        try {
            return preferences.getBoolean(key.key(), fallback);
        } catch (ClassCastException storedAsAnotherType) {
            return fallback;
        }
    }

    /**
     * Reads a whole-number setting, or its default when none is stored, the stored value is another
     * type, or it is outside the range the seek bar allows.
     */
    private int readInt(@NonNull PrefKey key) {
        int fallback = intDefault(key);
        try {
            int stored = preferences.getInt(key.key(), fallback);
            return stored >= MIN_EXPIRY_THRESHOLD_DAYS && stored <= MAX_EXPIRY_THRESHOLD_DAYS ? stored : fallback;
        } catch (ClassCastException storedAsAnotherType) {
            return fallback;
        }
    }

    /** Reads an enum stored by its name, or {@code fallback} when the name is missing or unknown. */
    @NonNull
    private <E extends Enum<E>> E readEnum(@NonNull PrefKey key, @NonNull Class<E> type, @NonNull E fallback) {
        try {
            String name = preferences.getString(key.key(), null);
            return name == null ? fallback : Enum.valueOf(type, name);
        } catch (IllegalArgumentException | ClassCastException unknownOrWrongType) {
            return fallback;
        }
    }

    private static boolean booleanDefault(@NonNull PrefKey key) {
        if (defaultValue(key) instanceof Boolean fallback) {
            return fallback;
        }
        throw new IllegalArgumentException(key + " is not a boolean setting");
    }

    /** The default of a whole-number setting; the threshold is the only one, and its range is the seek bar's. */
    private static int intDefault(@NonNull PrefKey key) {
        if (key == PrefKey.EXPIRY_THRESHOLD_DAYS) {
            return DEFAULT_EXPIRY_THRESHOLD_DAYS;
        }
        throw new IllegalArgumentException(key + " is not a whole-number setting");
    }

    /**
     * One setting as {@code LiveData}. It listens to {@code SharedPreferences} only while it has an
     * active observer, and holds its one listener in a field: {@code SharedPreferences} keeps its
     * listeners weakly, so a listener nobody else references would be collected and fall silent.
     *
     * @param <T> the setting's type, as its typed getter returns it
     */
    private final class PreferenceLiveData<T> extends LiveData<T> {

        private final Supplier<T> read;

        /** Called on the main thread; {@code changedKey} is {@code null} when the whole file was cleared. */
        private final SharedPreferences.OnSharedPreferenceChangeListener listener;

        PreferenceLiveData(@NonNull PrefKey key, @NonNull Supplier<T> read) {
            this.read = read;
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
            T now = read.get();
            if (!isInitialized() || !Objects.equals(getValue(), now)) {
                setValue(now);
            }
        }
    }
}
