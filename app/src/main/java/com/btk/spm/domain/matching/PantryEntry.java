package com.btk.spm.domain.matching;

import com.btk.spm.domain.Quantity;

import java.time.LocalDate;

/**
 * One pantry row as the matcher sees it: a name, a quantity and an optional expiry date.
 *
 * <p>A plain value, not the Room entity, so the engine compiles and runs without Room: the
 * repository maps each {@code PantryItem} to one of these (Issue 23). The name is kept as stored;
 * the matcher normalises it.
 *
 * @param name     the ingredient's name as stored, never {@code null} or blank
 * @param quantity how much there is, never {@code null}
 * @param expiry   the date it expires, or {@code null} for an item that never expires
 */
public record PantryEntry(String name, Quantity quantity, LocalDate expiry) {

    /**
     * Creates a pantry entry.
     *
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank, or {@code quantity}
     *     is {@code null}
     */
    public PantryEntry {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Pantry entry name is missing");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Pantry entry \"" + name + "\" has no quantity");
        }
    }
}
