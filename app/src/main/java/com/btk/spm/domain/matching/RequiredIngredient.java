package com.btk.spm.domain.matching;

import com.btk.spm.domain.Quantity;

/**
 * One line of a recipe as the matcher sees it: an ingredient and how much of it the recipe needs.
 *
 * <p>A plain value, not the Room entity, so the engine compiles without Room. The name is kept as
 * stored; the matcher normalises it.
 *
 * @param name     the ingredient's name as stored, never {@code null} or blank
 * @param quantity how much the recipe needs, never {@code null}
 */
public record RequiredIngredient(String name, Quantity quantity) {

    /**
     * Creates a required ingredient.
     *
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank, or {@code quantity}
     *     is {@code null}
     */
    public RequiredIngredient {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Required ingredient name is missing");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Required ingredient \"" + name + "\" has no quantity");
        }
    }

    /**
     * Returns the line as a log reads it, such as {@code 250 g flour}.
     *
     * @return the quantity and the name
     */
    @Override
    public String toString() {
        return quantity + " " + name;
    }
}
