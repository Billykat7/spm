package com.btk.spm.domain.validation;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * The rules a form's input must pass before anything is written (non-negotiable 8).
 *
 * <p>They live here, in plain Java, so the JVM tests every rule and boundary in milliseconds and the
 * screen only shows the outcome: it calls a validator, puts each {@link FieldError} under its field
 * and writes nothing unless the result {@link ValidationResult#isOk() is ok}. Nothing here reads the
 * clock: "today" is passed in, so a test can fix it and the answer never depends on when it runs.
 */
public final class Validators {

    /** The longest name a pantry item may have, after trimming. The form's counter shows the same limit. */
    public static final int MAX_NAME_LENGTH = 60;

    /**
     * The largest quantity a pantry item may hold, in its own unit. Far above any real pantry
     * (100 kg of flour is 100 here), so it only stops a slip of the finger such as {@code 5000000}.
     */
    public static final double MAX_QUANTITY = 100_000;

    /**
     * What a typed quantity may look like: digits with an optional sign and one decimal separator,
     * a full stop or a comma ({@code 4}, {@code 1.5}, {@code 1,5}, {@code .5}, {@code -2}). Anything
     * else is "not a number", including what {@code Double.parseDouble} would wrongly accept, such as
     * {@code NaN}, {@code Infinity}, {@code 1e3} and {@code 4f}.
     */
    private static final Pattern QUANTITY = Pattern.compile("[+-]?(\\d+([.,]\\d*)?|[.,]\\d+)");

    private static final BigDecimal MAX_QUANTITY_EXACT = BigDecimal.valueOf(MAX_QUANTITY);

    private Validators() {
        // Static rules only; never instantiated
    }

    /**
     * Checks the add and edit ingredient form. Each field gets at most one error, the first rule it
     * breaks, and the errors come back in the order the fields appear on screen:
     * <ol>
     *   <li><b>Name</b>: required, not just spaces; at most {@link #MAX_NAME_LENGTH} characters once
     *       trimmed.</li>
     *   <li><b>Quantity</b>: required; a number, with a full stop or a comma as the decimal separator
     *       ({@code "1,5"} is 1.5); more than 0; at most {@link #MAX_QUANTITY}.</li>
     *   <li><b>Unit</b>: required.</li>
     *   <li><b>Expiry</b>: optional; when given, not before {@code today} (today itself is fine).</li>
     * </ol>
     *
     * @param name         the name as typed, or {@code null} for nothing typed
     * @param quantityText the quantity as typed, or {@code null} for nothing typed
     * @param unit         the chosen unit, or {@code null} when none is chosen
     * @param expiry       the chosen expiry date, or {@code null} for an item that never expires
     * @param today        the date the check is made on; the caller reads the clock, never this class
     * @return {@link ValidationResult#ok()}, or one error per failing field in field order
     */
    public static ValidationResult validatePantryItem(String name, String quantityText, Unit unit,
                                                      LocalDate expiry, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            errors.add(new FieldError(Field.NAME, R.string.error_name_required));
        } else if (trimmedName.length() > MAX_NAME_LENGTH) {
            errors.add(new FieldError(Field.NAME, R.string.error_name_too_long));
        }

        String trimmedQuantity = quantityText == null ? "" : quantityText.trim();
        BigDecimal quantity = parseExact(trimmedQuantity);
        if (trimmedQuantity.isEmpty()) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_required));
        } else if (quantity == null) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_not_a_number));
        } else if (quantity.signum() <= 0) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_not_positive));
        } else if (quantity.compareTo(MAX_QUANTITY_EXACT) > 0) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_too_large));
        }

        if (unit == null) {
            errors.add(new FieldError(Field.UNIT, R.string.error_unit_required));
        }

        if (expiry != null && expiry.isBefore(today)) {
            errors.add(new FieldError(Field.EXPIRY, R.string.error_expiry_in_past));
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.error(errors.toArray(new FieldError[0]));
    }

    /**
     * Reads a typed quantity the way {@link #validatePantryItem} does, so the value saved is exactly
     * the value that was checked: {@code "1,5"} and {@code " 1.5 "} are both 1.5.
     *
     * @param quantityText the quantity as typed, or {@code null}
     * @return the number, or empty when the text is blank or not a number
     */
    public static OptionalDouble parseQuantity(String quantityText) {
        BigDecimal quantity = parseExact(quantityText == null ? "" : quantityText.trim());
        return quantity == null ? OptionalDouble.empty() : OptionalDouble.of(quantity.doubleValue());
    }

    /**
     * Parses trimmed quantity text into an exact decimal, so the boundary checks compare
     * {@code 100000.01} with {@code 100000} without floating-point rounding; {@code null} when the
     * text is not a number.
     */
    private static BigDecimal parseExact(String trimmed) {
        if (!QUANTITY.matcher(trimmed).matches()) {
            return null;
        }
        return new BigDecimal(trimmed.replace(',', '.'));
    }
}
