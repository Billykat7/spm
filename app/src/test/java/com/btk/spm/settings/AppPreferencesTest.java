package com.btk.spm.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.ui.pantry.SortOrder;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AppPreferences} over an in-memory {@code SharedPreferences}: the defaults, a round trip by
 * enum name, a bad stored value falling back to the default instead of crashing, and the
 * {@code LiveData} wrappers sending the current value, then each change of their own key only.
 */
public class AppPreferencesTest {

    @Rule
    public final InstantTaskExecutorRule liveDataOnTheTestThread = new InstantTaskExecutorRule();

    private final InMemorySharedPreferences stored = new InMemorySharedPreferences();
    private final AppPreferences preferences = new AppPreferences(stored);

    @Test
    public void nothingStored_givesThreeDaysAndSoonestExpiryFirst() {
        assertEquals(3, preferences.getExpiryThresholdDays());
        assertEquals(SortOrder.EXPIRY_SOONEST, preferences.getPantrySort());
    }

    @Test
    public void nothingStored_givesTheDefaultsOfAllFourSettings() {
        assertTrue(preferences.isExpiryAlertsEnabled());
        assertEquals(3, preferences.getExpiryThresholdDays());
        assertEquals(UnitsSystem.METRIC, preferences.getUnitsSystem());
        assertFalse(preferences.isCountExpiredItems());
    }

    @Test
    public void defaultValue_isWhatTheGettersFallBackTo_andNothingForTheScreenActions() {
        assertEquals(true, AppPreferences.defaultValue(PrefKey.EXPIRY_ALERTS_ENABLED));
        assertEquals(3, AppPreferences.defaultValue(PrefKey.EXPIRY_THRESHOLD_DAYS));
        assertEquals("METRIC", AppPreferences.defaultValue(PrefKey.UNITS_SYSTEM));
        assertEquals(false, AppPreferences.defaultValue(PrefKey.COUNT_EXPIRED_ITEMS));
        assertEquals("EXPIRY_SOONEST", AppPreferences.defaultValue(PrefKey.PANTRY_SORT));
        assertNull(AppPreferences.defaultValue(PrefKey.SEND_TEST_ALERT));
        assertNull(AppPreferences.defaultValue(PrefKey.ABOUT_VERSION));
        assertNull(AppPreferences.defaultValue(PrefKey.ABOUT_REPOSITORY));
    }

    @Test
    public void storedAlertsAndUnits_areRead() {
        stored.edit().putBoolean(PrefKey.EXPIRY_ALERTS_ENABLED.key(), false)
                .putString(PrefKey.UNITS_SYSTEM.key(), UnitsSystem.IMPERIAL.name()).apply();

        assertFalse(preferences.isExpiryAlertsEnabled());
        assertEquals(UnitsSystem.IMPERIAL, preferences.getUnitsSystem());
    }

    @Test
    public void anUnknownOrWronglyTypedUnitsSystem_fallsBackToMetric() {
        stored.edit().putString(PrefKey.UNITS_SYSTEM.key(), "CUBITS").apply();
        assertEquals(UnitsSystem.METRIC, preferences.getUnitsSystem());

        stored.edit().putBoolean(PrefKey.UNITS_SYSTEM.key(), true).apply();
        assertEquals(UnitsSystem.METRIC, preferences.getUnitsSystem());
    }

