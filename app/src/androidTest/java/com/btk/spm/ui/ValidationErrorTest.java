package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.R;
import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * A validation error through the real form (brief §3.1, Issue 32): an empty name and a quantity of 0
 * show both field errors at the same time, compared with their {@code strings.xml} values; no
 * Snackbar appears; and the pantry table is still empty.
 */
@RunWith(AndroidJUnit4.class)
public class ValidationErrorTest {

    private final FreshAppRule freshApp = new FreshAppRule();
    private final ActivityScenarioRule<MainActivity> mainActivity = new ActivityScenarioRule<>(MainActivity.class);

    @Rule
    public final TestRule rules = RuleChain.outerRule(freshApp).around(mainActivity);

    @Test
    public void anEmptyNameAndAZero_showBothErrors_noSnackbar_andNothingInserted() {
        onView(withId(R.id.add_ingredient)).perform(click());
        PantryFlows.fillAndSave("", "0", null);

        onView(withId(R.id.name_layout)).check(matches(hasError(R.string.error_name_required)));
        onView(withId(R.id.quantity_layout)).check(matches(hasError(R.string.error_quantity_positive)));
        onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist());
        onView(withId(R.id.action_save)).check(matches(isDisplayed()));
        assertEquals(0, freshApp.database().pantryItemDao().countSync());
    }

    /** Matches a {@link TextInputLayout} showing the error with this string resource's text. */
    private static Matcher<View> hasError(int messageRes) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(@NonNull View view) {
                if (!(view instanceof TextInputLayout layout) || layout.getError() == null) {
                    return false;
                }
                return layout.getError().toString().equals(view.getContext().getString(messageRes));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a TextInputLayout showing the error of string resource " + messageRes);
            }
        };
    }
}
