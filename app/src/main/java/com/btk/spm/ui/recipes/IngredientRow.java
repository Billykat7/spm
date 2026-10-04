package com.btk.spm.ui.recipes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.domain.DisplayQuantity;

import java.util.Objects;

/**
 * One ingredient line of the recipe detail screen, ready to show: its name, what the recipe needs,
 * what the pantry has, and whether that is enough.
 *
 * <p>Built by {@code RecipeDetailViewModel} from one {@code IngredientCheck} of the matcher's result,
 * so {@link #have()} is the matcher's answer, not one worked out here. The amounts are display values
 * (Issue 19's {@code DisplayQuantity}): the pantry's {@code 1000 g} is {@code 1 kg} by the time it
 * reaches the adapter, which only formats it.
 *
 * @param ingredientId the {@code recipe_ingredients} row's id, which {@code DiffUtil} keys the row on
 * @param name         the ingredient as the recipe names it
 * @param required     how much the recipe needs, as it writes it
 * @param available    how much of it the pantry holds in the same kind, or {@code null} for none
 * @param have         whether the pantry covers this line, as the matcher decided
 */
public record IngredientRow(long ingredientId, @NonNull String name, @NonNull DisplayQuantity required,
                            @Nullable DisplayQuantity available, boolean have) {

    /**
     * Creates a row.
     *
     * @throws NullPointerException if {@code name} or {@code required} is {@code null}
     */
    public IngredientRow {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(required, "required");
    }
}
