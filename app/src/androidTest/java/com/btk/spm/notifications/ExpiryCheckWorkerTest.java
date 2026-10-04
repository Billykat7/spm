package com.btk.spm.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Notification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * {@link ExpiryCheckWorker} run directly with {@link TestWorkerBuilder}, so no test waits a day for
 * WorkManager: its {@code doWork()} is called on the test thread against an in-memory pantry, and the
 * notification it posts is read back from the system (Issue 29).
 */
@RunWith(AndroidJUnit4.class)
public class ExpiryCheckWorkerTest {

    @Rule
    public final AlertFixture fixture = new AlertFixture();

    /** {@code doWork} runs on the calling thread; WorkManager's own executor is not involved. */
    private static final Executor DIRECT = Runnable::run;

    @Test
    public void alertsOff_succeeds_andPostsNothing() {
        fixture.setAlerts(false, 3);
        fixture.insert("tomato", 4, Unit.PCS, fixture.today.plusDays(1));

        assertEquals(ListenableWorker.Result.success(), run());
        assertNull(fixture.activeAlert());
    }

    @Test
    public void alertsOn_postsOneAlert_naming_theInsertedRow() throws InterruptedException {
        fixture.insert("tomato", 4, Unit.PCS, fixture.today.plusDays(1));

        assertEquals(ListenableWorker.Result.success(), run());

        Notification alert = fixture.awaitAlert();
        assertNotNull("no alert was posted", alert);
        assertEquals(fixture.context.getResources().getQuantityString(R.plurals.expiry_alert_title, 1, 1),
                AlertFixture.title(alert));
        assertEquals(fixture.expectedBody("tomato", 1L), AlertFixture.body(alert));
        assertEquals(NotificationChannels.EXPIRY_ALERTS, alert.getChannelId());
    }

    @Test
    public void theIssuesExample_twoItems_soonestFirst() throws InterruptedException {
        fixture.insert("milk", 1, Unit.L, fixture.today.plusDays(3));
        fixture.insert("tomato", 4, Unit.PCS, fixture.today.plusDays(1));
        fixture.insert("rice", 1, Unit.KG, null);
        fixture.insert("cheddar", 200, Unit.G, fixture.today.plusDays(4));

        run();

        Notification alert = fixture.awaitAlert();
        assertNotNull(alert);
        assertEquals(fixture.context.getResources().getQuantityString(R.plurals.expiry_alert_title, 2, 2),
                AlertFixture.title(alert));
        assertEquals(fixture.expectedBody("tomato", 1L, "milk", 3L), AlertFixture.body(alert));
    }

    @Test
    public void anExpiredItem_isNamed_withExpiredWording() throws InterruptedException {
        fixture.insert("yoghurt", 500, Unit.G, fixture.today.minusDays(2));

        run();

        Notification alert = fixture.awaitAlert();
        assertNotNull(alert);
        assertEquals(fixture.expectedBody("yoghurt", -2L), AlertFixture.body(alert));
    }

    @Test
    public void nothingWithinTheThreshold_succeeds_andPostsNothing() {
        fixture.insert("cheddar", 200, Unit.G, fixture.today.plusDays(4));
        fixture.insert("rice", 1, Unit.KG, null);

        assertEquals(ListenableWorker.Result.success(), run());
        assertNull(fixture.activeAlert());
    }

    @Test
    public void aSecondRun_replacesTheAlert_insteadOfStackingAnother() throws InterruptedException {
        fixture.insert("tomato", 4, Unit.PCS, fixture.today.plusDays(1));
        run();
        assertNotNull(fixture.awaitAlert());

        fixture.insert("milk", 1, Unit.L, fixture.today);
        run();

        // One id, so still one notification, now naming both
        Notification alert = fixture.awaitAlert();
        assertNotNull(alert);
        assertEquals(fixture.expectedBody("milk", 0L, "tomato", 1L), AlertFixture.body(alert));
    }

    private ListenableWorker.Result run() {
        return TestWorkerBuilder.from(fixture.context, ExpiryCheckWorker.class, DIRECT).build().doWork();
    }
}
