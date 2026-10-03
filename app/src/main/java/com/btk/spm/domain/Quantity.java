package com.btk.spm.domain;

import java.math.BigDecimal;

/**
 * An amount of something in a unit: {@code 250 g}, {@code 2 tbsp}, {@code 3 pcs}.
 *
 * <p>Immutable, and valid by construction: the amount is a finite number above zero and the unit is
 * never missing, so every class that receives a {@code Quantity} can skip those checks. A pantry
 * item with nothing left is deleted, not stored as zero. Two quantities are equal when both the
 * amount and the unit are equal; {@code 1 kg} and {@code 1000 g} are therefore different values here,
 * because converting between units is the converter's job (Issue 19), not this type's.
 *
 * @param amount how much, finite and greater than zero
 * @param unit   what it is measured in, never {@code null}
 */
public record Quantity(double amount, Unit unit) {

    /**
     * Creates a quantity, refusing one that cannot exist in a pantry or a recipe.
     *
     * @throws IllegalArgumentException if {@code amount} is zero, negative, infinite or NaN, or
     *     {@code unit} is {@code null}
     */
    public Quantity {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            // !(amount > 0) is also true for NaN, which "amount <= 0" would let through
            throw new IllegalArgumentException("Quantity amount must be a finite number above 0, was " + amount);
        }
        if (unit == null) {
            throw new IllegalArgumentException("Quantity unit is missing");
        }
    }

    /**
     * Returns the quantity as a log line reads it: the amount without a trailing {@code .0}, a space
     * and the unit's symbol, such as {@code 250 g} or {@code 1.5 kg}. Not for screens, which format
     * the number for the user's locale and use the unit's string resource.
     *
     * @return the amount and the unit symbol
     */
    @Override
    public String toString() {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() + " " + unit.symbol();
    }
}
