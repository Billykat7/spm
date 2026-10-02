package com.btk.spm;

import android.app.Application;

/**
 * The process-wide entry point of the Smart Pantry Manager.
 *
 * <p>Exists so that objects that must be created exactly once per process have a home: the Room
 * database, the single-thread I/O executor and the repositories built on them (Issue 9 adds
 * them). Screens reach those through this class, never by constructing their own, so every screen
 * observes the same pantry.
 */
public class SpmApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Issue 9 builds the database, the I/O executor and the repositories here.
    }
}
