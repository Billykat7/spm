package com.btk.spm.domain.validation;

import androidx.annotation.StringRes;

/**
 * One reason a form cannot be saved: the field that is wrong and the message that says how to fix it.
 *
 * <p>The message is a string resource id, not text, so the domain decides <em>which</em> message
 * without depending on Android to load it, and the screen shows it with
 * {@code TextInputLayout.setError(getString(messageRes))}.
 *
 * @param field      the field the message belongs under, never {@code null}
 * @param messageRes the {@code R.string.error_*} id of the message, never 0
 */
public record FieldError(Field field, @StringRes int messageRes) {

    /**
     * Creates a field error.
     *
     * @throws IllegalArgumentException if {@code field} is {@code null} or {@code messageRes} is 0,
     *     the value Android uses for "no resource"
     */
    public FieldError {
        if (field == null) {
            throw new IllegalArgumentException("FieldError needs a field");
        }
        if (messageRes == 0) {
            throw new IllegalArgumentException("FieldError for " + field + " needs a message resource");
        }
    }
}
