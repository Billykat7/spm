package com.btk.spm.data.mapping;

import androidx.annotation.NonNull;

import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.matching.RecipeSpec;
import com.btk.spm.domain.matching.RequiredIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Turns recipes read from Room into what the matcher takes: each {@link RecipeWithIngredients} becomes
 * a {@link RecipeSpec} with the recipe's id and one {@link RequiredIngredient} per ingredient row.
 *
 * <p>The id travels into the recipe's {@code MatchResult}, which is how a screen finds the recipe a
 * result belongs to again. Like {@link PantryEntryMapper}, this is the boundary between Room and the
 * engine, and the engine never sees the entity on the other side of it.
 */
public final class RecipeSpecMapper {

    private RecipeSpecMapper() {
        // Static mapper; never instantiated
    }

    /**
     * Maps one recipe with its ingredients, keeping the ingredients' order.
     *
     * @param recipe a recipe as Room reads it
     * @return its id and its required ingredients as an engine value
     * @throws IllegalArgumentException if the recipe has no ingredients, or a line has a blank name or
     *     a quantity that is not above zero; the seed's tests (Issue 11) rule all three out
     */
    @NonNull
    public static RecipeSpec toSpec(@NonNull RecipeWithIngredients recipe) {
        Objects.requireNonNull(recipe, "recipe");
        List<RequiredIngredient> lines = new ArrayList<>(recipe.getIngredients().size());
        for (RecipeIngredient ingredient : recipe.getIngredients()) {
            lines.add(new RequiredIngredient(ingredient.getName(),
                    new Quantity(ingredient.getQuantity(), ingredient.getUnit())));
        }
        return new RecipeSpec(recipe.getRecipe().getId(), lines);
    }

    /**
     * Maps every recipe, keeping their order.
     *
     * @param recipes the recipes as Room emitted them
     * @return one spec per recipe, in the same order; unmodifiable
     * @throws IllegalArgumentException as {@link #toSpec(RecipeWithIngredients)} does
     */
    @NonNull
    public static List<RecipeSpec> toSpecs(@NonNull List<RecipeWithIngredients> recipes) {
        List<RecipeSpec> specs = new ArrayList<>(recipes.size());
        for (RecipeWithIngredients recipe : recipes) {
            specs.add(toSpec(recipe));
        }
        return Collections.unmodifiableList(specs);
    }
}
