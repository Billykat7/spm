package com.btk.spm.data.model;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A recipe read together with every ingredient it needs: what the matcher takes as a recipe and what
 * the detail screen shows. Not a table; Room fills it from {@code recipes} and
 * {@code recipe_ingredients} with two queries, which is why the DAO methods that return it run in a
 * transaction (Issue 10).
 *
 * <p>Immutable, like the entities: Room passes both parts to the constructor. An instance is a
 * snapshot of two tables at one moment; the {@code LiveData} queries that return it emit a new one
 * when either table changes.
 */
public class RecipeWithIngredients {

    /** The {@code recipes} row, its columns read in place ({@code @Embedded}). */
    @Embedded
    @NonNull
    private final Recipe recipe;

    /**
     * Every {@code recipe_ingredients} row whose {@code recipe_id} is {@link #recipe}'s id. Empty, never
     * {@code null}, for a recipe with no ingredients.
     */
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    @NonNull
    private final List<RecipeIngredient> ingredients;

    /**
     * Creates the pair, as Room does after reading both tables.
     *
     * @param recipe the recipe row
     * @param ingredients the rows that point at it; copied
     */
    public RecipeWithIngredients(@NonNull Recipe recipe, @NonNull List<RecipeIngredient> ingredients) {
        this.recipe = recipe;
        this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
    }

    /**
     * Returns the recipe row.
     *
     * @return the recipe
     */
    @NonNull
    public Recipe getRecipe() {
        return recipe;
    }

    /**
     * Returns the ingredients the recipe needs.
     *
     * @return an unmodifiable list, empty for a recipe with none
     */
    @NonNull
    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    /** Equal when the recipe and its ingredients, in order, are equal; a test compares a read-back with what it inserted. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecipeWithIngredients)) {
            return false;
        }
        RecipeWithIngredients other = (RecipeWithIngredients) o;
        return Objects.equals(recipe, other.recipe) && Objects.equals(ingredients, other.ingredients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipe, ingredients);
    }

    /** Returns a one-line form for test failures and debug logs. */
    @NonNull
    @Override
    public String toString() {
        return "RecipeWithIngredients{" + recipe + ", " + ingredients + "}";
    }
}
