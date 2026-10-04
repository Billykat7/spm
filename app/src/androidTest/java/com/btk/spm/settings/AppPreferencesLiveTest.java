package com.btk.spm.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.domain.UnitsSystem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link AppPreferences} against the real default {@code SharedPreferences} file on a device, the one
 * the Settings screen writes: {@link #observeBoolean_sendsTheCurrentValue_eachChange_andNothingOnceUnobserved}
 * (Issue 26) and the defaults, typed round trips and per-key listening of Issue 28. Android calls
 * change listeners on the main thread and holds them weakly, which the JVM test's in-memory stand-in
 * cannot show. Every write is a {@code commit()}, and whatever the device had is put back afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class AppPreferencesLiveTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    /** The app's own preferences file, the one {@link AppPreferences#from} reads and the Settings screen writes. */
    private final SharedPreferences stored = PreferenceManager.getDefaultSharedPreferences(context);

    /** Everything the file held before the test, to put back. */
    private Map<String, ?> before;

    @Before
    public void startFromAClearedFile() {
        before = new HashMap<>(stored.getAll());
        stored.edit().clear().commit();
    }

    @After
    public void putTheFileBack() {
        SharedPreferences.Editor editor = stored.edit().clear();
        for (Map.Entry<String, ?> entry : before.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean b) {
                editor.putBoolean(entry.getKey(), b);
            } else if (value instanceof Integer i) {
                editor.putInt(entry.getKey(), i);
            } else if (value instanceof String text) {
                editor.putString(entry.getKey(), text);
            }
        }
        editor.commit();
    }

    @Test
    public void aClearedFile_givesEveryDefault() {
        AppPreferences preferences = AppPreferences.from(context);

        assertTrue(preferences.isExpiryAlertsEnabled());
        assertEquals(3, preferences.getExpiryThresholdDays());
        assertEquals(UnitsSystem.METRIC, preferences.getUnitsSystem());
        assertFalse(preferences.isCountExpiredItems());
    }

    @Test
    public void whatTheSettingsScreenWrites_isReadBackTyped() {
        // The types the preference widgets store: a switch a boolean, the seek bar an int, the list a string
        stored.edit().putBoolean(PrefKey.EXPIRY_ALERTS_ENABLED.key(), false)
                .putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 7)
                .putString(PrefKey.UNITS_SYSTEM.key(), "IMPERIAL")
                .putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true)
                .commit();
        AppPreferences preferences = AppPreferences.from(context);

        assertFalse(preferences.isExpiryAlertsEnabled());
        assertEquals(7, preferences.getExpiryThresholdDays());
        assertEquals(UnitsSystem.IMPERIAL, preferences.getUnitsSystem());
        assertTrue(preferences.isCountExpiredItems());
    }

    @Test
    public void observeBoolean_isNotWokenByAnotherKey() {
        LiveData<Boolean> countExpired = AppPreferences.from(context).observeBoolean(PrefKey.COUNT_EXPIRED_ITEMS);
        List<Boolean> seen = new CopyOnWriteArrayList<>();
        Observer<Boolean> observer = seen::add;
        onMain(() -> countExpired.observeForever(observer));

        onMain(() -> stored.edit().putBoolean(PrefKey.EXPIRY_ALERTS_ENABLED.key(), false).commit());
        onMain(() -> stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 9).commit());
        onMain(() -> stored.edit().putBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), true).commit());
        onMain(() -> countExpired.removeObserver(observer));

        assertEquals(List.of(false, true), seen);
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
