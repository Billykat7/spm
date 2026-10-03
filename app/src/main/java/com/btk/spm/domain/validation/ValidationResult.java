package com.btk.spm.domain.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The outcome of validating a form: either valid, or the list of field errors to show.
 *
 * <p>A form writes nothing until its result {@link #isOk()} (non-negotiable 8). When it is not, the
 * screen shows <em>every</em> error at once, each under its own field, and focuses the first; so the
 * errors keep the order the validator found them in, which is the order of the fields on screen, and
 * a field has at most one error.
 *
 * <p>Immutable; two results are equal when they hold the same errors in the same order.
 */
public final class ValidationResult {

    private static final ValidationResult OK = new ValidationResult(Collections.emptyList());

    private final List<FieldError> errors;

    private ValidationResult(List<FieldError> errors) {
        this.errors = errors;
    }

    /**
     * Returns the result of a form with nothing wrong.
     *
     * @return the valid result, with no errors
     */
    public static ValidationResult ok() {
        return OK;
    }

    /**
     * Returns the result of a form that must not be saved.
     *
     * @param errors one error per failing field, in the order the fields appear on screen
     * @return an invalid result holding those errors
     * @throws IllegalArgumentException if no error is given, an error is {@code null}, or two errors
     *     name the same field
     */
    public static ValidationResult error(FieldError... errors) {
        if (errors == null || errors.length == 0) {
            throw new IllegalArgumentException("An error result needs at least one FieldError; use ok() for a valid form");
        }
        Set<Field> seen = EnumSet.noneOf(Field.class);
        for (FieldError error : errors) {
            if (error == null) {
                throw new IllegalArgumentException("FieldError must not be null");
            }
            if (!seen.add(error.field())) {
                throw new IllegalArgumentException("Two errors for " + error.field() + "; report one per field");
            }
        }
        return new ValidationResult(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(errors))));
    }

    /**
     * Tells whether the form may be saved.
     *
     * @return {@code true} when there is no error
     */
    public boolean isOk() {
        return errors.isEmpty();
    }

    /**
     * Returns every error, in field order; empty when the form is valid.
     *
     * @return an unmodifiable list, never {@code null}
     */
    public List<FieldError> errors() {
        return errors;
    }

    /**
     * Returns the error to show under {@code field}, if it has one.
     *
     * @param field the field the screen is about to update
     * @return the field's error, or empty when that field is valid
     */
    public Optional<FieldError> errorFor(Field field) {
        for (FieldError error : errors) {
            if (error.field() == field) {
                return Optional.of(error);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValidationResult && errors.equals(((ValidationResult) other).errors);
    }

    @Override
    public int hashCode() {
        return errors.hashCode();
    }

    @Override
    public String toString() {
        return isOk() ? "ValidationResult.ok" : "ValidationResult.error" + errors;
    }
}
