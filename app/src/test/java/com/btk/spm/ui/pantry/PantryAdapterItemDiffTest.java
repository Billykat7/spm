package com.btk.spm.ui.pantry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Pins the rule {@code ListAdapter} uses to decide what to redraw: same id means the same row, and
 * the row is rebound only when a column changed.
 */
public class PantryAdapterItemDiffTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 10, 20);
    private static final long CREATED = 1_790_000_000_000L;

    private final PantryAdapter.ItemDiff diff = new PantryAdapter.ItemDiff();
    private final PantryItem eggs = new PantryItem(7, "Eggs", 6, Unit.PCS, EXPIRY, CREATED);

    @Test
    public void theSameRowReadAgain_isTheSameItemWithTheSameContents() {
        PantryItem readAgain = new PantryItem(7, "Eggs", 6, Unit.PCS, EXPIRY, CREATED);

        assertTrue(diff.areItemsTheSame(eggs, readAgain));
        assertTrue(diff.areContentsTheSame(eggs, readAgain));
    }

    @Test
    public void anEditedQuantity_isTheSameItemWithChangedContents() {
        PantryItem fewerEggs = new PantryItem(7, "Eggs", 4, Unit.PCS, EXPIRY, CREATED);

        assertTrue(diff.areItemsTheSame(eggs, fewerEggs));
        assertFalse(diff.areContentsTheSame(eggs, fewerEggs));
    }

    @Test
    public void everyOtherEditedColumn_changesTheContents() {
        assertFalse(diff.areContentsTheSame(eggs, new PantryItem(7, "Duck eggs", 6, Unit.PCS, EXPIRY, CREATED)));
        assertFalse(diff.areContentsTheSame(eggs, new PantryItem(7, "Eggs", 6, Unit.G, EXPIRY, CREATED)));
        assertFalse(diff.areContentsTheSame(eggs, new PantryItem(7, "Eggs", 6, Unit.PCS, null, CREATED)));
    }

    @Test
    public void anotherRowWithTheSameValues_isADifferentItem() {
        PantryItem secondBox = new PantryItem(8, "Eggs", 6, Unit.PCS, EXPIRY, CREATED);

        assertFalse(diff.areItemsTheSame(eggs, secondBox));
    }
}
