package com.btk.spm.domain.validation;

import com.btk.spm.domain.Unit;

import java.time.LocalDate;

/**
 * What the add and edit form holds when Save is tapped, exactly as the user left it: the two texts as
 * typed, and the unit and the date as chosen (Issue 31).
 *
 * <p>Nothing is cleaned or parsed on the way in, so {@link Validators#validatePantryItem} sees what
 * the user sees, spaces and all, and decides on it.
 *
 * @param name         the name field's text, or {@code null} for nothing typed
 * @param quantityText the quantity field's text, or {@code null} for nothing typed
 * @param unit         the chosen unit, or {@code null} when none is chosen
 * @param expiry       the chosen expiry date, or {@code null} for an item that never expires
 */
public record PantryItemInput(String name, String quantityText, Unit unit, LocalDate expiry) {
}
