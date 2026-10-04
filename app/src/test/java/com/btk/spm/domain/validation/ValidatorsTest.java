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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every rule of {@link Validators#validatePantryItem} and every boundary, as rows a reader (and the
 * report) can scan. Each row changes one field of an otherwise valid form, "Tomatoes, 4, pcs, no
 * date", and names the one error it expects, or none. {@code today} is fixed, never read from the
 * clock.
 */
@RunWith(Enclosed.class)
public class ValidatorsTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private static final String NAME = "Tomatoes";
    private static final String QUANTITY = "4";
    private static final Unit UNIT = Unit.PCS;
    private static final String SIXTY = "a".repeat(60);
    private static final String SIXTY_ONE = "a".repeat(61);

    /** One field changed per row; the error it must produce, or {@code null} for a valid form. */
    @RunWith(Parameterized.class)
    public static class OneFieldAtATime {

        @Parameters(name = "{0}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                    // Name
                    row("name: a 60-character name passes", SIXTY, QUANTITY, UNIT, null, null),
                    row("name: 60 characters with spaces round them passes, trimmed", "  " + SIXTY + "  ", QUANTITY, UNIT, null, null),
                    row("name: 61 characters is too long", SIXTY_ONE, QUANTITY, UNIT, null,
                            error(Field.NAME, R.string.error_name_too_long)),
                    row("name: empty is required", "", QUANTITY, UNIT, null, error(Field.NAME, R.string.error_name_required)),
                    row("name: only spaces is required", "   ", QUANTITY, UNIT, null, error(Field.NAME, R.string.error_name_required)),
                    row("name: nothing typed (null) is required", null, QUANTITY, UNIT, null,
                            error(Field.NAME, R.string.error_name_required)),
                    // Quantity: present
                    row("quantity: empty is required", NAME, "", UNIT, null, error(Field.QUANTITY, R.string.error_quantity_required)),
                    row("quantity: only spaces is required", NAME, "  ", UNIT, null,
                            error(Field.QUANTITY, R.string.error_quantity_required)),
                    row("quantity: nothing typed (null) is required", NAME, null, UNIT, null,
                            error(Field.QUANTITY, R.string.error_quantity_required)),
                    // Quantity: a number
                    row("quantity: abc is not a number", NAME, "abc", UNIT, null, notANumber()),
                    row("quantity: 1.2.3 is not a number", NAME, "1.2.3", UNIT, null, notANumber()),
                    row("quantity: 1,000.5 is not a number", NAME, "1,000.5", UNIT, null, notANumber()),
                    row("quantity: NaN is not a number", NAME, "NaN", UNIT, null, notANumber()),
                    row("quantity: Infinity is not a number", NAME, "Infinity", UNIT, null, notANumber()),
                    row("quantity: 1e3 is not a number", NAME, "1e3", UNIT, null, notANumber()),
                    row("quantity: 4f is not a number", NAME, "4f", UNIT, null, notANumber()),
                    row("quantity: 4 passes", NAME, "4", UNIT, null, null),
                    row("quantity: 1.5 passes", NAME, "1.5", UNIT, null, null),
                    row("quantity: 1,5 passes, a comma is a decimal separator", NAME, "1,5", UNIT, null, null),
                    row("quantity: .5 passes", NAME, ".5", UNIT, null, null),
                    row("quantity: 4. passes", NAME, "4.", UNIT, null, null),
                    row("quantity: spaces round it are ignored", NAME, " 4 ", UNIT, null, null),
                    // Quantity: more than 0
                    row("quantity: 0 is not more than 0", NAME, "0", UNIT, null, notPositive()),
                    row("quantity: 0,0 is not more than 0", NAME, "0,0", UNIT, null, notPositive()),
                    row("quantity: -2 is not more than 0", NAME, "-2", UNIT, null, notPositive()),
                    row("quantity: 0.01 passes", NAME, "0.01", UNIT, null, null),
                    // Quantity: at most 100000
                    row("quantity: 100000 passes", NAME, "100000", UNIT, null, null),
                    row("quantity: 100000.00 passes", NAME, "100000.00", UNIT, null, null),
                    row("quantity: 100000.01 is too large", NAME, "100000.01", UNIT, null,
                            error(Field.QUANTITY, R.string.error_quantity_too_large)),
                    row("quantity: 100001 is too large", NAME, "100001", UNIT, null,
                            error(Field.QUANTITY, R.string.error_quantity_too_large)),
                    // Unit
                    row("unit: none chosen is required", NAME, QUANTITY, null, null, error(Field.UNIT, R.string.error_unit_required)),
                    row("unit: every unit passes (kg here)", NAME, QUANTITY, Unit.KG, null, null),
                    // Expiry
                    row("expiry: none passes, the item never expires", NAME, QUANTITY, UNIT, null, null),
                    row("expiry: today passes", NAME, QUANTITY, UNIT, TODAY, null),
                    row("expiry: tomorrow passes", NAME, QUANTITY, UNIT, TODAY.plusDays(1), null),
                    row("expiry: yesterday is in the past", NAME, QUANTITY, UNIT, TODAY.minusDays(1),
                            error(Field.EXPIRY, R.string.error_expiry_in_past)),
                    row("expiry: a year ago is in the past", NAME, QUANTITY, UNIT, TODAY.minusYears(1),
                            error(Field.EXPIRY, R.string.error_expiry_in_past)),
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
        public FieldError expected;

        @Test
        public void givesTheExpectedErrorOrNone() {
            ValidationResult result = Validators.validatePantryItem(name, quantityText, unit, expiry, TODAY);

            ValidationResult want = expected == null ? ValidationResult.ok() : ValidationResult.error(expected);
            assertEquals(description, want, result);
        }

        private static Object[] row(String description, String name, String quantityText, Unit unit,
                                    LocalDate expiry, FieldError expected) {
            return new Object[]{description, name, quantityText, unit, expiry, expected};
        }

        private static FieldError error(Field field, int messageRes) {
            return new FieldError(field, messageRes);
        }

        private static FieldError notANumber() {
            return error(Field.QUANTITY, R.string.error_quantity_not_a_number);
        }

        private static FieldError notPositive() {
            return error(Field.QUANTITY, R.string.error_quantity_not_positive);
        }
    }

    /** Several bad fields at once, the parser the form saves with, and the limits the resources repeat. */
    public static class Together {

        @Test
        public void twoBadFields_giveTwoErrors_inFieldOrder() {
            ValidationResult result = Validators.validatePantryItem(
                    "  ", QUANTITY, UNIT, TODAY.minusDays(1), TODAY);

            assertEquals(ValidationResult.error(
                    new FieldError(Field.NAME, R.string.error_name_required),
                    new FieldError(Field.EXPIRY, R.string.error_expiry_in_past)), result);
        }

        @Test
        public void theEmptyForm_givesNameQuantityAndUnitErrors_inFieldOrder() {
            ValidationResult result = Validators.validatePantryItem("", "", null, null, TODAY);

            assertEquals(ValidationResult.error(
                    new FieldError(Field.NAME, R.string.error_name_required),
                    new FieldError(Field.QUANTITY, R.string.error_quantity_required),
                    new FieldError(Field.UNIT, R.string.error_unit_required)), result);
        }

        @Test
        public void everyFieldWrong_givesFourErrors_inFieldOrder() {
            ValidationResult result = Validators.validatePantryItem(
                    SIXTY_ONE, "-2", null, TODAY.minusDays(3), TODAY);

            assertEquals(ValidationResult.error(
                    new FieldError(Field.NAME, R.string.error_name_too_long),
                    new FieldError(Field.QUANTITY, R.string.error_quantity_not_positive),
                    new FieldError(Field.UNIT, R.string.error_unit_required),
                    new FieldError(Field.EXPIRY, R.string.error_expiry_in_past)), result);
        }

        @Test
        public void parseQuantity_readsWhatTheValidatorAccepts() {
            assertEquals(OptionalDouble.of(1.5), Validators.parseQuantity("1,5"));
            assertEquals(OptionalDouble.of(1.5), Validators.parseQuantity(" 1.5 "));
            assertEquals(OptionalDouble.of(100000), Validators.parseQuantity("100000"));
            assertEquals(OptionalDouble.of(0.5), Validators.parseQuantity(".5"));
        }

        @Test
        public void parseQuantity_isEmptyForWhatTheValidatorCallsNotANumber() {
            assertTrue(Validators.parseQuantity(null).isEmpty());
            assertTrue(Validators.parseQuantity("").isEmpty());
            assertTrue(Validators.parseQuantity("abc").isEmpty());
            assertTrue(Validators.parseQuantity("NaN").isEmpty());
            assertTrue(Validators.parseQuantity("1e3").isEmpty());
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
}
