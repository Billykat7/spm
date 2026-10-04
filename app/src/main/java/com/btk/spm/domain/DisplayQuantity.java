package com.btk.spm.domain;

import java.math.BigDecimal;

/**
 * An amount ready to be shown in the unit the user prefers: {@code 1.5 kg}, {@code 52.9 oz}
 * (Issue 19, for Issue 28's units preference).
 *
 * <p>Display only. It may be rounded, so it is never converted back or compared: {@code 52.9 oz} is
 * {@code 1499.7 g}, less than the {@code 1500 g} it was made from. The matcher works on canonical
 * amounts and has no method that takes one of these.
 *
 * @param amount how much, in {@code unit}
 * @param unit   the unit to show it in, never {@code null}
 */
public record DisplayQuantity(double amount, DisplayUnit unit) {

    /**
     * Creates a display quantity.
     *
     * @throws IllegalArgumentException if {@code amount} is not finite or {@code unit} is {@code null}
     */
    public DisplayQuantity {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Display amount must be finite, was " + amount);
        }
        if (unit == null) {
            throw new IllegalArgumentException("Display unit is missing");
        }
    }

    /**
     * Returns the amount and the unit's symbol as a test or a log reads them, such as {@code 1.5 kg}
     * or {@code 52.9 oz}. Screens format the number for the locale and use {@link DisplayUnit#symbolRes()}.
     *
     * @return the amount without trailing zeros, a space and the unit symbol
     */
    @Override
    public String toString() {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() + " " + unit.symbol();
    }
}
