package com.btk.spm.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import com.btk.spm.R;

/**
 * The app's notification channels; there is one, for the expiring-soon alert (Issue 29).
 *
 * <p>Since Android 8.0, the app's floor (minSdk 26), a notification without a channel is not shown,
 * and the channel is what the user sees, names and can turn off under the app's notification
 * settings. Its id is a constant here and nowhere else.
 */
public final class NotificationChannels {

    /** The expiring-soon alert's channel. Stored by the system: changing it orphans the user's choice. */
    public static final String EXPIRY_ALERTS = "expiry_alerts";

    private NotificationChannels() {
        // Constants and one helper; never instantiated
    }

    /**
     * Creates the app's channels. Safe to call on every start: creating a channel that exists only
     * updates its name and description, and never undoes what the user changed (its importance, or
     * turning it off). {@code SpmApplication.onCreate} calls it before anything can post.
     *
     * @param context any context of this app
     */
    public static void create(@NonNull Context context) {
        NotificationChannelCompat expiry = new NotificationChannelCompat.Builder(EXPIRY_ALERTS,
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.expiry_channel_name))
                .setDescription(context.getString(R.string.expiry_channel_description))
                .build();
        NotificationManagerCompat.from(context).createNotificationChannel(expiry);
    }
}
