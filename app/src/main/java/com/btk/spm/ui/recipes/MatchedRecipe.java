package com.btk.spm.ui.recipes;

import androidx.annotation.NonNull;

import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.matching.MatchResult;

import java.util.Objects;

/**
 * One row of the Recipes tab: a recipe as Room read it, next to the matcher's verdict on it.
 *
 * <p>The adapter needs both: the recipe for its name and servings, the result for the counts. The
 * result is the engine's, unchanged; nothing on this side of the boundary works out a status again.
 * Immutable, and equal when both halves are equal.
 *
 * @param recipe the recipe with its ingredients
 * @param result the matcher's verdict on that recipe
 */
public record MatchedRecipe(@NonNull RecipeWithIngredients recipe, @NonNull MatchResult result) {

    /**
     * Pairs a recipe with its result.
     *
     * @throws NullPointerException if either is {@code null}
     * @throws IllegalArgumentException if the result is for another recipe
     */
    public MatchedRecipe {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(result, "result");
        if (recipe.getRecipe().getId() != result.recipeId()) {
            throw new IllegalArgumentException("Result for recipe " + result.recipeId()
                    + " paired with recipe " + recipe.getRecipe().getId());
        }
    }

    /**
     * Returns the recipe's id, which is how {@code DiffUtil} knows two rows are the same recipe and how
     * a tap opens it (Issue 25).
     *
     * @return the {@code recipes.id} of the row
     */
    public long recipeId() {
        return result.recipeId();
    }
}
