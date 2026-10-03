package com.btk.spm.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code recipes} together with their {@code recipe_ingredients}, and writes them for the
 * first-run seed.
 *
 * <p>Every read returns {@link RecipeWithIngredients}: Room's {@code @Relation} runs one
 * {@code SELECT} for the recipes and a second for their ingredients, so each of those queries is a
 * {@code @Transaction} and the two see the same state. One observed list feeds both the matcher and
 * the detail screen, with no second trip to the database.
 *
 * <p>Recipes are read-only seed data (decision 7). The writes here exist for the seeder (Issue 11),
 * which lives inside {@code data/}, and for the tests; {@code RecipeRepository}, the only way in from
 * outside {@code data/}, exposes none of them ({@code DaoBoundaryTest} keeps it so).
 */
@Dao
public interface RecipeDao {

    /**
     * Inserts one recipe row. Use {@link #insertWithIngredients(Recipe, List)}, which also stores
     * what the recipe needs; this exists for that method and for tests.
     *
     * @param recipe a recipe whose id is {@code 0}
     * @return the new row's id
     */
    @Insert
    long insertRecipe(@NonNull Recipe recipe);

    /**
     * Inserts ingredient rows as given, each pointing at the recipe in its {@code recipe_id}.
     *
     * @param ingredients the rows to insert
     * @throws android.database.sqlite.SQLiteConstraintException if a {@code recipe_id} names no
     *     recipe (the foreign key) or a name is {@code null}
     */
    @Insert
    void insertIngredients(@NonNull List<RecipeIngredient> ingredients);

    /**
     * Inserts a recipe and everything it needs, all or nothing: the recipe row, then each ingredient
     * stamped with the recipe's new id. If any ingredient fails, the whole transaction rolls back and
     * no recipe row is left without its ingredients. The seeder inserts each recipe through this.
     *
     * @param recipe the recipe, id {@code 0}
     * @param ingredients what it needs, in the order they should be listed; their {@code recipe_id}
     *     is ignored and replaced
     * @return the recipe's new id
     * @throws android.database.sqlite.SQLiteConstraintException if an ingredient breaks a constraint;
     *     nothing is stored
     */
    @Transaction
    default long insertWithIngredients(@NonNull Recipe recipe, @NonNull List<RecipeIngredient> ingredients) {
        long recipeId = insertRecipe(recipe);
        List<RecipeIngredient> owned = new ArrayList<>(ingredients.size());
        for (RecipeIngredient ingredient : ingredients) {
            owned.add(ingredient.withRecipeId(recipeId));
        }
        insertIngredients(owned);
        return recipeId;
    }

    /**
     * Every recipe with its ingredients, ordered by name without regard to case (then by id, so two
     * recipes never swap places between emissions). The {@code LiveData} emits again when either
     * table changes. The matcher reads this (Issue 23).
     *
     * @return every recipe; an empty list before the seed has run
     */
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC, id ASC")
    @NonNull
    LiveData<List<RecipeWithIngredients>> observeAllWithIngredients();

    /**
     * One recipe with its ingredients and steps, for the detail screen (Issue 25).
     *
     * @param id the recipe's id
     * @return a {@code LiveData} that delivers the recipe, or {@code null} if there is no such row;
     *     an unknown id is not an error
     */
    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    @NonNull
    LiveData<RecipeWithIngredients> observeById(long id);

    /**
     * Every recipe with its ingredients as they are now, in the order of
     * {@link #observeAllWithIngredients()}, for the persistence tests (Issue 12).
     *
     * @return every recipe; empty, never {@code null}
     */
    @WorkerThread
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC, id ASC")
    @NonNull
    List<RecipeWithIngredients> getAllWithIngredientsSync();

    /**
     * Counts the recipes. The seeder seeds only when this is {@code 0} (Issue 11).
     *
     * @return how many recipes are stored
     */
    @WorkerThread
    @Query("SELECT COUNT(*) FROM recipes")
    int count();

    /**
     * Counts the ingredient rows of every recipe together, so a test can check the seed stored all of
     * them and the cascade removed all of them.
     *
     * @return how many {@code recipe_ingredients} rows exist
     */
    @WorkerThread
    @Query("SELECT COUNT(*) FROM recipe_ingredients")
    int countIngredients();

    /**
     * Deletes every recipe, and through {@code ON DELETE CASCADE} every ingredient row, for tests that
     * need an empty collection.
     */
    @WorkerThread
    @Query("DELETE FROM recipes")
    void deleteAll();
}
