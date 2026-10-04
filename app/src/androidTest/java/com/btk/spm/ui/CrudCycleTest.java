package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Create, read, update and delete through the real screens (brief §3.2, Issue 32): the FAB and the
 * form add "tomato, 4 pcs"; the row shows it; a tap opens the same form, 6 replaces 4 and the row
 * shows "6 pcs"; the row's overflow Delete, confirmed, removes it and the empty state comes back.
 * Every step is checked on the list the user sees, not on the database.
 */
@RunWith(AndroidJUnit4.class)
public class CrudCycleTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final FreshAppRule freshApp = new FreshAppRule();
    private final ActivityScenarioRule<MainActivity> mainActivity = new ActivityScenarioRule<>(MainActivity.class);

    /** The fresh app first, then the screen, so the screen opens on the test's database. */
    @Rule
    public final TestRule rules = RuleChain.outerRule(freshApp).around(mainActivity);

    @Test
    public void addReadEditDelete_throughTheFabTheFormTheRowAndTheOverflow() {
        // Create, and Read: the row is in the list
        PantryFlows.addThroughTheForm("tomato", "4", Unit.PCS);
        onView(PantryFlows.row("tomato", "4", Unit.PCS)).check(matches(isDisplayed()));

        // Update: the row opens the same form, prefilled; 6 replaces 4
        onView(withId(R.id.pantry_list))
                .perform(RecyclerViewActions.actionOnItem(hasDescendant(withText("tomato")), click()));
        onView(withText(R.string.title_edit_ingredient)).check(matches(isDisplayed()));
        onView(withId(R.id.quantity_input)).perform(replaceText("6"), closeSoftKeyboard());
        onView(withId(R.id.action_save)).perform(click());
        onView(isRoot()).perform(ListWait.until(PantryFlows.row("tomato", "6", Unit.PCS)));

        // Delete: the overflow, the confirmation, and the row is gone
        onView(withContentDescription(context.getString(R.string.pantry_item_options_for, "tomato"))).perform(click());
        onView(withText(R.string.action_delete)).inRoot(isPlatformPopup()).perform(click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
        onView(isRoot()).perform(ListWait.untilGone(PantryFlows.rowNamed("tomato")));
        onView(withText(R.string.pantry_empty_title)).check(matches(isDisplayed()));
    }
}
