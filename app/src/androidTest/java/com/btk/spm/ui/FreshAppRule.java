package com.btk.spm.ui;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.idling.concurrent.IdlingThreadPoolExecutor;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.data.seed.SeedResult;

import org.junit.rules.ExternalResource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Gives every Espresso test the same fresh, seeded and synchronised app (Issue 32).
 *
 * <p>Before each test it puts an <b>in-memory</b> {@link AppDatabase} in place of the app's file,
 * seeds the twenty recipes into it, and clears the default preferences, so every test starts from
 * exactly the same empty pantry and default settings, with no row left behind by a test that failed.
 * In memory is also fast, and it lives as long as the test process: {@code scenario.recreate()} and a
 * second {@code ActivityScenario} run in that process against the same {@code Application}, so a row
 * that survives them proves the <i>screens</i> read it back from Room rather than holding a copy.
 * Surviving process death is proven at the DAO level instead, on a real file
 * ({@code PersistenceAcrossRestartTest}, Issue 12).
 *
 * <p>It also replaces the threads the app does work on with {@link IdlingThreadPoolExecutor}s
 * registered with Espresso: the write thread, the matching thread, and Room's own query thread, which
 * computes every {@code LiveData}. Espresso then waits for a save, a Room emission and a match as it
 * waits for the main thread, so no test sleeps. After each test everything is unregistered and put
 * back, and the database is closed.
 */
public final class FreshAppRule extends ExternalResource {

    private static final long SHUTDOWN_S = 5;

    private final SpmApplication app = ApplicationProvider.getApplicationContext();

    private IdlingThreadPoolExecutor io;
    private IdlingThreadPoolExecutor match;
    private IdlingThreadPoolExecutor query;
    private AppDatabase database;

    private AppDatabase originalDatabase;
    private ExecutorService originalIo;
    private ExecutorService originalMatch;

    @Override
    protected void before() {
        io = idling("spm-io-test", 1);
        match = idling("spm-match-test", 1);
        query = idling("room-query-test", 2);
        IdlingRegistry.getInstance().register(io, match, query);

        // Room computes each LiveData on its query executor: an idling one, so Espresso sees it
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase.class).setQueryExecutor(query).build();
        SeedResult seeded = new RecipeSeeder(app, database).seedIfEmpty();
        if (seeded != SeedResult.SEEDED) {
            throw new IllegalStateException("The test database was not seeded: " + seeded);
        }
        clearPreferences();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            originalIo = app.replaceIoExecutorForTesting(io);
            originalMatch = app.replaceMatchExecutorForTesting(match);
            originalDatabase = app.replaceDatabaseForTesting(database);
        });
    }

    @Override
    protected void after() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            app.replaceDatabaseForTesting(originalDatabase);
            app.replaceMatchExecutorForTesting(originalMatch);
            app.replaceIoExecutorForTesting(originalIo);
        });
        IdlingRegistry.getInstance().unregister(io, match, query);
        for (ExecutorService executor : new ExecutorService[]{io, match, query}) {
            executor.shutdown();
            try {
                executor.awaitTermination(SHUTDOWN_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        database.close();
        clearPreferences();
    }

    /**
     * Returns the database the app is using for this test, for a test that writes its starting rows
     * directly or checks that nothing was written.
     *
     * @return the in-memory database
     */
    @NonNull
    public AppDatabase database() {
        return database;
    }

    private void clearPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
        preferences.edit().clear().commit();
    }

    private static IdlingThreadPoolExecutor idling(String name, int threads) {
        return new IdlingThreadPoolExecutor(name, threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, name));
    }
}
