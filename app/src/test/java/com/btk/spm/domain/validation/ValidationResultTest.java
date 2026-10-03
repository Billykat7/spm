package com.btk.spm.domain.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Checks the rules a {@link ValidationResult} and a {@link FieldError} enforce on themselves. */
public class ValidationResultTest {

    // Any non-zero id stands in for an R.string.error_* resource; the domain never reads the text
    private static final int MESSAGE = 0x7f120001;
    private static final int OTHER_MESSAGE = 0x7f120002;

    @Test
    public void okHasNoErrorsAndIsAlwaysTheSameResult() {
        assertTrue(ValidationResult.ok().isOk());
        assertTrue(ValidationResult.ok().errors().isEmpty());
        assertSame(ValidationResult.ok(), ValidationResult.ok());
    }

    @Test
    public void errorKeepsEveryErrorInFieldOrder() {
        FieldError name = new FieldError(Field.NAME, MESSAGE);
        FieldError quantity = new FieldError(Field.QUANTITY, OTHER_MESSAGE);
        ValidationResult result = ValidationResult.error(name, quantity);

        assertFalse(result.isOk());
        assertEquals(List.of(name, quantity), result.errors());
    }

    @Test
    public void errorForFindsAFieldsErrorOrNothing() {
        FieldError unit = new FieldError(Field.UNIT, MESSAGE);
        ValidationResult result = ValidationResult.error(unit);

        assertEquals(unit, result.errorFor(Field.UNIT).orElseThrow());
        assertTrue(result.errorFor(Field.EXPIRY).isEmpty());
        assertTrue(ValidationResult.ok().errorFor(Field.NAME).isEmpty());
    }

    @Test
    public void errorsCannotBeChangedAfterTheFact() {
        ValidationResult result = ValidationResult.error(new FieldError(Field.NAME, MESSAGE));
        assertThrows(UnsupportedOperationException.class,
                () -> result.errors().add(new FieldError(Field.EXPIRY, MESSAGE)));
    }

    @Test
    public void errorRefusesNoErrorsANullErrorAndTwoErrorsForOneField() {
        assertThrows(IllegalArgumentException.class, ValidationResult::error);
        assertThrows(IllegalArgumentException.class, () -> ValidationResult.error((FieldError) null));
        assertThrows(IllegalArgumentException.class, () -> ValidationResult.error(
                new FieldError(Field.NAME, MESSAGE), new FieldError(Field.NAME, OTHER_MESSAGE)));
    }

    @Test
    public void fieldErrorNeedsAFieldAndAMessage() {
        assertThrows(IllegalArgumentException.class, () -> new FieldError(null, MESSAGE));
        assertThrows(IllegalArgumentException.class, () -> new FieldError(Field.NAME, 0));
    }

    @Test
    public void resultsWithTheSameErrorsAreEqual() {
        ValidationResult one = ValidationResult.error(new FieldError(Field.NAME, MESSAGE));
        ValidationResult two = ValidationResult.error(new FieldError(Field.NAME, MESSAGE));
        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertFalse(one.equals(ValidationResult.ok()));
    }
}
