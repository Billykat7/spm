package com.btk.spm.data.mapping;

import androidx.annotation.NonNull;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.matching.PantryEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Turns pantry rows into what the matcher takes: each {@link PantryItem} entity becomes a
 * {@link PantryEntry} value with the same name, amount, unit and expiry.
 *
 * <p>This class and {@link RecipeSpecMapper} are the only place a Room entity meets an engine type.
 * The engine imports nothing from {@code data/} ({@code MatchingPurityTest}), so it can run on the JVM
 * and cannot be handed a row it might change. The name is passed on as the user typed it; the matcher
 * tidies it before comparing.
 */
public final class PantryEntryMapper {

    private PantryEntryMapper() {
        // Static mapper; never instantiated
    }

    /**
     * Maps one pantry row.
     *
     * @param item a stored pantry item
     * @return the same name, quantity and expiry as an engine value; a {@code null} expiry stays
     *     {@code null}, an item that never expires
     * @throws IllegalArgumentException if the item's quantity is not above zero or its name is blank,
     *     which the add form never stores
     */
    @NonNull
    public static PantryEntry toEntry(@NonNull PantryItem item) {
        Objects.requireNonNull(item, "item");
        return new PantryEntry(item.getName(), new Quantity(item.getQuantity(), item.getUnit()),
                item.getExpiryDate());
    }

    /**
     * Maps the whole pantry, keeping its order.
     *
     * <p>A row with a quantity of zero or less is left out instead of failing the whole list. The add
     * form refuses one ({@code Validators}), but the Database Inspector can still write it, and none of
     * an ingredient covers nothing, so leaving it out gives the matcher the same answer it would give
     * if the row were there.
     *
     * @param items the pantry as Room emitted it
     * @return one entry per row with something in it, in the same order; unmodifiable
     */
    @NonNull
    public static List<PantryEntry> toEntries(@NonNull List<PantryItem> items) {
        List<PantryEntry> entries = new ArrayList<>(items.size());
        for (PantryItem item : items) {
            if (item.getQuantity() > 0) {
                entries.add(toEntry(item));
            }
        }
        return Collections.unmodifiableList(entries);
    }
}
