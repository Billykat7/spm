package com.btk.spm.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.db.RecipeDao;
import com.btk.spm.data.model.RecipeWithIngredients;

import java.util.List;

/**
 * The only way the rest of the app reads recipes, each with its ingredients (non-negotiable 6).
 *
 * <p><b>Read-only, on purpose.</b> Recipes are seed data (decision 7): the app ships twenty and the
 * user cannot add, edit or remove one. So this class has no method that writes, and a screen has
 * nothing to misuse. The one writer is the first-run seeder (Issue 11), which lives inside
 * {@code data/} and uses {@link RecipeDao} directly; {@code DaoBoundaryTest} stops anything outside
 * {@code data/} from doing the same. For the same reason the repository takes no write executor,
 * unlike {@code PantryRepository}: it would never use one.
 *
 * <p>Reads are the DAO's {@code LiveData}, so the suggested list re-runs the matcher when the
 * recipes change, and the detail screen shows a recipe with no second query.
 */
public class RecipeRepository {

    private final RecipeDao dao;

    /**
     * Creates the repository over {@code database}'s recipe tables.
     *
     * @param database the app's database
     */
    public RecipeRepository(@NonNull AppDatabase database) {
        this.dao = database.recipeDao();
    }

    /**
     * Returns every recipe with its ingredients, ordered by name without regard to case, updating when
     * the recipes change. The matcher reads this (Issue 23).
     *
     * @return the observed list; empty before the seed has run
     */
    @NonNull
    public LiveData<List<RecipeWithIngredients>> observeAllWithIngredients() {
        return dao.observeAllWithIngredients();
    }

    /**
     * Returns one recipe with its ingredients and steps, for the detail screen (Issue 25).
     *
     * @param id the recipe's id
     * @return a {@code LiveData} that delivers the recipe, or {@code null} for an unknown id
     */
    @NonNull
    public LiveData<RecipeWithIngredients> observeById(long id) {
        return dao.observeById(id);
    }

    /**
     * Counts the recipes as they are now. Blocks, so only call it from a background thread.
     *
     * @return how many recipes are stored
     */
    @WorkerThread
    public int count() {
        return dao.count();
    }
}
