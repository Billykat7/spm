package com.btk.spm.ui.pantry;

import androidx.annotation.NonNull;

import com.btk.spm.data.model.PantryItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The orders the pantry list can be shown in, each with the {@link Comparator} that produces it.
 *
 * <p>The order is applied by {@link PantryViewModel}, never by the adapter (adapters render, they do
 * not decide) and never by a second DAO query, so changing it re-sorts the list the screen already
 * observes. Issue 13 fixes the list at {@link #NAME}; Issue 17 adds the menu that switches between
 * the two, remembers the choice and makes {@link #EXPIRY_SOONEST} the default.
 *
 * <p>Both orders end on the creation time and then the id, the same tie-breaks as
 * {@code PantryItemDao.observeAll()}, so two rows that compare equal never swap places between
 * emissions and the list does not flicker.
 */
public enum SortOrder {

    /**
     * What to use first: the earliest expiry date at the top, items that never expire at the bottom,
     * and items with the same date by name.
     */
    EXPIRY_SOONEST(Comparator.comparing(PantryItem::getExpiryDate,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(byName())),

    /** Alphabetical by name, ignoring letter case, so "eggs" sits between "Butter" and "Flour". */
    NAME(byName());

    private final Comparator<PantryItem> comparator;

    SortOrder(Comparator<PantryItem> comparator) {
        this.comparator = comparator;
    }

    /**
     * Returns the comparator that puts items in this order.
     *
     * @return a total order over pantry items: only the same row compares equal to itself
     */
    @NonNull
    public Comparator<PantryItem> comparator() {
        return comparator;
    }

    /**
     * Returns a sorted copy of {@code items}; the list passed in is not changed, because it is the
     * one Room delivered and other observers may hold it.
     *
     * @param items the pantry as the repository emitted it
     * @return a new, unmodifiable list in this order
     */
    @NonNull
    public List<PantryItem> sort(@NonNull List<PantryItem> items) {
        List<PantryItem> sorted = new ArrayList<>(items);
        sorted.sort(comparator);
        return Collections.unmodifiableList(sorted);
    }

    /** Name ignoring case, then the order the rows were added in, then the id. */
    private static Comparator<PantryItem> byName() {
        return Comparator.comparing(PantryItem::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(PantryItem::getCreatedAt)
                .thenComparingLong(PantryItem::getId);
    }
}
