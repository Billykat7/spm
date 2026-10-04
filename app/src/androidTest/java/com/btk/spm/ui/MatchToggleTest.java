package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * The strict rule through the screens (brief §2.3, Issue 32), with the pantry of
 * {@code docs/DEMO/MATCH_PROOF_STEPS.md} typed into the form: four of Tomato pasta's five ingredients
 * give the brief's zero-match sentence and no Tomato pasta among the suggestions; the fifth, garlic,
 * puts it there; deleting the garlic takes it out again. One {@code MainActivity} throughout, no
 * restart.
 */
@RunWith(AndroidJUnit4.class)
public class MatchToggleTest {

    /** The seed recipe the demo uses (Issue 26). */
    private static final String RECIPE = "Tomato pasta";

    private final Context context = ApplicationProvider.getApplicationContext();
    private final FreshAppRule freshApp = new FreshAppRule();
    private final ActivityScenarioRule<MainActivity> mainActivity = new ActivityScenarioRule<>(MainActivity.class);

    @Rule
    public final TestRule rules = RuleChain.outerRule(freshApp).around(mainActivity);

    @Test
    public void fourOfFive_isTheZeroMatchState_theFifthAddsTheRecipe_andDeletingItTakesItAway() {
        // The demo's rows 1 to 4
        PantryFlows.addThroughTheForm("Pasta", "200", Unit.G);
        PantryFlows.addThroughTheForm("Tomatoes", "4", Unit.PCS);
        PantryFlows.addThroughTheForm("Olive oil", "2", Unit.TBSP);
        PantryFlows.addThroughTheForm("Salt", "2", Unit.G);

        onView(withId(R.id.nav_recipes)).perform(click());
        onView(isRoot()).perform(ListWait.until(zeroMatchRow()));
        onView(isRoot()).perform(ListWait.untilGone(suggested()));

        // Row 5, garlic: the recipe is suggested
        onView(withId(R.id.nav_pantry)).perform(click());
        PantryFlows.addThroughTheForm("Garlic", "2", Unit.PCS);
        onView(withId(R.id.nav_recipes)).perform(click());
        onView(isRoot()).perform(ListWait.until(suggested()));
        onView(isRoot()).perform(ListWait.untilGone(zeroMatchRow()));

        // Garlic deleted from the Pantry tab: the recipe goes, the sentence comes back
        onView(withId(R.id.nav_pantry)).perform(click());
        onView(withContentDescription(context.getString(R.string.pantry_item_options_for, "Garlic"))).perform(click());
        onView(withText(R.string.action_delete)).inRoot(isPlatformPopup()).perform(click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
        onView(isRoot()).perform(ListWait.untilGone(PantryFlows.rowNamed("Garlic")));
        onView(withId(R.id.nav_recipes)).perform(click());
        onView(isRoot()).perform(ListWait.untilGone(suggested()));
        onView(isRoot()).perform(ListWait.until(zeroMatchRow()));
    }

    /** Tomato pasta as a suggestion: its name beside "You have all 5 ingredients", not an almost-there card. */
    static Matcher<View> suggested() {
        return allOf(withId(R.id.name), withText(RECIPE), hasSibling(withId(R.id.have_all)), isDisplayed());
    }

    /** The brief's zero-match sentence as the list's first row. */
    private Matcher<View> zeroMatchRow() {
        return allOf(withId(R.id.message), withText(R.string.recipes_empty_no_match), isDisplayed());
    }
}
