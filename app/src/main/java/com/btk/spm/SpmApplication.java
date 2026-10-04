package com.btk.spm;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.repo.PantryRepository;
import com.btk.spm.data.repo.RecipeRepository;
import com.btk.spm.data.seed.AliasLoader;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.data.seed.SeedResult;
import com.btk.spm.domain.matching.IngredientNormaliser;
import com.btk.spm.domain.matching.StrictMatcher;
import com.btk.spm.domain.matching.UnitConverter;
import com.btk.spm.notifications.ExpiryAlertScheduler;
import com.btk.spm.notifications.NotificationChannels;
import com.btk.spm.settings.AppPreferences;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The process-wide entry point of the Smart Pantry Manager.
 *
 * <p>Exists so that objects that must be created exactly once per process have a home: the Room
 * database, the single-thread I/O executor and the repositories built on them, and the matcher with
 * the thread it runs on. Screens reach the
 * repositories through this class, never by constructing their own, so every screen observes the same
 * pantry (non-negotiable 6). No other class in the app builds a database, and nothing outside
 * {@code data/} touches a DAO ({@code DaoBoundaryTest}).
 */
public class SpmApplication extends Application {

    /** The name of the write thread, as it appears in a stack trace or the profiler. */
    private static final String IO_THREAD_NAME = "spm-io";

    /** The name of the matching thread, as it appears in a stack trace or the profiler. */
    private static final String MATCH_THREAD_NAME = "spm-match";

    /** The logcat tag of the first-run seed: {@code adb logcat -s Seed}. */
    private static final String SEED_LOG_TAG = "Seed";

    private AppDatabase database;
    private ExecutorService ioExecutor;
    private ExecutorService matchExecutor;
    private PantryRepository pantryRepository;
    private RecipeRepository recipeRepository;

    /**
     * Becomes {@code true} once the first-run seed has finished, so a screen can tell recipes that are
     * still being inserted from recipes that never loaded (Issue 24).
     */
    private final MutableLiveData<Boolean> recipeSeedDone = new MutableLiveData<>(false);

