package com.btk.spm.domain.validation;

/**
 * The input fields a validator can report an error on.
 *
 * <p>The validators name a field with this enum rather than a view id, so the rules stay plain Java
 * and are tested on the JVM; the screen maps each {@code Field} to its own input (Issue 14).
 */
public enum Field {

    /** The ingredient's name. */
    NAME,

    /** The amount, as typed. */
    QUANTITY,

    /** The unit chosen from the dropdown. */
    UNIT,

    /** The optional expiry date. */
    EXPIRY
}
