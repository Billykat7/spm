package com.btk.spm.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btk.spm.ui.pantry.SortOrder;

import org.junit.Test;

/**
 * {@link AppPreferences} over an in-memory {@code SharedPreferences}: the defaults, a round trip by
 * enum name, and a bad stored value falling back to the default instead of crashing.
 */
public class AppPreferencesTest {

    private final InMemorySharedPreferences stored = new InMemorySharedPreferences();
    private final AppPreferences preferences = new AppPreferences(stored);

    @Test
    public void nothingStored_givesThreeDaysAndSoonestExpiryFirst() {
        assertEquals(3, preferences.getExpiryThresholdDays());
        assertEquals(SortOrder.EXPIRY_SOONEST, preferences.getPantrySort());
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
}