    @Test
    public void aThresholdOutsideTheSeekBarsRange_fallsBackToTheDefault() {
        for (int outside : new int[] {0, 15, 99}) {
            stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), outside).apply();
            assertEquals("stored " + outside, 3, preferences.getExpiryThresholdDays());
        }
        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 1).apply();
        assertEquals(1, preferences.getExpiryThresholdDays());
        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 14).apply();
        assertEquals(14, preferences.getExpiryThresholdDays());
    }

    @Test
    public void aStoredThreshold_isRead() {
        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 7).apply();

        assertEquals(7, preferences.getExpiryThresholdDays());
    }

    @Test
    public void aNegativeOrWronglyTypedThreshold_fallsBackToTheDefault() {
        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), -1).apply();
        assertEquals(AppPreferences.DEFAULT_EXPIRY_THRESHOLD_DAYS, preferences.getExpiryThresholdDays());

        // A settings screen built on EditTextPreference would store the number as text
        stored.edit().putString(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), "5").apply();
        assertEquals(AppPreferences.DEFAULT_EXPIRY_THRESHOLD_DAYS, preferences.getExpiryThresholdDays());
    }

    @Test
    public void theSortOrder_isStoredByItsEnumName_andReadBack() {
        preferences.setPantrySort(SortOrder.NAME);

        assertEquals(SortOrder.NAME.name(), stored.getString(PrefKey.PANTRY_SORT.key(), null));
        assertEquals(SortOrder.NAME, preferences.getPantrySort());

        preferences.setPantrySort(SortOrder.EXPIRY_SOONEST);
        assertEquals(SortOrder.EXPIRY_SOONEST, preferences.getPantrySort());
    }

    @Test
    public void anUnknownStoredSortOrder_fallsBackToTheDefault() {
        stored.edit().putString(PrefKey.PANTRY_SORT.key(), "BY_COLOUR").apply();
        assertEquals(AppPreferences.DEFAULT_PANTRY_SORT, preferences.getPantrySort());

        stored.edit().putInt(PrefKey.PANTRY_SORT.key(), 1).apply();
        assertEquals(AppPreferences.DEFAULT_PANTRY_SORT, preferences.getPantrySort());
    }

    @Test
    public void nothingStored_leavesExpiredItemsOut() {
        assertFalse(preferences.isCountExpiredItems());
    }

    @Test
    public void aStoredCountExpiredChoice_isRead() {
        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).apply();

        assertTrue(preferences.isCountExpiredItems());
    }

    @Test
    public void aWronglyTypedCountExpiredChoice_fallsBackToTheDefault() {
        stored.edit().putString(PrefKey.COUNT_EXPIRED_ITEMS.key(), "true").apply();

        assertEquals(AppPreferences.DEFAULT_COUNT_EXPIRED_ITEMS, preferences.isCountExpiredItems());
    }

    @Test
    public void observeBoolean_sendsTheCurrentValue_thenEachChange() {
        LiveData<Boolean> countExpired = preferences.observeBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
        List<Boolean> seen = new ArrayList<>();
        countExpired.observeForever(seen::add);

        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).apply();
        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), false).apply();

        assertEquals(List.of(false, true, false), seen);
    }

    @Test
    public void observeBoolean_ignoresOtherKeys_andAnUnchangedValue() {
        LiveData<Boolean> countExpired = preferences.observeBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
        List<Boolean> seen = new ArrayList<>();
        countExpired.observeForever(seen::add);

        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 5).apply();
        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), false).apply();

        assertEquals(List.of(false), seen);
    }

    @Test
    public void observeBoolean_stopsListening_whenNobodyObserves_andCatchesUpWhenObservedAgain() {
        LiveData<Boolean> countExpired = preferences.observeBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
        List<Boolean> seen = new ArrayList<>();
        Observer<Boolean> observer = seen::add;
        countExpired.observeForever(observer);
        assertEquals(1, stored.listenerCount());

        countExpired.removeObserver(observer);
        assertEquals(0, stored.listenerCount());
        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).apply();
        assertEquals(List.of(false), seen);

        countExpired.observeForever(observer);
        assertEquals(List.of(false, true), seen);
    }

    @Test
    public void observeBoolean_refusesASettingThatIsNotABoolean() {
        assertThrows(IllegalArgumentException.class, () -> preferences.observeBoolean(PrefKey.PANTRY_SORT));
        assertThrows(IllegalArgumentException.class, () -> preferences.observeBoolean(PrefKey.EXPIRY_THRESHOLD_DAYS));
        assertThrows(IllegalArgumentException.class, () -> preferences.observeBoolean(PrefKey.SEND_TEST_ALERT));
    }

    @Test
    public void observeBoolean_watchesTheAlertsSwitchToo() {
        List<Boolean> seen = new ArrayList<>();
        preferences.observeBoolean(PrefKey.EXPIRY_ALERTS_ENABLED).observeForever(seen::add);

        stored.edit().putBoolean(PrefKey.EXPIRY_ALERTS_ENABLED.key(), false).apply();

        assertEquals(List.of(true, false), seen);
    }

    @Test
    public void observeInt_sendsTheThreshold_thenEachChange_andIgnoresOtherKeys() {
        List<Integer> seen = new ArrayList<>();
        preferences.observeInt(PrefKey.EXPIRY_THRESHOLD_DAYS).observeForever(seen::add);

        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 7).apply();
        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).apply();
        stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 99).apply(); // out of range: the default

        assertEquals(List.of(3, 7, 3), seen);
    }

    @Test
    public void observeInt_refusesASettingThatIsNotAWholeNumber() {
        assertThrows(IllegalArgumentException.class, () -> preferences.observeInt(PrefKey.COUNT_EXPIRED_ITEMS));
        assertThrows(IllegalArgumentException.class, () -> preferences.observeInt(PrefKey.UNITS_SYSTEM));
    }

    @Test
    public void observeUnitsSystem_sendsMetric_thenImperial_andStopsListeningWhenUnobserved() {
        LiveData<UnitsSystem> units = preferences.observeUnitsSystem();
        List<UnitsSystem> seen = new ArrayList<>();
        Observer<UnitsSystem> observer = seen::add;
        units.observeForever(observer);

        stored.edit().putString(PrefKey.UNITS_SYSTEM.key(), UnitsSystem.IMPERIAL.name()).apply();
        units.removeObserver(observer);

        assertEquals(List.of(UnitsSystem.METRIC, UnitsSystem.IMPERIAL), seen);
        assertEquals(0, stored.listenerCount());
    }
}
