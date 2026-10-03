package com.btk.spm.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Checks the helper the DAO tests rely on before they rely on it: it returns a value, a {@code null}
 * value, and fails rather than hanging when nothing is delivered.
 */
@RunWith(AndroidJUnit4.class)
public class LiveDataTestUtilTest {

    @Test
    public void returnsTheCurrentValue() throws InterruptedException {
        MutableLiveData<String> liveData = new MutableLiveData<>("egg");
        assertEquals("egg", LiveDataTestUtil.getOrAwaitValue(liveData));
    }

    @Test
    public void returnsAValuePostedLater() throws InterruptedException {
        MutableLiveData<Integer> liveData = new MutableLiveData<>();
        liveData.postValue(3);
        assertEquals(Integer.valueOf(3), LiveDataTestUtil.getOrAwaitValue(liveData));
    }

    @Test
    public void returnsNullWhenNullIsTheValue() throws InterruptedException {
        MutableLiveData<String> liveData = new MutableLiveData<>(null);
        assertNull(LiveDataTestUtil.getOrAwaitValue(liveData));
    }

    @Test
    public void failsWhenNoValueArrives() {
        MutableLiveData<String> liveData = new MutableLiveData<>();
        assertThrows(AssertionError.class,
                () -> LiveDataTestUtil.getOrAwaitValue(liveData, 100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void stopsObservingAfterward() throws InterruptedException {
        MutableLiveData<String> liveData = new MutableLiveData<>("egg");
        LiveDataTestUtil.getOrAwaitValue(liveData);
        boolean[] observed = new boolean[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> observed[0] = liveData.hasObservers());
        assertFalse(observed[0]);
    }
}
