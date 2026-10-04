package com.btk.spm.ui.pantry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.btk.spm.R;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Pins both pantry orders, their tie-breaks, and that sorting never touches the list Room emitted. */
public class SortOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @Test
    public void name_ignoresLetterCase() {
        List<PantryItem> items = Arrays.asList(
                item(1, "flour", null, 10), item(2, "Eggs", null, 20), item(3, "banana", null, 30));

        assertEquals(Arrays.asList("banana", "Eggs", "flour"), names(SortOrder.NAME.sort(items)));
    }

    @Test
    public void name_breaksTiesByCreationTimeThenId() {
        PantryItem laterRice = item(1, "rice", null, 200);
        PantryItem earlierRice = item(2, "Rice", null, 100);
        PantryItem sameTimeHigherId = item(9, "rice", null, 100);

        assertEquals(Arrays.asList(earlierRice, sameTimeHigherId, laterRice),
                SortOrder.NAME.sort(Arrays.asList(laterRice, sameTimeHigherId, earlierRice)));
    }

    @Test
    public void expirySoonest_putsTheEarliestDateFirst_andNoDateLast() {
        List<PantryItem> items = Arrays.asList(
                item(1, "salt", null, 10),
                item(2, "milk", TODAY.plusDays(2), 20),
                item(3, "bread", TODAY.minusDays(1), 30),
                item(4, "rice", TODAY.plusDays(90), 40));

        assertEquals(Arrays.asList("bread", "milk", "rice", "salt"), names(SortOrder.EXPIRY_SOONEST.sort(items)));
    }

    @Test
    public void expirySoonest_ordersTheSameDateAndTheUndated_byName() {
        List<PantryItem> items = Arrays.asList(
                item(1, "yoghurt", TODAY, 10), item(2, "Cream", TODAY, 20),
                item(3, "sugar", null, 30), item(4, "Flour", null, 40));

        assertEquals(Arrays.asList("Cream", "yoghurt", "Flour", "sugar"),
                names(SortOrder.EXPIRY_SOONEST.sort(items)));
    }

    @Test
    public void sort_returnsANewUnmodifiableList_andLeavesTheInputAlone() {
        List<PantryItem> emitted = new ArrayList<>(Arrays.asList(item(1, "b", null, 1), item(2, "a", null, 2)));
        List<PantryItem> before = new ArrayList<>(emitted);

        List<PantryItem> sorted = SortOrder.NAME.sort(emitted);

        assertEquals(before, emitted);
        assertThrows(UnsupportedOperationException.class, () -> sorted.add(item(3, "c", null, 3)));
    }

    @Test
    public void sort_ofAnEmptyPantry_isEmpty() {
        for (SortOrder order : SortOrder.values()) {
            assertEquals(order.name(), 0, order.sort(new ArrayList<>()).size());
        }
    }

    @Test
    public void everyOrder_isPickedByItsOwnMenuItem_andAnotherItemPicksNone() {
        for (SortOrder order : SortOrder.values()) {
            assertEquals(order, SortOrder.fromMenuItemId(order.menuItemId()));
        }
        assertNotEquals(SortOrder.EXPIRY_SOONEST.menuItemId(), SortOrder.NAME.menuItemId());
        // The "Sort by" action only opens the choices; it is not one of them
        assertNull(SortOrder.fromMenuItemId(R.id.action_sort));
    }

    private static PantryItem item(long id, String name, LocalDate expiry, long createdAt) {
        return new PantryItem(id, name, 1, Unit.PCS, expiry, createdAt);
    }

    private static List<String> names(List<PantryItem> items) {
        return items.stream().map(PantryItem::getName).collect(Collectors.toList());
    }
}
