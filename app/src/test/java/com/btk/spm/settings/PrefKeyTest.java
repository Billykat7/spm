package com.btk.spm.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Checks that every {@link PrefKey} has its own stable, lower snake case key string. */
public class PrefKeyTest {

    @Test
    public void everyKeyIsTheConstantNameInLowerCaseAndUnique() {
        Set<String> keys = new HashSet<>();
        for (PrefKey prefKey : PrefKey.values()) {
            assertTrue(prefKey + " key must be lower snake case", prefKey.key().matches("[a-z]+(_[a-z]+)*"));
            assertEquals(prefKey.name().toLowerCase(Locale.ROOT), prefKey.key());
            assertTrue("duplicate key " + prefKey.key(), keys.add(prefKey.key()));
        }
    }

    @Test
    public void hasTheFourSettingsTheFourScreenActionsAndThePantrySort() {
        assertEquals(9, PrefKey.values().length);
        assertEquals("count_expired_items", PrefKey.COUNT_EXPIRED_ITEMS.key());
        // Stored on users' devices from Issue 17 on: changing it would lose their chosen order
        assertEquals("pantry_sort", PrefKey.PANTRY_SORT.key());
    }

    @Test
    public void onlyThePantrySort_isKeptOffTheSettingsScreen() {
        for (PrefKey prefKey : PrefKey.values()) {
            assertEquals(prefKey + " userFacing", prefKey != PrefKey.PANTRY_SORT, prefKey.userFacing());
        }
    }
}
