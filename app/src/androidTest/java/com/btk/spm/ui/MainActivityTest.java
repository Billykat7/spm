package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.R;
import com.btk.spm.ui.pantry.PantryFragment;
import com.btk.spm.ui.recipes.SuggestedRecipesFragment;
import com.btk.spm.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The navigation shell on a device (Issue 3): each tab shows its Fragment and title, the tab
 * survives the Activity being re-created, Back returns to Pantry, and
 * {@link MainActivity#intentFor} lands on the tab it names.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void opensOnPantry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertShowing(scenario, Tab.PANTRY, PantryFragment.class);
        }
    }

    @Test
    public void eachTabShowsItsFragmentAndTitle() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_recipes)).perform(click());
            assertShowing(scenario, Tab.RECIPES, SuggestedRecipesFragment.class);

            onView(withId(R.id.nav_settings)).perform(click());
            assertShowing(scenario, Tab.SETTINGS, SettingsFragment.class);

            onView(withId(R.id.nav_pantry)).perform(click());
            assertShowing(scenario, Tab.PANTRY, PantryFragment.class);
        }
    }

    @Test
    public void selectedTabSurvivesRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_recipes)).perform(click());
            // recreate() destroys and rebuilds the Activity exactly as a rotation does
            scenario.recreate();
            assertShowing(scenario, Tab.RECIPES, SuggestedRecipesFragment.class);
            scenario.onActivity(activity -> assertEquals("the restored Fragment is reused, not duplicated",
                    1, activity.getSupportFragmentManager().getFragments().size()));
        }
    }

    @Test
    public void backFromAnotherTabReturnsToPantry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_settings)).perform(click());
            pressBack();
            assertShowing(scenario, Tab.PANTRY, PantryFragment.class);
        }
    }

    @Test
    public void intentForOpensOnTheTabItNames() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.intentFor(context, Tab.SETTINGS))) {
            assertShowing(scenario, Tab.SETTINGS, SettingsFragment.class);
        }
    }

    @Test
    public void intentForSwitchesARunningHostToTheTab() {
        // Launched through intentFor too: ActivityScenario keeps track of its Activity by comparing
        // getIntent() with the Intent it launched (Intent.filterEquals, which ignores extras), and
        // onNewIntent replaces getIntent(). A launcher Intent here would make it lose the Activity.
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.intentFor(context, Tab.PANTRY))) {
            assertShowing(scenario, Tab.PANTRY, PantryFragment.class);
            // Another screen starting the host: it is brought forward and switched (onNewIntent)
            scenario.onActivity(activity -> activity.startActivity(MainActivity.intentFor(activity, Tab.RECIPES)));
            Espresso.onIdle();
            assertShowing(scenario, Tab.RECIPES, SuggestedRecipesFragment.class);
        }
    }

    private void assertShowing(ActivityScenario<MainActivity> scenario, Tab tab,
            Class<? extends Fragment> fragmentClass) {
        Espresso.onIdle();
        scenario.onActivity(activity -> {
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
            Fragment shown = activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            assertEquals("selected item", tab.menuItemId(), nav.getSelectedItemId());
            assertEquals("toolbar title", activity.getString(tab.titleRes()), String.valueOf(toolbar.getTitle()));
            assertTrue("expected " + fragmentClass.getSimpleName() + " but found " + shown,
                    fragmentClass.isInstance(shown));
        });
    }
}
