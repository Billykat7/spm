package com.btk.spm.notifications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;
import com.btk.spm.settings.PrefKey;

import org.junit.rules.ExternalResource;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * What the alert's device tests share (Issue 29): an in-memory database swapped into the app, the
 * alert settings set and put back, {@code POST_NOTIFICATIONS} granted on API 33 and later, and the
 * alert looked up among the app's active notifications.
 *
 * <p>The worker reads the pantry through {@code SpmApplication}'s repository, so the only way a name
 * can reach the notification is the query on this database.
 */
final class AlertFixture extends ExternalResource {

    /** How long the system may take to list a notification after {@code notify()}. */
    private static final long POST_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    final Context context = ApplicationProvider.getApplicationContext();
    final LocalDate today = LocalDate.now();

    private final SpmApplication app = ApplicationProvider.getApplicationContext();
    private final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    private final NotificationManager notifications = context.getSystemService(NotificationManager.class);

    private AppDatabase memory;
    private AppDatabase original;
    private Map<String, ?> preferencesBefore;

    @Override
    protected void before() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        preferencesBefore = new HashMap<>(preferences.getAll());
        memory = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> original = app.replaceDatabaseForTesting(memory));
        notifications.cancel(ExpiryCheckWorker.NOTIFICATION_ID);
        setAlerts(true, 3);
    }

    @Override
    protected void after() {
        notifications.cancel(ExpiryCheckWorker.NOTIFICATION_ID);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.replaceDatabaseForTesting(original));
        memory.close();
        SharedPreferences.Editor restore = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : preferencesBefore.entrySet()) {
            if (entry.getValue() instanceof Boolean b) {
                restore.putBoolean(entry.getKey(), b);
            } else if (entry.getValue() instanceof Integer i) {
                restore.putInt(entry.getKey(), i);
            } else if (entry.getValue() instanceof String text) {
                restore.putString(entry.getKey(), text);
            }
        }
        restore.commit();
    }

    /** Sets what the Settings tab would: the alert switch and the threshold. */
    void setAlerts(boolean enabled, int thresholdDays) {
        preferences.edit()
                .putBoolean(PrefKey.EXPIRY_ALERTS_ENABLED.key(), enabled)
                .putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), thresholdDays)
                .commit();
    }

    /** Puts an item in the pantry the worker will read. */
    void insert(String name, double quantity, Unit unit, @Nullable LocalDate expiry) {
        memory.pantryItemDao().insert(new PantryItem(name, quantity, unit, expiry, System.currentTimeMillis()));
    }

    /**
     * Returns the alert once the system lists it, or {@code null} if it does not within five seconds.
     * A short poll with a deadline, never a fixed wait: {@code notify()} returns before the system
     * service has added the notification to its list.
     */
    @Nullable
    Notification awaitAlert() throws InterruptedException {
        long deadline = System.currentTimeMillis() + POST_TIMEOUT_MS;
        Object poll = new Object();
        while (true) {
            Notification posted = activeAlert();
            if (posted != null || System.currentTimeMillis() >= deadline) {
                return posted;
            }
            synchronized (poll) {
                poll.wait(50);
            }
        }
    }

    /** Returns the alert if the system lists it now, or {@code null}. */
    @Nullable
    Notification activeAlert() {
        for (StatusBarNotification posted : notifications.getActiveNotifications()) {
            if (posted.getId() == ExpiryCheckWorker.NOTIFICATION_ID) {
                return posted.getNotification();
            }
        }
        return null;
    }

    /**
     * The body the alert should have for these items, in the given order, worded from the app's
     * resources: {@code expectedBody("tomato", 1L, "milk", 3L)} is "tomato (tomorrow), milk (in 3 days)"
     * in English, and the same test passes in another language.
     *
     * @param namesAndDaysLeft each item's name, then its days left
     */
    String expectedBody(Object... namesAndDaysLeft) {
        ResourceExpiryTexts texts = new ResourceExpiryTexts(context.getResources());
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < namesAndDaysLeft.length; i += 2) {
            if (i > 0) {
                body.append(texts.separator());
            }
            body.append(texts.item((String) namesAndDaysLeft[i], texts.when((Long) namesAndDaysLeft[i + 1])));
        }
        return body.toString();
    }

    /** The alert's body, as the expanded notification shows it. */
    static String body(Notification alert) {
        return String.valueOf(alert.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
    }

    /** The alert's title. */
    static String title(Notification alert) {
        return String.valueOf(alert.extras.getCharSequence(Notification.EXTRA_TITLE));
    }
}
