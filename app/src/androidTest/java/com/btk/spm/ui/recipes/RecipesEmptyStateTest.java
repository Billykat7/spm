package com.btk.spm.ui.recipes;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.ui.MainActivity;
import com.btk.spm.ui.Tab;
import com.btk.spm.ui.pantry.PantryFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Recipes tab's empty states on a device (Issue 24), against an in-memory database put in place of
 * the app's own through {@link SpmApplication#replaceDatabaseForTesting}, so the user's pantry and
 * recipes are never touched.
 *
 * <p>With no recipes at all, the seed having "failed", the tab says the collection did not load and
 * offers no button, even though the pantry is empty too. With the twenty recipes and an empty pantry,
 * it says the pantry is empty, and "Add ingredients" switches the same {@link MainActivity} to the
 * Pantry tab through {@code onNewIntent}: no second host is created. Every wait is for the state or
 * the lifecycle event itself, never a fixed sleep.
 */
@RunWith(AndroidJUnit4.class)
public class RecipesEmptyStateTest {

    private static final long TIMEOUT_S = 5;

    private final SpmApplication app = ApplicationProvider.getApplicationContext();

    private AppDatabase inMemory;
    private AppDatabase original;

    @Before
    public void useAnEmptyInMemoryDatabase() throws InterruptedException {
        // The app's own seed must be done first: the tab waits for it, and it must not land in this database
        awaitSeedDone();
        inMemory = Room.inMemoryDatabaseBuilder(app, AppDatabase.class).build();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> original = app.replaceDatabaseForTesting(inMemory));
    }

    @After
    public void putTheAppsDatabaseBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.replaceDatabaseForTesting(original));
        inMemory.close();
    }

    @Test
    public void noRecipes_saysTheCollectionDidNotLoad_withNoButton_evenWithAnEmptyPantry() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.intentFor(app, Tab.RECIPES))) {
            assertEquals(EmptyReason.NO_RECIPES, awaitEmptyReason(scenario));

            Espresso.onIdle();
            scenario.onActivity(activity -> {
                TextView message = activity.findViewById(R.id.state_headline);
                assertEquals(activity.getString(R.string.recipes_error_title), message.getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.state_message).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.state_action).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.recipe_list).getVisibility());
            });
        }
    }

    @Test
    public void pantryEmpty_addIngredients_switchesTheSameHostToThePantryTab() throws InterruptedException {
        new RecipeSeeder(app, inMemory).seedIfEmpty(); // the twenty recipes, and still no pantry
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.intentFor(app, Tab.RECIPES))) {
            assertEquals(EmptyReason.PANTRY_EMPTY, awaitEmptyReason(scenario));
            Espresso.onIdle();
            AtomicReference<Activity> host = new AtomicReference<>();
            scenario.onActivity(activity -> {
                host.set(activity);
                TextView message = activity.findViewById(R.id.state_headline);
                assertEquals(activity.getString(R.string.recipes_empty_pantry_empty), message.getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.state_action).getVisibility());
            });

            awaitPauseAndResume(() -> onView(withId(R.id.state_action)).perform(click()));

            Espresso.onIdle();
            scenario.onActivity(activity -> {
                assertSame("a second MainActivity was created", host.get(), activity);
                BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                assertEquals(R.id.nav_pantry, nav.getSelectedItemId());
                Fragment shown = activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                assertTrue("expected the Pantry tab, found " + shown, shown instanceof PantryFragment);
            });
            assertEquals("MainActivity instances alive", 1, mainActivities().size());
        }
    }

    /** Waits until the Recipes tab's ViewModel posts an empty state, and returns its reason. */
    private static EmptyReason awaitEmptyReason(ActivityScenario<MainActivity> scenario) throws InterruptedException {
        Espresso.onIdle(); // the tab's Fragment is attached
        CountDownLatch posted = new CountDownLatch(1);
        AtomicReference<EmptyReason> reason = new AtomicReference<>();
        AtomicReference<Runnable> stopObserving = new AtomicReference<>();
        scenario.onActivity(activity -> {
            Fragment recipes = activity.getSupportFragmentManager().findFragmentByTag(Tab.RECIPES.name());
            assertNotNull("The Recipes tab is not shown", recipes);
            // The Fragment created it, so the store hands back that same instance
            SuggestedRecipesViewModel viewModel = new ViewModelProvider(recipes).get(SuggestedRecipesViewModel.class);
            Observer<UiState> observer = state -> {
                if (state instanceof UiState.Empty empty) {
                    reason.set(empty.reason());
                    posted.countDown();
                }
            };
            viewModel.getState().observeForever(observer);
            stopObserving.set(() -> viewModel.getState().removeObserver(observer));
        });
        try {
            assertTrue("No empty state was posted", posted.await(TIMEOUT_S, TimeUnit.SECONDS));
            return reason.get();
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(stopObserving.get());
        }
    }

    private void awaitSeedDone() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Observer<Boolean> observer = finished -> {
            if (Boolean.TRUE.equals(finished)) {
                done.countDown();
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.getRecipeSeedDone().observeForever(observer));
        try {
            assertTrue("The app's recipe seed did not finish", done.await(TIMEOUT_S, TimeUnit.SECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.getRecipeSeedDone().removeObserver(observer));
        }
    }

    /**
     * Runs {@code action} and waits until a {@link MainActivity} has been paused and resumed again, which
     * is when a new Intent for the running host has been delivered to {@code onNewIntent}.
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
                    resumedAgain.await(TIMEOUT_S, TimeUnit.SECONDS));
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);
        }
    }

    /** Every {@link MainActivity} that has been created and not destroyed. */
    private static List<Activity> mainActivities() {
        List<Activity> alive = new ArrayList<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Stage stage : List.of(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)) {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) {
                    if (activity instanceof MainActivity) {
                        alive.add(activity);
                    }
                }
            }
        });
        return alive;
    }
}
