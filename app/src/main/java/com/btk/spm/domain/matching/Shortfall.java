package com.btk.spm.domain.matching;

/**
 * One reason a recipe cannot be made: a required ingredient that is missing from the pantry, or
 * present but short.
 *
 * <p>{@code available} is what the pantry holds of that ingredient in the same kind as the
 * requirement, so a screen can say "200 g of 250 g". It is {@code null} when the pantry holds none of
 * that kind: the ingredient is absent, expired, or stored only in another kind ({@code 500 g} of
 * flour against {@code 2 cups}, decision 5).
 *
 * @param required  the recipe line that is not covered, never {@code null}
 * @param available the pantry's canonical total of the same kind, or {@code null} for none
 */
public record Shortfall(RequiredIngredient required, CanonicalQuantity available) {

    /**
     * Creates a shortfall.
     *
     * @throws IllegalArgumentException if {@code required} is {@code null}
     */
    public Shortfall {
        if (required == null) {
            throw new IllegalArgumentException("A shortfall names the ingredient it is about");
        }
    }

    /**
     * Says whether the ingredient is missing altogether, rather than present but short.
     *
     * @return {@code true} if the pantry holds none of it in the required kind
     */
    public boolean isMissing() {
        return available == null;
    }
}
