package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import org.hamcrest.Matcher;

/**
 * The steps a person takes on the Pantry tab and the add form, for the Espresso flows (Issue 32):
 * the FAB, the four fields, the unit dropdown and Save, then the row as the list shows it. Every
 * expected text is built from the app's resources, never typed here.
 */
public final class PantryFlows {

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private PantryFlows() {
        // Static steps only; never instantiated
    }

    /**
     * Adds an ingredient through the FAB and the form, with no expiry date, and waits for its row.
     *
     * @param name     what to type in the name field
     * @param quantity what to type in the quantity field
     * @param unit     the unit to pick from the dropdown
     */
    public static void addThroughTheForm(@NonNull String name, @NonNull String quantity, @NonNull Unit unit) {
        onView(withId(R.id.add_ingredient)).perform(click());
        fillAndSave(name, quantity, unit);
        onView(isRoot()).perform(ListWait.until(row(name, quantity, unit)));
    }

    /**
     * Fills the open form and taps Save.
     *
     * @param name     the name, or an empty string to leave it empty
     * @param quantity the quantity as typed
     * @param unit     the unit to pick, or {@code null} to pick none
     */
    public static void fillAndSave(@NonNull String name, @NonNull String quantity, Unit unit) {
        onView(withId(R.id.name_input)).perform(replaceText(name));
        onView(withId(R.id.quantity_input)).perform(replaceText(quantity), closeSoftKeyboard());
        if (unit != null) {
            onView(withId(R.id.unit_input)).perform(click());
            onView(withText(unit.symbolRes())).inRoot(isPlatformPopup()).perform(click());
        }
        onView(withId(R.id.action_save)).perform(click());
    }

    /**
     * Matches the pantry row's name next to its amount, as the list shows them: {@code tomato} over
     * {@code 4 pcs}.
     *
     * @param name     the item's name
     * @param quantity the amount as the row prints it
     * @param unit     the unit the amount is shown in
     * @return a matcher for the displayed name of that row
     */
    @NonNull
    public static Matcher<View> row(@NonNull String name, @NonNull String quantity, @NonNull Unit unit) {
        String amount = CONTEXT.getString(R.string.quantity_with_unit, quantity, CONTEXT.getString(unit.symbolRes()));
        return allOf(withId(R.id.name), withText(name), hasSibling(allOf(withId(R.id.quantity), withText(amount))),
                isDisplayed());
    }

    /**
     * Matches the displayed name of a pantry row, whatever its amount.
     *
     * @param name the item's name
     * @return a matcher for that row's name
     */
    @NonNull
    public static Matcher<View> rowNamed(@NonNull String name) {
        return allOf(withId(R.id.name), withText(name), isDisplayed());
    }
}
