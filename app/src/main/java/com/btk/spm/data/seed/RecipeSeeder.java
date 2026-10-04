package com.btk.spm.data.seed;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.db.RecipeDao;
import com.btk.spm.data.model.RecipeWithIngredients;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Puts the twenty recipes of {@code assets/recipes.json} into the database the first time the app
 * starts (brief §2.2, "pre-loaded/seeded on first run").
 *
 * <p><b>The rule:</b> the seed writes only when the {@code recipes} table is empty, and writes every
 * recipe in one transaction, so it can run on every start and the table only ever holds 0 or all 20.
 *
 * <p>{@code SpmApplication} runs it on the write executor at every start, not from a
 * {@code RoomDatabase.Callback.onCreate}. That callback hands over a raw database while Room is still
 * building the {@code AppDatabase}, so the DAO is out of reach, and it fires only when the file is
 * first created: a seed interrupted by a crash would leave a database that is never seeded. A count
 * check costs one query once the twenty are there, and a crash halfway through a seed rolls back to
 * an empty table that the next start fills.
 *
 * <p>The seeder lives in {@code data/}, so it uses {@link RecipeDao} directly; the
 * {@code RecipeRepository} the screens see has no writes (decision 7).
 */
public final class RecipeSeeder {

    /** The seed's file name under {@code src/main/assets/}. */
    public static final String ASSET_NAME = "recipes.json";

    /** Where the recipes to seed come from: the asset in the app, a fixed list in a test. */
    @VisibleForTesting
    interface RecipeSource {
        /**
         * Loads every recipe to insert, each with its ingredients.
         *
         * @return the recipes, ids {@code 0}
         * @throws IOException if they cannot be read
         */
        @NonNull
        List<RecipeWithIngredients> load() throws IOException;
    }

    private final AppDatabase database;
    private final RecipeDao dao;
    private final RecipeSource source;

    /**
     * Creates a seeder that reads {@link #ASSET_NAME} from the app's assets.
     *
     * @param context any context of the app; only its assets are used
     * @param database the database to seed
     */
    public RecipeSeeder(@NonNull Context context, @NonNull AppDatabase database) {
        this(database, assetSource(context.getApplicationContext().getAssets()));
    }

    /**
     * Creates a seeder over any source of recipes, so a test can seed a list that fails part way.
     *
     * @param database the database to seed
     * @param source where the recipes come from
     */
    @VisibleForTesting
    RecipeSeeder(@NonNull AppDatabase database, @NonNull RecipeSource source) {
        this.database = database;
        this.dao = database.recipeDao();
        this.source = source;
    }

    /**
     * Seeds the recipes if, and only if, there are none yet. Blocks on the database, so it runs on the
     * write executor, never the main thread.
     *
     * @return {@link SeedResult#SEEDED} if the twenty were inserted now,
     *     {@link SeedResult#ALREADY_SEEDED} if the table already held recipes
     * @throws UncheckedIOException if the asset cannot be read
     * @throws IllegalArgumentException if the asset is malformed; the message names the recipe
     * @throws RuntimeException if an insert fails; the transaction has rolled back and nothing is kept
     */
    @WorkerThread
    @NonNull
    public SeedResult seedIfEmpty() {
        // The cheap check first, so a normal start never reads or parses the asset
        if (dao.count() > 0) {
            return SeedResult.ALREADY_SEEDED;
        }
        List<RecipeWithIngredients> recipes;
        try {
            recipes = source.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read assets/" + ASSET_NAME, e);
        }
        return database.runInTransaction(() -> {
            // Checked again inside the transaction, so two seeds started together insert once
            if (dao.count() > 0) {
                return SeedResult.ALREADY_SEEDED;
            }
            for (RecipeWithIngredients recipe : recipes) {
                dao.insertWithIngredients(recipe.getRecipe(), recipe.getIngredients());
            }
            return SeedResult.SEEDED;
        });
    }

    private static RecipeSource assetSource(AssetManager assets) {
        return () -> RecipeJsonParser.parse(AssetText.read(assets, ASSET_NAME));
    }
}
