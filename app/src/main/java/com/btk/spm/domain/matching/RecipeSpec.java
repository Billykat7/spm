package com.btk.spm.domain.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A recipe as the matcher sees it: its id and the ingredients it requires.
 *
 * <p>A plain value, not the Room relation, so the engine compiles without Room: the repository maps
 * each {@code RecipeWithIngredients} to one of these (Issue 23). The ingredient list is copied and
 * cannot be changed afterwards.
 *
 * @param id          the recipe's database id, carried into its {@link MatchResult}
 * @param ingredients what the recipe requires, in the recipe's order; at least one
 */
public record RecipeSpec(long id, List<RequiredIngredient> ingredients) {

    /**
     * Creates a recipe spec, keeping an unmodifiable copy of the ingredients.
     *
     * @throws IllegalArgumentException if {@code ingredients} is {@code null}, empty or holds a
     *     {@code null}
     */
    public RecipeSpec {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("Recipe " + id + " requires no ingredients");
        }
        if (ingredients.contains(null)) {
            throw new IllegalArgumentException("Recipe " + id + " has a missing ingredient");
        }
        ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
    }
}
