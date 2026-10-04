package com.btk.spm.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link AppPreferences#observeBoolean} against the real {@code SharedPreferences} file on a device
 * (Issue 26): the current value on observe, a new one on each change, nothing once nobody observes.
 * Android calls change listeners on the main thread and holds them weakly, which the JVM test's
 * in-memory stand-in cannot show. Every write is a {@code commit()}, and the value the device had is
 * put back afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class AppPreferencesLiveTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    /** The app's own preferences file, the one {@link AppPreferences#from} reads. */
    private final SharedPreferences stored =
            context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);

    private boolean storedBefore;
    private boolean hadValue;

    @Before
    public void startFromOff() {
        hadValue = stored.contains(PrefKey.COUNT_EXPIRED_ITEMS.key());
        storedBefore = stored.getBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), false);
        stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), false).commit();
    }

    @After
    public void putTheValueBack() {
        SharedPreferences.Editor editor = stored.edit();
        if (hadValue) {
            editor.putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), storedBefore);
        } else {
            editor.remove(PrefKey.COUNT_EXPIRED_ITEMS.key());
        }
        editor.commit();
    }

    @Test
    public void observeBoolean_sendsTheCurrentValue_eachChange_andNothingOnceUnobserved() {
        LiveData<Boolean> countExpired = AppPreferences.from(context).observeBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
        List<Boolean> seen = new CopyOnWriteArrayList<>();
        Observer<Boolean> observer = seen::add;
        onMain(() -> countExpired.observeForever(observer));
        assertEquals(List.of(false), seen);

        // The listener runs on the main thread: write there, so it has run when this returns
        onMain(() -> stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).commit());
        onMain(() -> stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), false).commit());
        assertEquals(List.of(false, true, false), seen);

        onMain(() -> countExpired.removeObserver(observer));
        onMain(() -> stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).commit());
        assertEquals("a change while nobody observes is not sent", List.of(false, true, false), seen);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
}
