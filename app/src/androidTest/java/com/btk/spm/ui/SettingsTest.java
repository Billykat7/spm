package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.R;
import com.btk.spm.data.db.PantryItemDao;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.time.LocalDate;

/**
 * The Settings screen changes a match (decision 6, Issue 32): with Tomato pasta's five ingredients in
 * the pantry and the garlic expired yesterday, the recipe is not suggested; <i>Count expired items</i>
 * turned on, on the Settings tab, puts it on the Recipes tab; turned off, it goes again.
 */
@RunWith(AndroidJUnit4.class)
public class SettingsTest {

    private final FreshAppRule freshApp = new FreshAppRule();
    private final ActivityScenarioRule<MainActivity> mainActivity = new ActivityScenarioRule<>(MainActivity.class);

    @Rule
    public final TestRule rules = RuleChain.outerRule(freshApp).around(mainActivity);

    @Before
    public void fillThePantry_withTheGarlicExpired() {
        PantryItemDao dao = freshApp.database().pantryItemDao();
        long now = System.currentTimeMillis();
        dao.insert(new PantryItem("pasta", 200, Unit.G, null, now));
        dao.insert(new PantryItem("tomato", 4, Unit.PCS, null, now));
        dao.insert(new PantryItem("olive oil", 2, Unit.TBSP, null, now));
        dao.insert(new PantryItem("salt", 2, Unit.G, null, now));
        dao.insert(new PantryItem("garlic", 2, Unit.PCS, LocalDate.now().minusDays(1), now));
    }

    @Test
    public void countExpiredItems_onAndOff_putsTheRecipeInAndTakesItOut() {
        onView(withId(R.id.nav_recipes)).perform(click());
        onView(isRoot()).perform(ListWait.untilGone(MatchToggleTest.suggested()));

        onView(withId(R.id.nav_settings)).perform(click());
        onView(withText(R.string.settings_count_expired_title)).perform(click());
        onView(withId(R.id.nav_recipes)).perform(click());
        onView(isRoot()).perform(ListWait.until(MatchToggleTest.suggested()));

        onView(withId(R.id.nav_settings)).perform(click());
        onView(withText(R.string.settings_count_expired_title)).perform(click());
        onView(withId(R.id.nav_recipes)).perform(click());
        onView(isRoot()).perform(ListWait.untilGone(MatchToggleTest.suggested()));
    }
}