    /** Built on first use, on the matching thread, because it reads the alias table from the APK. */
    @GuardedBy("this")
    private StrictMatcher strictMatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        // Building is cheap: Room opens the file and creates the tables on the first query, which
        // runs off the main thread, so this does no disk work during start-up.
        database = Room.databaseBuilder(this, AppDatabase.class, AppDatabase.DB_NAME).build();
        // One thread, so writes run in the order they were submitted: an insert followed by its undo
        // can never land the other way round. The process owns it, so it is never shut down.
        ioExecutor = Executors.newSingleThreadExecutor(task -> new Thread(task, IO_THREAD_NAME));
        // The matcher gets a thread of its own, so a match never queues behind a write and a write
        // never waits for a match (Issue 23). One thread, so matches run in the order they were asked for.
        matchExecutor = Executors.newSingleThreadExecutor(task -> new Thread(task, MATCH_THREAD_NAME));
        pantryRepository = new PantryRepository(database, ioExecutor);
        recipeRepository = new RecipeRepository(database);
        // The recipe seed runs on every start and inserts only into an empty table (Issue 11). It is
        // the first task on the write thread, so it finishes before any pantry write the user makes.
        // A damaged file seeds what it can and logs the rest; a failure leaves the table empty and the
        // Recipes tab on its error state (Issue 31). Either way the app keeps running: an exception
        // on this thread would end the process.
        AppDatabase seeded = database;
        ioExecutor.execute(() -> {
            try {
                SeedResult result = new RecipeSeeder(this, seeded).seedIfEmpty();
                if (BuildConfig.DEBUG) {
                    Log.d(SEED_LOG_TAG, "Recipe seed: " + result);
                }
            } catch (RuntimeException failed) {
                if (BuildConfig.DEBUG) {
                    Log.e(SEED_LOG_TAG, "Recipe seed failed and was rolled back", failed);
                }
            } finally {
                recipeSeedDone.postValue(true);
            }
        });
        // The channel exists before anything can post. The daily check is enqueued once and kept
        // (KEEP), so a start never adds a second one; with alerts off, it is taken off the schedule.
        NotificationChannels.create(this);
        ExpiryAlertScheduler.sync(this, AppPreferences.from(this).isExpiryAlertsEnabled());
    }

    /**
     * Returns the app's one database. Only the repositories under {@code data/} use it.
     *
     * @return the database built in {@link #onCreate()}
     */
    @NonNull
    public AppDatabase getDatabase() {
        return database;
    }

    /**
     * Returns the executor every database write runs on. It has a single thread, so writes keep the
     * order they were submitted in and never run on the main thread.
     *
     * @return the write executor
     */
    @NonNull
    public Executor getIoExecutor() {
        return ioExecutor;
    }

    /**
     * Returns the executor the strict matcher runs on (Issue 23). It has a single thread of its own,
     * apart from the write thread, and is never the main thread.
     *
     * @return the matching executor
     */
    @NonNull
    public Executor getMatchExecutor() {
        return matchExecutor;
    }

    /**
     * Returns the app's one {@link StrictMatcher}, built the first time it is asked for over the
     * shipped alias table ({@code aliases.json}). The matcher holds no state between calls, so every
     * screen can share it. Reads an asset the first time, so call it off the main thread.
     *
     * @return the matcher
     * @throws UncheckedIOException if the alias table cannot be read, which means a broken APK
     */
    @WorkerThread
    @NonNull
    public synchronized StrictMatcher getStrictMatcher() {
        if (strictMatcher == null) {
            try {
                strictMatcher = new StrictMatcher(new IngredientNormaliser(AliasLoader.load(this)), new UnitConverter());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + AliasLoader.ASSET_NAME, e);
            }
        }
        return strictMatcher;
    }

    /**
     * Returns the one pantry repository, the way every screen reads and writes pantry items. The same
     * instance on every call, so every screen observes the same {@code LiveData}.
     *
     * @return the pantry repository built in {@link #onCreate()}
     */
    @NonNull
    public PantryRepository getPantryRepository() {
        return pantryRepository;
    }

    /**
     * Returns the one recipe repository, the way every screen reads recipes. It has no writes: recipes
     * are seed data (decision 7). The same instance on every call.
     *
     * @return the recipe repository built in {@link #onCreate()}
     */
    @NonNull
    public RecipeRepository getRecipeRepository() {
        return recipeRepository;
    }

    /**
     * Says whether the first-run seed has finished. Until it has, an empty recipe table only means the
     * recipes are on their way, and the Recipes tab waits instead of saying they did not load.
     *
     * @return {@code false} until the seed has run on the write thread, then {@code true} for good
     */
    @NonNull
    public LiveData<Boolean> getRecipeSeedDone() {
        return recipeSeedDone;
    }

    /**
     * Points the repositories at {@code replacement}, for an instrumented test that needs a database
     * the app's own data cannot reach, such as one with no recipes. Call it on the main thread before
     * the screen under test is opened, and put the original back afterwards: screens already open keep
     * the repositories they were given.
     *
     * @param replacement the database the repositories should use from now on
     * @return the database they used until now
     */
    @VisibleForTesting
    @NonNull
    public AppDatabase replaceDatabaseForTesting(@NonNull AppDatabase replacement) {
        AppDatabase previous = database;
        database = replacement;
        pantryRepository = new PantryRepository(replacement, ioExecutor);
        recipeRepository = new RecipeRepository(replacement);
        return previous;
    }

    /**
     * Returns the application object from any context, such as an Activity or a Fragment's
     * {@code requireContext()}.
     *
     * @param context any context of this app
     * @return the application
     */
    @NonNull
    public static SpmApplication from(@NonNull Context context) {
        return (SpmApplication) context.getApplicationContext();
    }
}
