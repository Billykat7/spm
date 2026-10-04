package com.btk.spm.domain.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every rule of {@link Validators} in both directions, as named rows a reader (and the report) can
 * scan (Issue 31). Each row of {@link OneRulePerRow} changes one field of an otherwise valid form,
 * "Tomatoes, 4, pcs, no date" under {@code en_US}, and names the one error it expects, or none.
 * {@code today} and the locale are fixed, never read from the device.
 */
@RunWith(Enclosed.class)
public class ValidatorsTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private static final Locale ENGLISH = Locale.US;
    private static final Locale GERMAN = Locale.GERMANY;
    private static final String NAME = "Tomatoes";
    private static final String QUANTITY = "4";
    private static final Unit UNIT = Unit.PCS;
    private static final String SIXTY = "a".repeat(60);
    private static final String SIXTY_ONE = "a".repeat(61);

    /** One rule per row, each in both directions; the error it must produce, or {@code null} for a valid form. */
    @RunWith(Parameterized.class)
    public static class OneRulePerRow {

        @Parameters(name = "{0}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                    // Name: required, after trimming
                    row("name required: Tomatoes passes", NAME, QUANTITY, UNIT, null, ENGLISH, null),
                    row("name required: empty is refused", "", QUANTITY, UNIT, null, ENGLISH, nameRequired()),
                    row("name required: only spaces is refused", "   ", QUANTITY, UNIT, null, ENGLISH, nameRequired()),
                    row("name required: nothing typed (null) is refused", null, QUANTITY, UNIT, null, ENGLISH, nameRequired()),
                    row("name trimmed: '  Tomatoes  ' passes", "  Tomatoes  ", QUANTITY, UNIT, null, ENGLISH, null),
                    // Name: at least one letter
                    row("name letters: '...' is refused", "...", QUANTITY, UNIT, null, ENGLISH, nameInvalid()),
                    row("name letters: '42' is refused", "42", QUANTITY, UNIT, null, ENGLISH, nameInvalid()),
                    row("name letters: '-' is refused", "-", QUANTITY, UNIT, null, ENGLISH, nameInvalid()),
                    row("name letters: '7up' passes, it has letters", "7up", QUANTITY, UNIT, null, ENGLISH, null),
                    row("name letters: 'crème fraîche' passes, accents are letters", "crème fraîche", QUANTITY, UNIT, null, ENGLISH, null),
                    // Name: at most 60 characters, after cleaning
                    row("name length: 60 characters passes", SIXTY, QUANTITY, UNIT, null, ENGLISH, null),
                    row("name length: 61 characters is refused", SIXTY_ONE, QUANTITY, UNIT, null, ENGLISH,
                            error(Field.NAME, R.string.error_name_too_long)),
                    row("name length: 60 characters with spaces round them passes", "  " + SIXTY + "  ", QUANTITY, UNIT, null, ENGLISH, null),
                    // Quantity: required
                    row("quantity required: empty is refused", NAME, "", UNIT, null, ENGLISH, quantityRequired()),
                    row("quantity required: only spaces is refused", NAME, "  ", UNIT, null, ENGLISH, quantityRequired()),
                    row("quantity required: nothing typed (null) is refused", NAME, null, UNIT, null, ENGLISH, quantityRequired()),
                    // Quantity: a number in the device's own format
                    row("quantity number: 1.5 passes under en_US", NAME, "1.5", UNIT, null, ENGLISH, null),
                    row("quantity number: 1,5 is refused under en_US", NAME, "1,5", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: 1,5 passes under de_DE", NAME, "1,5", UNIT, null, GERMAN, null),
                    row("quantity number: 1.5 is refused under de_DE", NAME, "1.5", UNIT, null, GERMAN, quantityInvalid()),
                    row("quantity number: 1.5.2 is refused", NAME, "1.5.2", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: abc is refused", NAME, "abc", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: 4f is refused", NAME, "4f", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: 1,000.5 is refused, no grouping", NAME, "1,000.5", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: 1E3 is refused, no exponent", NAME, "1E3", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: NaN is refused", NAME, "NaN", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: the infinity sign is refused", NAME, "∞", UNIT, null, ENGLISH, quantityInvalid()),
                    row("quantity number: ' 4 ' passes, spaces round it ignored", NAME, " 4 ", UNIT, null, ENGLISH, null),
                    row("quantity number: .5 passes", NAME, ".5", UNIT, null, ENGLISH, null),
                    // Quantity: more than 0
                    row("quantity positive: 0 is refused", NAME, "0", UNIT, null, ENGLISH, quantityPositive()),
                    row("quantity positive: -1 is refused", NAME, "-1", UNIT, null, ENGLISH, quantityPositive()),
                    row("quantity positive: 0.01 passes", NAME, "0.01", UNIT, null, ENGLISH, null),
                    // Quantity: at most two decimal places
                    row("quantity decimals: 0.25 passes", NAME, "0.25", UNIT, null, ENGLISH, null),
                    row("quantity decimals: 1.50 passes, a trailing zero is not a decimal", NAME, "1.50", UNIT, null, ENGLISH, null),
                    row("quantity decimals: 0.999 is refused", NAME, "0.999", UNIT, null, ENGLISH,
                            error(Field.QUANTITY, R.string.error_quantity_precision)),
                    // Quantity: at most 100000
                    row("quantity largest: 100000 passes", NAME, "100000", UNIT, null, ENGLISH, null),
                    row("quantity largest: 100001 is refused", NAME, "100001", UNIT, null, ENGLISH,
                            error(Field.QUANTITY, R.string.error_quantity_too_large)),
                    // Unit
                    row("unit required: none chosen is refused", NAME, QUANTITY, null, null, ENGLISH,
                            error(Field.UNIT, R.string.error_unit_required)),
                    row("unit required: kg passes", NAME, QUANTITY, Unit.KG, null, ENGLISH, null),
                    // Expiry
                    row("expiry: none passes, the item never expires", NAME, QUANTITY, UNIT, null, ENGLISH, null),
                    row("expiry: today passes", NAME, QUANTITY, UNIT, TODAY, ENGLISH, null),
                    row("expiry: tomorrow passes", NAME, QUANTITY, UNIT, TODAY.plusDays(1), ENGLISH, null),
                    row("expiry: yesterday is refused", NAME, QUANTITY, UNIT, TODAY.minusDays(1), ENGLISH, expiryPast()),
                    row("expiry: a year ago is refused", NAME, QUANTITY, UNIT, TODAY.minusYears(1), ENGLISH, expiryPast()),
            });
        }

        @Parameter(0)
        public String description;
        @Parameter(1)
        public String name;
        @Parameter(2)
        public String quantityText;
        @Parameter(3)
        public Unit unit;
        @Parameter(4)
        public LocalDate expiry;
        @Parameter(5)
        public Locale locale;
        @Parameter(6)
        public FieldError expected;

        @Test
        public void givesTheExpectedErrorOrNone() {
            ValidationResult result = Validators.validatePantryItem(
                    new PantryItemInput(name, quantityText, unit, expiry), TODAY, locale);

            ValidationResult want = expected == null ? ValidationResult.ok() : ValidationResult.error(expected);
            assertEquals(description, want, result);
        }

        private static Object[] row(String description, String name, String quantityText, Unit unit,
                                    LocalDate expiry, Locale locale, FieldError expected) {
            return new Object[]{description, name, quantityText, unit, expiry, locale, expected};
        }
    }

    /** The Settings seek bar's rule, at and beyond both ends. */
    @RunWith(Parameterized.class)
    public static class ThresholdDays {

        @Parameters(name = "threshold: {0} days is {1}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                    {0, "refused"}, {1, "accepted"}, {3, "accepted"}, {14, "accepted"}, {15, "refused"}, {99, "refused"}, {-1, "refused"},
            });
        }

        @Parameter(0)
        public int days;
        @Parameter(1)
        public String verdict;

        @Test
        public void isAcceptedOnlyFromOneToFourteen() {
            ValidationResult want = verdict.equals("accepted") ? ValidationResult.ok()
                    : ValidationResult.error(new FieldError(Field.THRESHOLD_DAYS, R.string.error_threshold_range));
            assertEquals(want, Validators.validateThresholdDays(days));
        }
    }

    /** Several bad fields at once, the name and number the form saves, and the limits the resources repeat. */
    public static class Together {

        @Test
        public void anEmptyNameAZeroQuantityAndYesterday_giveThreeErrors_atOnce_inFieldOrder() {
            ValidationResult result = Validators.validatePantryItem(
                    new PantryItemInput("", "0", UNIT, TODAY.minusDays(1)), TODAY, ENGLISH);

            assertEquals(ValidationResult.error(
                    nameRequired(), quantityPositive(), expiryPast()), result);
        }

        @Test
        public void everyFieldWrong_givesFourErrors_inFieldOrder() {
            ValidationResult result = Validators.validatePantryItem(
                    new PantryItemInput("...", "abc", null, TODAY.minusDays(3)), TODAY, ENGLISH);

            assertEquals(ValidationResult.error(nameInvalid(), quantityInvalid(),
                    error(Field.UNIT, R.string.error_unit_required), expiryPast()), result);
        }

        @Test
        public void cleanName_trimsAndMakesRunsOfSpacesOne() {
            assertEquals("Tomatoes", Validators.cleanName("  Tomatoes  "));
            assertEquals("olive oil", Validators.cleanName(" olive \t  oil "));
            assertEquals("", Validators.cleanName(null));
            assertEquals("", Validators.cleanName("   "));
        }

        @Test
        public void parseQuantity_readsWhatTheValidatorAccepts_inThatLocale() {
            assertEquals(Optional.of(new BigDecimal("1.5")), Validators.parseQuantity("1.5", ENGLISH));
            assertEquals(Optional.of(new BigDecimal("1.5")), Validators.parseQuantity(" 1,5 ", GERMAN));
            assertEquals(Optional.empty(), Validators.parseQuantity("1,5", ENGLISH));
            assertEquals(Optional.empty(), Validators.parseQuantity(null, ENGLISH));
            assertEquals(Optional.empty(), Validators.parseQuantity("1E3", ENGLISH));
        }

        @Test
        public void theNameCounter_showsTheSameLimitAsTheRule() throws IOException {
            String integers = read(Paths.get("src", "main", "res", "values", "integers.xml"));
            assertEquals(String.valueOf(Validators.MAX_NAME_LENGTH),
                    valueOf(integers, "integer", "pantry_name_max_length"));
        }

        @Test
        public void theErrorMessages_stateTheSameLimitsAsTheRules() throws IOException {
            String strings = read(Paths.get("src", "main", "res", "values", "strings.xml"));
            assertTrue(valueOf(strings, "string", "error_name_too_long")
                    .contains(String.valueOf(Validators.MAX_NAME_LENGTH)));
            assertTrue(valueOf(strings, "string", "error_quantity_too_large")
                    .contains(String.valueOf((long) Validators.MAX_QUANTITY)));
            assertTrue(valueOf(strings, "string", "error_quantity_precision")
                    .contains(String.valueOf(Validators.MAX_DECIMALS)));
            String range = valueOf(strings, "string", "error_threshold_range");
            assertTrue(range.contains(String.valueOf(Validators.MIN_THRESHOLD_DAYS)));
            assertTrue(range.contains(String.valueOf(Validators.MAX_THRESHOLD_DAYS)));
        }

        private static String read(Path path) throws IOException {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }

        /** Returns the text of {@code <type name="name">…</type>} in a values file. */
        private static String valueOf(String xml, String type, String name) {
            Matcher m = Pattern.compile("<" + type + " name=\"" + name + "\">([^<]*)</" + type + ">").matcher(xml);
            assertTrue(type + " " + name + " not found", m.find());
            return m.group(1);
        }
    }

    private static FieldError error(Field field, int messageRes) {
        return new FieldError(field, messageRes);
    }

    private static FieldError nameRequired() {
        return error(Field.NAME, R.string.error_name_required);
    }

    private static FieldError nameInvalid() {
        return error(Field.NAME, R.string.error_name_invalid);
    }

    private static FieldError quantityRequired() {
        return error(Field.QUANTITY, R.string.error_quantity_required);
    }

    private static FieldError quantityInvalid() {
        return error(Field.QUANTITY, R.string.error_quantity_invalid);
    }

    private static FieldError quantityPositive() {
        return error(Field.QUANTITY, R.string.error_quantity_positive);
    }

    private static FieldError expiryPast() {
        return error(Field.EXPIRY, R.string.error_expiry_past);
    }
}
