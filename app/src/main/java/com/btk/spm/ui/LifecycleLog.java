package com.btk.spm.ui;

import android.util.Log;

import androidx.annotation.NonNull;

import com.btk.spm.BuildConfig;

/**
 * Writes one logcat line per lifecycle callback of a screen, in debug builds only.
 *
 * <p>This is the evidence for the video's "Activity (or Fragment) lifecycle in your app": run
 * {@code adb logcat -s Lifecycle} and switch tabs, rotate or leave the app, and the callbacks appear
 * in the order Android calls them. Each line names the class and the instance, so a rotation shows a
 * new {@code MainActivity@...} being created after the old one is destroyed.
 *
 * <p>Release builds log nothing: {@link BuildConfig#DEBUG} is false there, and no user data is ever
 * passed in, only class and callback names.
 */
public final class LifecycleLog {

    /** The logcat tag every line uses: {@code adb logcat -s Lifecycle}. */
    public static final String TAG = "Lifecycle";

    private LifecycleLog() {
        // Static helper; never instantiated
    }

    /**
     * Logs that {@code owner} has just run {@code callback}.
     *
     * @param owner    the Activity or Fragment whose callback ran
     * @param callback the callback's name, such as {@code onStart}
     */
    public static void log(@NonNull Object owner, @NonNull String callback) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, name(owner) + " " + callback);
        }
    }

    /**
     * Logs a callback with a short detail, such as whether {@code onCreate} restored saved state.
     *
     * @param owner    the Activity or Fragment whose callback ran
     * @param callback the callback's name
     * @param detail   what is worth knowing about this call, never user data
     */
    public static void log(@NonNull Object owner, @NonNull String callback, @NonNull String detail) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, name(owner) + " " + callback + " (" + detail + ")");
        }
    }

    private static String name(Object owner) {
        return owner.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(owner));
    }
}
