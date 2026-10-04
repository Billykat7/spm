package com.btk.spm.data.seed;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.btk.spm.BuildConfig;
import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.db.RecipeDao;
import com.btk.spm.data.model.RecipeWithIngredients;

import java.io.IOException;
import java.util.function.Consumer;

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
 * <p><b>A damaged file never stops the app</b> (Issue 31). The asset is read with
 * {@link RecipeJsonParser#parseSkippingBroken}: a recipe that breaks a rule is left out and logged by
 * its position, and the rest are seeded. A file that cannot be read, is not JSON, or holds no good
 * recipe seeds nothing and leaves the table empty, so the Recipes tab shows its error state instead
 * of crashing; the next start tries again.
 *
 * <p>The seeder lives in {@code data/}, so it uses {@link RecipeDao} directly; the
 * {@code RecipeRepository} the screens see has no writes (decision 7).
 */
public final class RecipeSeeder {

    /** The seed's file name under {@code src/main/assets/}. */
    public static final String ASSET_NAME = "recipes.json";

    /** The logcat tag of the seed's problems, in debug builds: {@code adb logcat -s Seed}. */
    static final String LOG_TAG = "Seed";

    /** Where the recipes to seed come from: the asset in the app, a fixture or a fixed list in a test. */
    @VisibleForTesting
    interface RecipeSource {
        /**
         * Loads every recipe that can be inserted, each with its ingredients, and why any were left out.
         *
         * @return the good recipes, ids {@code 0}, and one line per recipe left out
         * @throws IOException if the file cannot be read
         */
        @NonNull
        RecipeJsonParser.Report load() throws IOException;
    }

    private final AppDatabase database;
    private final RecipeDao dao;
    private final RecipeSource source;
    private final Consumer<String> log;

    /**
     * Creates a seeder that reads {@link #ASSET_NAME} from the app's assets.
     *
     * @param context any context of the app; only its assets are used
     * @param database the database to seed
     */
    public RecipeSeeder(@NonNull Context context, @NonNull AppDatabase database) {
        this(database, assetSource(context.getApplicationContext().getAssets(), ASSET_NAME));
    }

    /**
     * Creates a seeder that reads another file, such as a broken fixture in the test APK's assets.
     *
     * @param database the database to seed
     * @param assets   the assets to read from
     * @param name     the file's name in them
     * @return the seeder
     */
    @VisibleForTesting
    @NonNull
    static RecipeSeeder fromAsset(@NonNull AppDatabase database, @NonNull AssetManager assets, @NonNull String name) {
        return new RecipeSeeder(database, assetSource(assets, name));
    }

    /**
     * Creates a seeder over any source of recipes, so a test can seed a list that fails part way.
     *
     * @param database the database to seed
     * @param source where the recipes come from
     */
    @VisibleForTesting
    RecipeSeeder(@NonNull AppDatabase database, @NonNull RecipeSource source) {
        this(database, source, RecipeSeeder::logInDebug);
    }

    /**
     * Creates a seeder that reports each problem to {@code log}, so a test can read what was left out.
     *
     * @param database the database to seed
     * @param source   where the recipes come from
     * @param log      receives one line per recipe left out, and one when nothing could be read
     */
    @VisibleForTesting
    RecipeSeeder(@NonNull AppDatabase database, @NonNull RecipeSource source, @NonNull Consumer<String> log) {
        this.database = database;
        this.dao = database.recipeDao();
        this.source = source;
        this.log = log;
    }

    /**
     * Seeds the recipes if, and only if, there are none yet. Blocks on the database, so it runs on the
     * write executor, never the main thread.
     *
     * @return {@link SeedResult#SEEDED} if every recipe was inserted now,
     *     {@link SeedResult#SEEDED_SKIPPING_SOME} if the good ones were and the broken ones logged,
     *     {@link SeedResult#NOTHING_TO_SEED} if nothing could be read and the table stays empty,
     *     {@link SeedResult#ALREADY_SEEDED} if the table already held recipes
     * @throws RuntimeException if an insert fails; the transaction has rolled back and nothing is
     *     kept, and {@code SpmApplication} logs it rather than crash
     */
    @WorkerThread
    @NonNull
    public SeedResult seedIfEmpty() {
        // The cheap check first, so a normal start never reads or parses the asset
        if (dao.count() > 0) {
            return SeedResult.ALREADY_SEEDED;
        }
        RecipeJsonParser.Report report;
        try {
            report = source.load();
        } catch (IOException unreadable) {
            log.accept("cannot read the recipes: " + unreadable.getMessage());
            return SeedResult.NOTHING_TO_SEED;
        }
        report.skipped().forEach(problem -> log.accept("skipped " + problem));
        if (report.recipes().isEmpty()) {
            log.accept("no recipe to seed; the Recipes tab shows its error state");
            return SeedResult.NOTHING_TO_SEED;
        }
        return database.runInTransaction(() -> {
            // Checked again inside the transaction, so two seeds started together insert once
            if (dao.count() > 0) {
                return SeedResult.ALREADY_SEEDED;
            }
            for (RecipeWithIngredients recipe : report.recipes()) {
                dao.insertWithIngredients(recipe.getRecipe(), recipe.getIngredients());
            }
            return report.skipped().isEmpty() ? SeedResult.SEEDED : SeedResult.SEEDED_SKIPPING_SOME;
        });
    }

    private static RecipeSource assetSource(@NonNull AssetManager assets, @NonNull String name) {
        return () -> RecipeJsonParser.parseSkippingBroken(AssetText.read(assets, name));
    }

    /** Logs a seed problem in debug builds; a recipe file holds no user data. */
    private static void logInDebug(@NonNull String problem) {
        if (BuildConfig.DEBUG) {
            Log.w(LOG_TAG, problem);
        }
    }
}
