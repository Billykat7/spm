package com.btk.spm.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.btk.spm.domain.Unit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * {@link ExpiryAlertScheduler} against WorkManager's test implementation (Issue 29): one unique
 * periodic check however often it is scheduled, none after cancel, and a period that has elapsed,
 * through {@link TestDriver}, running the worker that posts the alert. A synchronous executor runs the
 * work on the test thread, so nothing waits for the real scheduler.
 */
@RunWith(AndroidJUnit4.class)
public class ExpiryAlertSchedulerTest {

    @Rule
    public final AlertFixture fixture = new AlertFixture();

    private WorkManager workManager;

    @Before
    public void useTheTestWorkManager() {
        Configuration configuration = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(fixture.context, configuration);
        workManager = WorkManager.getInstance(fixture.context);
    }

    @Test
    public void scheduleTwice_leavesOneEnqueuedCheck_withTheSameId() throws Exception {
        ExpiryAlertScheduler.schedule(fixture.context);
        UUID first = daily().get(0).getId();
        ExpiryAlertScheduler.schedule(fixture.context);

        List<WorkInfo> daily = daily();
        assertEquals(1, daily.size());
        assertEquals(WorkInfo.State.ENQUEUED, daily.get(0).getState());
        assertEquals("KEEP: the first request stays", first, daily.get(0).getId());
    }

    @Test
    public void cancel_leavesNoCheckToRun() throws Exception {
        ExpiryAlertScheduler.schedule(fixture.context);
        ExpiryAlertScheduler.cancel(fixture.context);

        for (WorkInfo info : daily()) {
            assertTrue("still scheduled: " + info.getState(), info.getState().isFinished());
        }
    }

    @Test
    public void aPeriodPassing_runsTheCheck_andPostsTheAlert() throws Exception {
        fixture.insert("tomato", 4, Unit.PCS, fixture.today.plusDays(1));
        ExpiryAlertScheduler.schedule(fixture.context);
        UUID id = daily().get(0).getId();

        TestDriver driver = WorkManagerTestInitHelper.getTestDriver(fixture.context);
        assertNotNull(driver);
        driver.setAllConstraintsMet(id);
        driver.setPeriodDelayMet(id);

        Notification alert = fixture.awaitAlert();
        assertNotNull("the periodic run posted nothing", alert);
        assertEquals(fixture.expectedBody("tomato", 1L), AlertFixture.body(alert));
        // Periodic work goes back to waiting for its next period
        assertEquals(WorkInfo.State.ENQUEUED, daily().get(0).getState());
    }

    @Test
    public void runOnce_withAlertsOff_succeeds_andPostsNothing() throws Exception {
        fixture.setAlerts(false, 3);
        fixture.insert("tomato", 4, Unit.PCS, fixture.today.plusDays(1));

        ExpiryAlertScheduler.runOnce(fixture.context);

        List<WorkInfo> now = workManager.getWorkInfosForUniqueWork(ExpiryAlertScheduler.CHECK_NOW).get();
        assertEquals(1, now.size());
        assertEquals(WorkInfo.State.SUCCEEDED, now.get(0).getState());
        assertNull(fixture.activeAlert());
    }

    private List<WorkInfo> daily() throws ExecutionException, InterruptedException {
        return workManager.getWorkInfosForUniqueWork(ExpiryAlertScheduler.DAILY_CHECK).get();
    }
}
