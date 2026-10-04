package com.btk.spm.domain;

/**
 * Which units quantities are shown in: the Settings choice stored under {@code PrefKey.UNITS_SYSTEM}
 * (Issue 28), by {@link #name()}.
 *
 * <p>Display only (decision 5). Matching always compares canonical grams, millilitres and pieces,
 * whatever this says, so switching to imperial can change how {@code 1500 g} reads but never which
 * recipes are suggested.
 */
public enum UnitsSystem {

    /** Grams and kilograms, millilitres and litres; spoons and cups as the recipe wrote them. */
    METRIC,

    /** Ounces and fluid ounces; spoons and cups as the recipe wrote them. */
    IMPERIAL
}
