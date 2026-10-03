package com.btk.spm.testing;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads one value from a {@link LiveData} in an instrumented test, for the DAO tests of Issues 9 and
 * 10 (added in Issue 8 so the two do not both create it).
 *
 * <p>A Room {@code LiveData} query computes nothing until something observes it, and then delivers
 * its result on the main thread. This helper observes once, waits on a {@link CountDownLatch} rather
 * than sleeping, and stops observing, so a test reads the value the way a screen would.
 */
public final class LiveDataTestUtil {

    /** How long {@link #getOrAwaitValue(LiveData)} waits for the first value. */
    public static final long DEFAULT_TIMEOUT_SECONDS = 2;

    private LiveDataTestUtil() {
    }

    /**
     * Returns the first value {@code liveData} delivers, waiting up to
     * {@value #DEFAULT_TIMEOUT_SECONDS} seconds.
     *
     * @param liveData the stream to read, usually a DAO query
     * @param <T> the value type
     * @return the value, which may be {@code null} (a query by an unknown id emits {@code null})
     * @throws InterruptedException if the test thread is interrupted while waiting
     * @throws AssertionError if no value arrives in time
     */
    @Nullable
    public static <T> T getOrAwaitValue(@NonNull LiveData<T> liveData) throws InterruptedException {
        return getOrAwaitValue(liveData, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Returns the first value {@code liveData} delivers, waiting up to {@code timeout}.
     *
     * @param liveData the stream to read
     * @param timeout how long to wait
     * @param unit the unit of {@code timeout}
     * @param <T> the value type
     * @return the value, which may be {@code null}
     * @throws InterruptedException if the test thread is interrupted while waiting
     * @throws AssertionError if no value arrives in time
     */
    @Nullable
    public static <T> T getOrAwaitValue(@NonNull LiveData<T> liveData, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException {
        AtomicReference<T> value = new AtomicReference<>();
        CountDownLatch delivered = new CountDownLatch(1);
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T newValue) {
                value.set(newValue);
                delivered.countDown();
                liveData.removeObserver(this);
            }
        };
        // observeForever must be called on the main thread; a test runs on the instrumentation thread
        runOnMainThread(() -> liveData.observeForever(observer));
        try {
            if (!delivered.await(timeout, unit)) {
                throw new AssertionError("LiveData delivered no value within " + timeout + " " + unit);
            }
        } finally {
            runOnMainThread(() -> liveData.removeObserver(observer));
        }
        return value.get();
    }

    private static void runOnMainThread(@NonNull Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        }
    }
}
