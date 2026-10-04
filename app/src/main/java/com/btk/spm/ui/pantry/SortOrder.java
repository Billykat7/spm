package com.btk.spm.ui.pantry;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.R;

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
 * observes. The Pantry tab's sort menu switches between the two, and the choice is remembered by its
 * {@link #name()} under {@code PrefKey.PANTRY_SORT}; {@link #EXPIRY_SOONEST} is the default.
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
    EXPIRY_SOONEST(R.id.sort_expiry, Comparator.comparing(PantryItem::getExpiryDate,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(byName())),

    /** Alphabetical by name, ignoring letter case, so "eggs" sits between "Butter" and "Flour". */
    NAME(R.id.sort_name, byName());

    @IdRes
    private final int menuItemId;
    private final Comparator<PantryItem> comparator;

    SortOrder(@IdRes int menuItemId, Comparator<PantryItem> comparator) {
        this.menuItemId = menuItemId;
        this.comparator = comparator;
    }

    /**
     * Returns the item of the Pantry tab's sort menu ({@code menu/pantry_sort.xml}) that picks this
     * order, so the menu can check the one in use.
     *
     * @return a {@code R.id.sort_*} id
     */
    @IdRes
    public int menuItemId() {
        return menuItemId;
    }

    /**
     * Returns the order a sort menu item picks.
     *
     * @param menuItemId the id of the menu item that was chosen
     * @return its order, or {@code null} when the item is not one of the sort choices
     */
    @Nullable
    public static SortOrder fromMenuItemId(@IdRes int menuItemId) {
        for (SortOrder order : values()) {
            if (order.menuItemId == menuItemId) {
                return order;
            }
        }
        return null;
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
