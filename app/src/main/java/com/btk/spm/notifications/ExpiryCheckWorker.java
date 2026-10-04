package com.btk.spm.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.btk.spm.BuildConfig;
import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.ExpiryRules;
import com.btk.spm.settings.AppPreferences;
import com.btk.spm.ui.MainActivity;
import com.btk.spm.ui.Tab;
import com.btk.spm.util.ExpiryStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The daily expiring-soon check (Issue 29): what the Settings tab's alert switch controls.
 *
 * <p>WorkManager runs it about once a day ({@link ExpiryAlertScheduler}), with no screen open.
 * {@link #doWork()} does nothing unless every step says yes:
 * <ol>
 *   <li>alerts are on ({@link AppPreferences#isExpiryAlertsEnabled()}); off, it returns success and
 *       posts nothing;</li>
 *   <li>the pantry has items expired or expiring within the threshold, read in one synchronous query
 *       and judged by {@link ExpiryRules}, the same rule as the pantry badges (Issue 17), so the alert
 *       and the badge never disagree about an item;</li>
 *   <li>the app may post: on Android 13 and later that needs the {@code POST_NOTIFICATIONS}
 *       permission, and on any version the user can block the app's notifications.</li>
 * </ol>
 * Then it posts one notification with a fixed id, so a second run replaces the first instead of
 * stacking, worded by {@link ExpiryMessageBuilder}. Tapping it opens the Pantry tab. Every outcome is
 * a success: a skipped check is not a failure to retry.
 */
public class ExpiryCheckWorker extends Worker {

    /** The logcat tag of every outcome, in debug builds: {@code adb logcat -s ExpiryCheckWorker}. */
    public static final String TAG = "ExpiryCheckWorker";

    /** The alert's id: one alert at a time, replaced by the next run. */
    @VisibleForTesting
    public static final int NOTIFICATION_ID = 29;

    /** The tap's request code; with the fixed id, the same PendingIntent is updated rather than added. */
    private static final int OPEN_PANTRY_REQUEST = 0;

    /**
     * Created by WorkManager, never by the app.
     *
     * @param context the application context
     * @param params  this run's parameters
     */
    public ExpiryCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @WorkerThread
    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppPreferences preferences = AppPreferences.from(context);
        if (!preferences.isExpiryAlertsEnabled()) {
            log("skipped: alerts are off");
            return Result.success();
        }
        // The edge of the app, like the ViewModel factories: the day is read here, once per run
        LocalDate today = LocalDate.now();
        int threshold = preferences.getExpiryThresholdDays();
        List<PantryItem> due = SpmApplication.from(context).getPantryRepository()
                .findWithExpiryOnOrBefore(today.plusDays(threshold));

        List<ExpiringItem> items = new ArrayList<>(due.size());
        for (PantryItem item : due) {
            ExpiryStatus status = ExpiryRules.statusOf(item.getExpiryDate(), today, threshold);
            if (status == ExpiryStatus.EXPIRED || status == ExpiryStatus.EXPIRING_SOON) {
                items.add(new ExpiringItem(item.getName(), ExpiryRules.daysUntil(item.getExpiryDate(), today)));
            }
        }
        Optional<ExpiryMessage> message = ExpiryMessageBuilder.build(items, new ResourceExpiryTexts(context.getResources()));
        if (message.isEmpty()) {
            log("nothing expires within " + threshold + " days");
            return Result.success();
        }
        if (!NotificationAccess.canPost(context)) {
            log("skipped: notifications are not allowed");
            return Result.success();
        }
        if (post(context, message.get())) {
            log("posted: " + items.size() + " items");
        }
        return Result.success();
    }

    /**
     * Posts the alert on its channel, replacing any earlier one. Called only after {@link NotificationAccess#canPost},
     * but the user can still revoke the permission in between, so a refusal is caught, not thrown.
     *
     * @return whether the system took the notification
     */
    private static boolean post(@NonNull Context context, @NonNull ExpiryMessage message) {
        // FLAG_IMMUTABLE: required from API 31, and nothing may fill in this Intent later
        PendingIntent openPantry = PendingIntent.getActivity(context, OPEN_PANTRY_REQUEST,
                MainActivity.intentFor(context, Tab.PANTRY),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(context, NotificationChannels.EXPIRY_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(message.title())
                .setContentText(message.body())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message.body()))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openPantry)
                .setAutoCancel(true)
                .build();
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
            return true;
        } catch (SecurityException revokedSinceTheCheck) {
            log("skipped: the permission was revoked");
            return false;
        }
    }

    /** Logs an outcome in debug builds; never an item's name, so no pantry data reaches a release log. */
    private static void log(@NonNull String outcome) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, outcome);
        }
    }
}
