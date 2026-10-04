package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.accessibility.AccessibilityChecks;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.domain.Unit;
import com.btk.spm.testing.MatcherIdlingResource;
import com.btk.spm.ui.pantry.AddEditIngredientActivity;
import com.btk.spm.ui.recipes.RecipeDetailActivity;
import com.btk.spm.ui.recipes.SuggestedRecipesViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every screen and state with the Accessibility Test Framework on (Issue 30): the checks Accessibility
 * Scanner runs, here run by Espresso on the whole window at each action, and failing the test on any
 * error. Touch targets under 48 dp, an image or a button with no label, two controls with the same
 * label, text too faint for its background: each is a failure naming the view.
 *
 * <p>An in-memory database with the twenty recipes and a small pantry replaces the app's own, so the
 * screens show real rows and the user's data is never touched.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityChecksTest {

    private static final long TIMEOUT_S = 5;

    private final SpmApplication app = ApplicationProvider.getApplicationContext();

    private AppDatabase inMemory;
    private AppDatabase original;
    private MatcherIdlingResource matcherIdle;

    /** Once for the class: from here on every ViewAction runs the checks over the whole window. */
    @BeforeClass
    public static void enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true);
    }

    @Before
    public void useAPantryOfFiveAndTheSeed() throws InterruptedException {
        awaitSeedDone();
        inMemory = Room.inMemoryDatabaseBuilder(app, AppDatabase.class).build();
        new RecipeSeeder(app, inMemory).seedIfEmpty();
        LocalDate today = LocalDate.now();
        // Tomato pasta's ingredients without garlic: an almost-there card under the zero-match row,
        // and an expired milk for the error-coloured badge
        insert("pasta", 500, Unit.G, null);
        insert("tomatoes", 6, Unit.PCS, today.plusDays(2));
        insert("olive oil", 0.5, Unit.L, null);
        insert("salt", 1, Unit.KG, null);
        insert("milk", 1, Unit.L, today.minusDays(1));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> original = app.replaceDatabaseForTesting(inMemory));
    }

    @After
    public void putTheAppsDatabaseBack() {
        if (matcherIdle != null) {
            IdlingRegistry.getInstance().unregister(matcherIdle);
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.replaceDatabaseForTesting(original));
        inMemory.close();
    }

    @Test
    public void thePantryList_theSortMenu_andTheDeleteDialog() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.intentFor(app, Tab.PANTRY))) {
            onView(withText("tomatoes")).check(matches(isDisplayed()));
            onView(withId(R.id.action_sort)).perform(click());
            onView(withText(R.string.sort_name)).perform(click());
            onView(withText("salt")).perform(click());   // opens the edit form
        }
    }

    @Test
    public void theEmptyPantry() {
        for (PantryItem item : inMemory.pantryItemDao().getAllSync()) {
            inMemory.pantryItemDao().deleteById(item.getId());
        }
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.intentFor(app, Tab.PANTRY))) {
            onView(withId(R.id.state_action)).check(matches(isDisplayed())).perform(closeSoftKeyboard());
        }
    }

    @Test
    public void theAddForm_withEveryErrorShowing() {
        try (ActivityScenario<AddEditIngredientActivity> ignored =
                     ActivityScenario.launch(AddEditIngredientActivity.intentForAdd(app))) {
            onView(withId(R.id.action_save)).perform(click());
            onView(withId(R.id.name_input)).perform(closeSoftKeyboard());
        }
    }

    @Test
    public void theRecipesTab_withAlmostThere_thenTheSettingsTab() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.intentFor(app, Tab.RECIPES))) {
            waitForTheMatcher(scenario);
            onView(withText(R.string.almost_there_heading)).check(matches(isDisplayed()));
            onView(withText("Tomato pasta")).perform(click());   // opens the detail screen
        }
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.intentFor(app, Tab.SETTINGS))) {
            onView(withText(R.string.settings_units_title)).perform(click());
            onView(withText(R.string.action_cancel)).perform(click());
        }
    }

    @Test
    public void theRecipeDetailScreen() {
        long pasta = recipeId("Tomato pasta");
        try (ActivityScenario<RecipeDetailActivity> ignored =
                     ActivityScenario.launch(RecipeDetailActivity.intentFor(app, pasta))) {
            onView(withText(R.string.recipe_method)).check(matches(isDisplayed()));
            onView(withId(R.id.detail_list)).perform(closeSoftKeyboard());
        }
    }

    private void insert(String name, double quantity, Unit unit, LocalDate expiry) {
        inMemory.pantryItemDao().insert(new PantryItem(name, quantity, unit, expiry, System.currentTimeMillis()));
    }

    private long recipeId(String name) {
        return inMemory.recipeDao().getAllWithIngredientsSync().stream()
                .filter(r -> r.getRecipe().getName().equals(name)).findFirst().orElseThrow()
                .getRecipe().getId();
    }

    /** Registers the Recipes tab's pending matches with Espresso, so every check waits for the list. */
    private void waitForTheMatcher(ActivityScenario<MainActivity> scenario) {
        AtomicReference<SuggestedRecipesViewModel> viewModel = new AtomicReference<>();
        scenario.onActivity(activity -> {
            Fragment recipes = activity.getSupportFragmentManager().findFragmentByTag(Tab.RECIPES.name());
            viewModel.set(new ViewModelProvider(recipes).get(SuggestedRecipesViewModel.class));
        });
        matcherIdle = new MatcherIdlingResource(viewModel.get()::pendingMatches);
        IdlingRegistry.getInstance().register(matcherIdle);
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
}
