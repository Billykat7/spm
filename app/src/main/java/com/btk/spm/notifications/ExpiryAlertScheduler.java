package com.btk.spm.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Puts {@link ExpiryCheckWorker} on WorkManager's schedule, or takes it off (Issue 29).
 *
 * <p>The check runs once every 24 hours as unique periodic work, so it is enqueued once, however
 * often the app starts: {@link ExistingPeriodicWorkPolicy#KEEP} leaves a check that is already
 * scheduled, and its next run, as they are. WorkManager keeps the schedule across a reboot and picks
 * the moment within the period, which is all the brief's "expiring soon" needs: no exact alarm, no
 * foreground service, no boot receiver.
 */
public final class ExpiryAlertScheduler {

    /** The daily check's unique work name. */
    public static final String DAILY_CHECK = "expiry_check_daily";

    /** The "Send a test alert now" run's unique work name. */
    public static final String CHECK_NOW = "expiry_check_now";

    /** How often the check runs. */
    public static final long PERIOD_HOURS = 24;

    private ExpiryAlertScheduler() {
        // Static helpers only; never instantiated
    }

    /**
     * Schedules the daily check, unless it is already scheduled. Called on every app start while alerts
     * are on, and when the Settings switch turns them on.
     *
     * @param context any context of this app
     */
    public static void schedule(@NonNull Context context) {
        PeriodicWorkRequest daily = new PeriodicWorkRequest.Builder(ExpiryCheckWorker.class, PERIOD_HOURS, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY_CHECK, ExistingPeriodicWorkPolicy.KEEP, daily);
    }

    /**
     * Takes the daily check off the schedule. Called when alerts are turned off, and on a start with
     * them off.
     *
     * @param context any context of this app
     */
    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_CHECK);
    }

    /**
     * Schedules or cancels the daily check to match the alerts setting.
     *
     * @param context any context of this app
     * @param enabled whether alerts are on
     */
    public static void sync(@NonNull Context context, boolean enabled) {
        if (enabled) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    /**
     * Runs the check once, now, for "Send a test alert now". It is the same worker, so with alerts off
     * it posts nothing. A second tap replaces a run that has not started yet.
     *
     * @param context any context of this app
     */
    public static void runOnce(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(CHECK_NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.from(ExpiryCheckWorker.class));
    }
}
