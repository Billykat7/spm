package com.btk.spm.notifications;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Whether the app may post its alert, asked in one place by the worker and the Settings tab (Issue 29).
 *
 * <p>Android 13 (API 33) made {@code POST_NOTIFICATIONS} a runtime permission: an app that targets 33
 * or later posts nothing until the user allows it. Below 33 it is granted at install, but on any
 * version the user can still block the app's notifications in system settings. Both are checked here.
 */
public final class NotificationAccess {

    private NotificationAccess() {
        // Static checks only; never instantiated
    }

    /**
     * Says whether this device asks the user before the app may post: API 33 or later, with the
     * permission not granted yet. Below 33 there is never a prompt.
     *
     * @param context any context of this app
     * @return {@code true} when the Settings tab should request {@code POST_NOTIFICATIONS}
     */
    public static boolean needsRuntimePermission(@NonNull Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Says whether a notification from the app would be shown now: the permission is granted where it
     * is needed, and the user has not blocked the app's notifications.
     *
     * @param context any context of this app
     * @return {@code true} when the alert can be posted
     */
    public static boolean canPost(@NonNull Context context) {
        return !needsRuntimePermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Returns the Intent that opens this app's page of notification settings, where a denied or blocked
     * alert can be allowed again.
     *
     * @param context any context of this app
     * @return an Intent for {@link Settings#ACTION_APP_NOTIFICATION_SETTINGS}
     */
    @NonNull
    public static Intent settingsIntent(@NonNull Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }
}
