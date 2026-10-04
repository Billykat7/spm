package com.btk.spm.ui.pantry;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.AppDatabase;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The add form shows every validation error at once and writes nothing (Issue 31): an empty name and a
 * quantity of 0 give both field errors together, from {@code strings.xml}, with no Toast or Snackbar
 * and Save still enabled; typing a letter in the name clears that error alone. An in-memory database
 * stands in for the app's, so the count proves nothing reached the table.
 */
@RunWith(AndroidJUnit4.class)
public class ValidationErrorsAtOnceTest {

    private final SpmApplication app = ApplicationProvider.getApplicationContext();

    private AppDatabase inMemory;
    private AppDatabase original;

    @Before
    public void useAnEmptyInMemoryDatabase() {
        inMemory = Room.inMemoryDatabaseBuilder(app, AppDatabase.class).build();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> original = app.replaceDatabaseForTesting(inMemory));
    }

    @After
    public void putTheAppsDatabaseBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.replaceDatabaseForTesting(original));
        inMemory.close();
    }

    @Test
    public void anEmptyNameAndAZeroQuantity_showBothErrorsAtOnce_andWriteNothing() {
        try (ActivityScenario<AddEditIngredientActivity> scenario =
                     ActivityScenario.launch(AddEditIngredientActivity.intentForAdd(app))) {
            onView(withId(R.id.quantity_input)).perform(typeText("0"), closeSoftKeyboard());
            onView(withId(R.id.action_save)).check(matches(isEnabled())).perform(click());

            assertEquals(app.getString(R.string.error_name_required), errorOf(scenario, R.id.name_layout));
            assertEquals(app.getString(R.string.error_quantity_positive), errorOf(scenario, R.id.quantity_layout));
            onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist());
            onView(withId(R.id.action_save)).check(matches(isEnabled()));
            assertEquals(0, inMemory.pantryItemDao().countSync());

            // One letter in the name: its error goes, the quantity's stays
            onView(withId(R.id.name_input)).perform(typeText("t"), closeSoftKeyboard());
            assertNull(errorOf(scenario, R.id.name_layout));
            assertEquals(app.getString(R.string.error_quantity_positive), errorOf(scenario, R.id.quantity_layout));
        }
    }

    /** The error a field's layout shows now, or {@code null}. */
    private static String errorOf(ActivityScenario<AddEditIngredientActivity> scenario, int layoutId) {
        AtomicReference<String> error = new AtomicReference<>();
        scenario.onActivity(activity -> {
            CharSequence shown = ((TextInputLayout) activity.findViewById(layoutId)).getError();
            error.set(shown == null ? null : shown.toString());
        });
        return error.get();
    }
}
