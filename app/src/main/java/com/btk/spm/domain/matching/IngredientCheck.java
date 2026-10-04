package com.btk.spm.domain.matching;

/**
 * The matcher's verdict on one line of a recipe: whether the pantry covers it, and how much of it
 * the pantry holds (Issue 25).
 *
 * <p>{@link Shortfall} describes only the lines that are not covered. The recipe detail screen shows
 * every line, with "need 250 g, have 1 kg" on a covered one too, so it needs the amount for those as
 * well, and it must not look the pantry up and compare again (non-negotiable 1). This is that answer,
 * one per required line, decided by {@link StrictMatcher} with the same comparison as the status.
 *
 * @param required  the recipe line, never {@code null}
 * @param available the pantry's canonical total of that ingredient in the line's kind, or {@code null}
 *                  when it holds none of that kind: absent, expired, or only in another kind (decision 5)
 * @param satisfied whether the line is covered: {@code available} is at least what the recipe needs
 */
public record IngredientCheck(RequiredIngredient required, CanonicalQuantity available, boolean satisfied) {

    /**
     * Creates a check.
     *
     * @throws IllegalArgumentException if {@code required} is {@code null}, or the line is marked
     *     covered with nothing available
     */
    public IngredientCheck {
        if (required == null) {
            throw new IllegalArgumentException("A check names the ingredient it is about");
        }
        if (satisfied && available == null) {
            throw new IllegalArgumentException("\"" + required.name() + "\" cannot be covered by nothing");
        }
    }

    /**
     * Returns the same line as a {@link Shortfall}, as {@link MatchResult#shortfalls()} lists it when
     * the line is not covered.
     *
     * @return a shortfall with the same required line and available amount
     */
    public Shortfall asShortfall() {
        return new Shortfall(required, available);
    }
}
