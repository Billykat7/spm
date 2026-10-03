package com.btk.spm.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.R;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Checks that every {@link Tab} maps to its own menu item and title, and reads back safely. */
public class TabTest {

    @Test
    public void tabsAreInTheBottomNavigationsOrder() {
        assertEquals(3, Tab.values().length);
        assertSame(Tab.PANTRY, Tab.values()[0]);
        assertEquals(R.id.nav_pantry, Tab.PANTRY.menuItemId());
        assertEquals(R.id.nav_recipes, Tab.RECIPES.menuItemId());
        assertEquals(R.id.nav_settings, Tab.SETTINGS.menuItemId());
    }

    @Test
    public void everyTabHasItsOwnMenuItemAndTitle() {
        Set<Integer> items = new HashSet<>();
        Set<Integer> titles = new HashSet<>();
        for (Tab tab : Tab.values()) {
            assertTrue(tab + " shares a menu item", items.add(tab.menuItemId()));
            assertTrue(tab + " shares a title", titles.add(tab.titleRes()));
        }
    }

    @Test
    public void fromMenuItemIdFindsEveryTab() {
        for (Tab tab : Tab.values()) {
            assertSame(tab, Tab.fromMenuItemId(tab.menuItemId()));
        }
    }

    @Test
    public void fromMenuItemIdRefusesAnUnknownItem() {
        assertThrows(IllegalArgumentException.class, () -> Tab.fromMenuItemId(R.id.fragment_container));
    }

    @Test
    public void fromNameReadsEveryTabBack() {
        for (Tab tab : Tab.values()) {
            assertSame(tab, Tab.fromName(tab.name(), Tab.PANTRY));
        }
    }

    @Test
    public void fromNameFallsBackOnAMissingOrUnknownName() {
        assertSame(Tab.PANTRY, Tab.fromName(null, Tab.PANTRY));
        assertSame(Tab.PANTRY, Tab.fromName("recipes", Tab.PANTRY));
        assertSame(Tab.SETTINGS, Tab.fromName("SHOPPING", Tab.SETTINGS));
    }
}
