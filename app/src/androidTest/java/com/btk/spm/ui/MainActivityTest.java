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
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.btk.spm.R;
import com.btk.spm.ui.pantry.PantryFragment;
import com.btk.spm.ui.recipes.SuggestedRecipesFragment;
import com.btk.spm.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The navigation shell on a device (Issue 3): each tab shows its Fragment and title, the tab
 * survives the Activity being re-created, Back returns to Pantry, and
 * {@link MainActivity#intentFor} lands on the tab it names.
 *
 * <p>Since Issue 23 the Recipes tab replaces its title with "Suggested recipes (N)" once its first
 * match is posted, from a background thread these tests do not wait for, so either title is the
 * Recipes tab's.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    /** How many recipes the seed holds, so the most the Recipes title can count (Issue 11). */
    private static final int SEEDED_RECIPES = 20;

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
    public void intentForSwitchesARunningHostToTheTab() throws InterruptedException {
        // Launched through intentFor too: ActivityScenario keeps track of its Activity by comparing
        // getIntent() with the Intent it launched (Intent.filterEquals, which ignores extras), and
        // onNewIntent replaces getIntent(). A launcher Intent here would make it lose the Activity.
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.intentFor(context, Tab.PANTRY))) {
            assertShowing(scenario, Tab.PANTRY, PantryFragment.class);
            // Another screen starting the host: it is brought forward and switched (onNewIntent).
            // The Intent goes through the system server, which Espresso's idle check does not see, so
            // wait for the pause, onNewIntent, resume round the host makes, not for the main looper.
            awaitPauseAndResume(() -> scenario.onActivity(
                    activity -> activity.startActivity(MainActivity.intentFor(activity, Tab.RECIPES))));
            assertShowing(scenario, Tab.RECIPES, SuggestedRecipesFragment.class);
        }
    }

    /**
     * Runs {@code action} and waits until a {@link MainActivity} has been paused and then resumed.
     * A new Intent for the running host is delivered between those two callbacks, on API 26 as on
     * API 35, so once this returns {@code onNewIntent} has run.
     */
    private static void awaitPauseAndResume(Runnable action) throws InterruptedException {
        AtomicBoolean paused = new AtomicBoolean();
        CountDownLatch resumedAgain = new CountDownLatch(1);
        ActivityLifecycleCallback callback = (activity, stage) -> {
            if (!(activity instanceof MainActivity)) {
                return;
            }
            if (stage == Stage.PAUSED) {
                paused.set(true);
            } else if (stage == Stage.RESUMED && paused.get()) {
                resumedAgain.countDown();
            }
        };
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);
        try {
            action.run();
            assertTrue("MainActivity was not paused and resumed by the new Intent",
                    resumedAgain.await(5, TimeUnit.SECONDS));
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);
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
            String title = String.valueOf(toolbar.getTitle());
            assertTrue("toolbar title " + title, titlesOf(activity, tab).contains(title));
            assertTrue("expected " + fragmentClass.getSimpleName() + " but found " + shown,
                    fragmentClass.isInstance(shown));
        });
    }

    /** The titles {@code tab} can show: its name, and for Recipes also its count once matched. */
    private static List<String> titlesOf(Context context, Tab tab) {
        List<String> titles = new ArrayList<>(List.of(context.getString(tab.titleRes())));
        if (tab == Tab.RECIPES) {
            for (int n = 0; n <= SEEDED_RECIPES; n++) {
                titles.add(context.getResources().getQuantityString(R.plurals.suggested_recipes_title, n, n));
            }
        }
        return titles;
    }
}
