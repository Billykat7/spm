package com.btk.spm.domain.validation;

import com.btk.spm.R;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Every rule that decides whether input may reach the database, in one place (non-negotiable 8,
 * Issue 31).
 *
 * <p>Plain Java: the device's {@link Locale} and "today" are passed in, never read here, so the JVM
 * tests every rule and boundary in milliseconds on fixed values. The screens only show the outcome:
 * a form calls a validator, puts each {@link FieldError} under its field and writes nothing unless the
 * result {@link ValidationResult#isOk() is ok}. This is the only class that builds a {@code FieldError}
 * or names an {@code R.string.error_*} message ({@code ConventionsTest}).
 *
 * <p>{@link #validatePantryItem} checks the fields in screen order and reports every field that is
 * wrong, each with the first rule it breaks, so the form shows all of its errors at once:
 * <ol>
 *   <li><b>Name</b>, trimmed and with runs of spaces made one ({@link #cleanName}): required; at least
 *       one letter, so {@code "..."} or {@code "42"} is not a name; at most {@link #MAX_NAME_LENGTH}
 *       characters.</li>
 *   <li><b>Quantity</b>, trimmed: required; a plain decimal number in the device's own format, read by
 *       {@link NumberFormat} for {@code locale} with grouping off and the whole text consumed, so
 *       {@code "1,5"} is one and a half under {@code de_DE} and not a number under {@code en_US}, and
 *       {@code "1.5.2"}, {@code "abc"}, {@code "1E3"} or {@code "NaN"} is not a number anywhere; more
 *       than 0; at most two decimal places; at most {@link #MAX_QUANTITY}.</li>
 *   <li><b>Unit</b>: required. The dropdown has no empty choice, but the rule is here so the validator
 *       is complete on its own.</li>
 *   <li><b>Expiry</b>: optional; today is accepted, because an item expires at the end of its day
 *       (Issue 17); a day before {@code today} is not.</li>
 * </ol>
 * {@link #validateThresholdDays} is the Settings seek bar's rule: 1 to 14 days.
 */
public final class Validators {

    /** The longest name a pantry item may have, after cleaning. The form's counter shows the same limit. */
    public static final int MAX_NAME_LENGTH = 60;

    /**
     * The largest quantity a pantry item may hold, in its own unit. Far above any real pantry
     * (100 kg of flour is 100 here), so it only stops a slip of the finger such as {@code 5000000}.
     */
    public static final double MAX_QUANTITY = 100_000;

    /** The most decimal places a quantity may have: {@code 0.25 l}, never {@code 0.999 kg}. */
    public static final int MAX_DECIMALS = 2;

    /** The shortest expiring-soon threshold: tomorrow. The Settings seek bar's minimum. */
    public static final int MIN_THRESHOLD_DAYS = 1;

    /** The longest expiring-soon threshold: two weeks. The Settings seek bar's maximum. */
    public static final int MAX_THRESHOLD_DAYS = 14;

    private static final BigDecimal MAX_QUANTITY_EXACT = BigDecimal.valueOf(MAX_QUANTITY);

    private Validators() {
        // Static rules only; never instantiated
    }

    /**
     * Checks the add and edit ingredient form, field by field, as listed on this class.
     *
     * @param input  the form as the user left it
     * @param today  the date the check is made on; the caller reads the clock, never this class
     * @param locale the device's locale, whose decimal separator the quantity must use
     * @return {@link ValidationResult#ok()}, or one error for every field that is wrong, in field order
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ValidationResult validatePantryItem(PantryItemInput input, LocalDate today, Locale locale) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(locale, "locale");
        List<FieldError> errors = new ArrayList<>();

        String name = cleanName(input.name());
        if (name.isEmpty()) {
            errors.add(new FieldError(Field.NAME, R.string.error_name_required));
        } else if (name.codePoints().noneMatch(Character::isLetter)) {
            errors.add(new FieldError(Field.NAME, R.string.error_name_invalid));
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.add(new FieldError(Field.NAME, R.string.error_name_too_long));
        }

        String quantityText = input.quantityText() == null ? "" : input.quantityText().trim();
        Optional<BigDecimal> quantity = parse(quantityText, locale);
        if (quantityText.isEmpty()) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_required));
        } else if (quantity.isEmpty()) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_invalid));
        } else if (quantity.get().signum() <= 0) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_positive));
        } else if (Math.max(quantity.get().stripTrailingZeros().scale(), 0) > MAX_DECIMALS) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_precision));
        } else if (quantity.get().compareTo(MAX_QUANTITY_EXACT) > 0) {
            errors.add(new FieldError(Field.QUANTITY, R.string.error_quantity_too_large));
        }

        if (input.unit() == null) {
            errors.add(new FieldError(Field.UNIT, R.string.error_unit_required));
        }

        if (input.expiry() != null && input.expiry().isBefore(today)) {
            errors.add(new FieldError(Field.EXPIRY, R.string.error_expiry_past));
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.error(errors.toArray(new FieldError[0]));
    }

    /**
     * Checks an expiring-soon threshold: the Settings seek bar refuses anything else, and
     * {@code AppPreferences} falls back to the default when a stored value fails it.
     *
     * @param days the threshold
     * @return ok from {@link #MIN_THRESHOLD_DAYS} to {@link #MAX_THRESHOLD_DAYS}, both included;
     *     otherwise one error on {@link Field#THRESHOLD_DAYS}
     */
    public static ValidationResult validateThresholdDays(int days) {
        if (days >= MIN_THRESHOLD_DAYS && days <= MAX_THRESHOLD_DAYS) {
            return ValidationResult.ok();
        }
        return ValidationResult.error(new FieldError(Field.THRESHOLD_DAYS, R.string.error_threshold_range));
    }

    /**
     * Returns the name as it is checked and saved: trimmed, with every run of spaces inside it made
     * one, so {@code "  Tomatoes  "} is saved as {@code "Tomatoes"} and {@code "olive   oil"} as
     * {@code "olive oil"}. The matcher normalises it further for comparing (Issue 18); this is only
     * tidying what the user typed.
     *
     * @param name the name as typed, or {@code null}
     * @return the cleaned name; empty for {@code null} or only spaces
     */
    public static String cleanName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    /**
     * Reads a typed quantity exactly as {@link #validatePantryItem} does, so the value saved is the
     * value that was checked: {@code "1.5"} under {@code en_US} and {@code "1,5"} under {@code de_DE}
     * are both 1.5.
     *
     * @param quantityText the quantity as typed, or {@code null}
     * @param locale       the device's locale
     * @return the number, or empty when the text is blank or not a number in that locale
     */
    public static Optional<BigDecimal> parseQuantity(String quantityText, Locale locale) {
        return parse(quantityText == null ? "" : quantityText.trim(), Objects.requireNonNull(locale, "locale"));
    }

    /**
     * Parses trimmed text as a plain decimal in {@code locale}'s format. {@link NumberFormat} stops at
     * the first character it cannot use, so the whole text must have been read: {@code "1,5"} under
     * {@code en_US} stops at the comma and is refused, where {@code Double.parseDouble} would refuse it
     * too but {@code "1.5.2"} would be half read. An exponent ({@code "1E3"}) and the infinity and NaN
     * symbols are refused as well, because a typed quantity never needs them.
     */
    private static Optional<BigDecimal> parse(String trimmed, Locale locale) {
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(false);
        if (format instanceof DecimalFormat decimal) {
            decimal.setParseBigDecimal(true);
            DecimalFormatSymbols symbols = decimal.getDecimalFormatSymbols();
            if (trimmed.contains(symbols.getExponentSeparator())) {
                return Optional.empty();
            }
        }
        ParsePosition position = new ParsePosition(0);
        Number number = format.parse(trimmed, position);
        if (!(number instanceof BigDecimal exact) || position.getIndex() != trimmed.length()) {
            return Optional.empty();
        }
        return Optional.of(exact);
    }
}
