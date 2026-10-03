package com.btk.spm;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.btk.spm.data.db.AppDatabase;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The process-wide entry point of the Smart Pantry Manager.
 *
 * <p>Exists so that objects that must be created exactly once per process have a home: the Room
 * database, the single-thread I/O executor and the repositories built on them (Issues 9 and 10 add
 * those). Screens reach them through this class, never by constructing their own, so every screen
 * observes the same pantry (non-negotiable 6). This is the only place in the app that calls
 * {@code Room.databaseBuilder}.
 */
public class SpmApplication extends Application {

    /** The name of the write thread, as it appears in a stack trace or the profiler. */
    private static final String IO_THREAD_NAME = "spm-io";

    private AppDatabase database;
    private ExecutorService ioExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        // Building is cheap: Room opens the file and creates the tables on the first query, which
        // runs off the main thread, so this does no disk work during start-up.
        database = Room.databaseBuilder(this, AppDatabase.class, AppDatabase.DB_NAME).build();
        // One thread, so writes run in the order they were submitted: an insert followed by its undo
        // can never land the other way round. The process owns it, so it is never shut down.
        ioExecutor = Executors.newSingleThreadExecutor(task -> new Thread(task, IO_THREAD_NAME));
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
