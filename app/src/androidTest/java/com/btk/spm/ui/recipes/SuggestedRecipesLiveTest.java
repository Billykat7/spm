package com.btk.spm.ui.recipes;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.PantryItemDao;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.testing.MatcherIdlingResource;
import com.btk.spm.ui.MainActivity;
import com.btk.spm.ui.Tab;
import com.btk.spm.util.QuantityFormatter;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * The video's part 2 clip on a device (brief §5.1, Issue 26): with the Recipes tab on screen the whole
 * time, the fifth ingredient of Tomato pasta, inserted through {@code PantryRepository}, moves it from
 * the "Almost there" section into the suggestions, and deleting it moves it back, with no navigation
 * and no restart. Since Issue 27 the four-of-five state shows the brief's zero-match sentence, the
 * heading and the recipe's card saying what is missing; with the fifth, the heading is gone.
 *
 * <p>The pantry is emptied in {@link #emptyThePantry()} and what was in it is put back afterwards, ids
 * and all. Three waits, none of them a sleep: Espresso waits for the matcher through a
 * {@link MatcherIdlingResource} over the ViewModel's pending matches; the test waits for the state the
 * ViewModel posts; and, because {@code ListAdapter} works out a change on a background thread no idling
 * check covers, it waits for the list's own change notice before looking at a row.
 *
 * <p>"Not shown" means no displayed view has the recipe's name, not that no view has it: when the list
 * empties, the Fragment hides the {@code RecyclerView}, and a hidden view gets no layout pass, so the
 * removed row's view can stay attached, invisible, until the list is shown again.
 */
@RunWith(AndroidJUnit4.class)
public class SuggestedRecipesLiveTest {

    private static final long TIMEOUT_S = 5;

    /** The longest a row may take to leave the screen once its list has dropped it. */
    private static final long GONE_WITHIN_MS = 2_000;
    private static final String RECIPE = "Tomato pasta";

    /** Marks the rows this test inserts. */
    private static final long TEST_CREATED_AT = 1_234_567_890_456L;

    private final SpmApplication app = ApplicationProvider.getApplicationContext();
    private final PantryItemDao dao = app.getDatabase().pantryItemDao();
    private final List<PantryItem> putBack = new ArrayList<>();

    private RecipeWithIngredients tomatoPasta;
    private MatcherIdlingResource matcherIdle;

    @Before
    public void emptyThePantry() throws InterruptedException {
        List<RecipeWithIngredients> recipes = awaitValue(app.getRecipeRepository().observeAllWithIngredients(),
                list -> list != null && !list.isEmpty());
        tomatoPasta = recipes.stream()
                .filter(r -> r.getRecipe().getName().equals(RECIPE))
                .findFirst()
                .orElseThrow(() -> new AssertionError(RECIPE + " is not in the seed"));
        // A test may touch the DAO (DaoBoundaryTest scans app/src/main only); the app has no deleteAll
        for (PantryItem item : dao.getAllSync()) {
            putBack.add(item);
            dao.delete(item);
        }
    }

    @After
    public void putThePantryBack() {
        if (matcherIdle != null) {
            IdlingRegistry.getInstance().unregister(matcherIdle);
        }
        for (PantryItem item : dao.getAllSync()) {
            if (item.getCreatedAt() == TEST_CREATED_AT) {
                dao.delete(item);
            }
        }
        for (PantryItem item : putBack) {
            dao.insert(item); // the same object, id included, so the same row comes back
        }
    }

    @Test
    public void theFifthIngredient_makesTheRowAppear_andDeletingItMakesItGo() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.intentFor(app, Tab.RECIPES))) {
            Espresso.onIdle();
            SuggestedRecipesViewModel viewModel = recipesViewModel(scenario);
            matcherIdle = new MatcherIdlingResource(viewModel::pendingMatches);
            IdlingRegistry.getInstance().register(matcherIdle);
            AtomicReference<Activity> host = new AtomicReference<>();
            scenario.onActivity(host::set);

            List<RecipeIngredient> lines = tomatoPasta.getIngredients();
            RecipeIngredient fifth = lines.get(lines.size() - 1);
            String heading = app.getString(R.string.almost_there_heading);
            String sentence = app.getString(R.string.recipes_empty_no_match);
            String missingFifth = app.getString(R.string.almost_missing,
                    QuantityFormatter.format(app, fifth.getQuantity(), fifth.getUnit()), fifth.getName());

            // Four of five: nothing suggested, the brief's sentence, then Tomato pasta under the heading
            CountDownLatch cardIn = onNextChange(scenario, AlmostThereAdapter.class);
            for (RecipeIngredient line : lines.subList(0, lines.size() - 1)) {
                insert(line);
            }
            awaitState(viewModel, state -> state instanceof UiState.Content content
                    && content.canMake().isEmpty() && content.almostThere().stream().anyMatch(this::isTomatoPasta));
            assertTrue("The almost-there card was not added", cardIn.await(TIMEOUT_S, TimeUnit.SECONDS));
            // The sentence as the list's first row, not the full-screen state layout: that one is
            // hidden here, and may still hold the same words from a moment when nothing was close
            onView(allOf(withId(R.id.message), withText(sentence))).check(matches(isDisplayed()));
            onView(withId(R.id.state_message)).check(matches(not(isDisplayed())));
            onView(withText(heading)).check(matches(isDisplayed()));
            onView(withText(RECIPE)).check(matches(isDisplayed()));
            onView(withText(missingFifth)).check(matches(isDisplayed()));

            // The fifth: Tomato pasta is a suggestion, and the heading and its card are gone
            String haveAll = app.getResources().getQuantityString(R.plurals.recipe_have_all_ingredients,
                    lines.size(), lines.size());
            CountDownLatch rowIn = onNextChange(scenario, RecipeAdapter.class);
            CountDownLatch cardOut = onNextChange(scenario, AlmostThereAdapter.class);
            insert(fifth);
            awaitState(viewModel, state -> state instanceof UiState.Content content
                    && content.canMake().stream().anyMatch(this::isTomatoPasta));
            // Each section diffs on its own, so wait for both: the row in, and the card out
            assertTrue("The row was not added to the suggestions", rowIn.await(TIMEOUT_S, TimeUnit.SECONDS));
            assertTrue("The card was not removed from Almost there", cardOut.await(TIMEOUT_S, TimeUnit.SECONDS));
            onView(allOf(withText(RECIPE), hasSibling(withText(haveAll)))).check(matches(isDisplayed()));
            awaitGone(withText(heading));
            awaitGone(withText(missingFifth));

            // The fifth deleted: out of the suggestions, back under the heading
            CountDownLatch rowOut = onNextChange(scenario, RecipeAdapter.class);
            CountDownLatch cardBack = onNextChange(scenario, AlmostThereAdapter.class);
            deleteTheTestRow(fifth);
            awaitState(viewModel, state -> state instanceof UiState.Content content
                    && content.canMake().isEmpty() && content.almostThere().stream().anyMatch(this::isTomatoPasta));
            assertTrue("The row was not removed from the suggestions", rowOut.await(TIMEOUT_S, TimeUnit.SECONDS));
            assertTrue("The card did not come back", cardBack.await(TIMEOUT_S, TimeUnit.SECONDS));
            awaitGone(withText(haveAll));
            onView(withText(heading)).check(matches(isDisplayed()));
            onView(withText(missingFifth)).check(matches(isDisplayed()));

            scenario.onActivity(activity -> assertSame("The screen was opened again", host.get(), activity));
        }
    }

    private boolean isTomatoPasta(MatchedRecipe row) {
        return row.recipeId() == tomatoPasta.getRecipe().getId();
    }

    /** Adds a pantry row holding exactly what {@code line} needs, through the repository, as the form does. */
    private void insert(RecipeIngredient line) {
        app.getPantryRepository().insert(new PantryItem(line.getName(), line.getQuantity(), line.getUnit(), null,
                TEST_CREATED_AT));
    }

    /** Deletes the row this test inserted for {@code line}, through the repository. */
    private void deleteTheTestRow(RecipeIngredient line) {
        for (PantryItem item : dao.getAllSync()) {
            if (item.getCreatedAt() == TEST_CREATED_AT && item.getName().equals(line.getName())) {
                app.getPantryRepository().delete(item);
                return;
            }
        }
        throw new AssertionError("The test's " + line.getName() + " row is not in the pantry");
    }

    /** The Recipes tab's ViewModel: the Fragment created it, so the store hands back that instance. */
    private static SuggestedRecipesViewModel recipesViewModel(ActivityScenario<MainActivity> scenario) {
        AtomicReference<SuggestedRecipesViewModel> viewModel = new AtomicReference<>();
        scenario.onActivity(activity -> {
            Fragment recipes = activity.getSupportFragmentManager().findFragmentByTag(Tab.RECIPES.name());
            assertNotNull("The Recipes tab is not shown", recipes);
            viewModel.set(new ViewModelProvider(recipes).get(SuggestedRecipesViewModel.class));
        });
        return viewModel.get();
    }

    /**
     * A latch released the next time the section adapter of {@code type} inside the list's
     * {@code ConcatAdapter} adds, removes or changes a row. One section, not the whole list: the header
     * rows change at once, while a section's rows arrive after {@code ListAdapter}'s background diff.
     */
    private static CountDownLatch onNextChange(ActivityScenario<MainActivity> scenario,
                                               Class<? extends RecyclerView.Adapter<?>> type) {
        CountDownLatch changed = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.recipe_list);
            ConcatAdapter all = (ConcatAdapter) list.getAdapter();
            RecyclerView.Adapter<?> section = all.getAdapters().stream()
                    .filter(type::isInstance)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " in the list"));
            section.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    changed.countDown();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    changed.countDown();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    changed.countDown();
                }
            });
        });
        return changed;
    }

    /**
     * Waits, at most {@link #GONE_WITHIN_MS}, until no displayed view matches {@code view}. A removed
     * row is animated out after the list has been told, and Espresso does not wait for item
     * animations, so this is the bounded poll the issue allows as the fallback; every attempt first
     * waits for the main thread to be idle, so it never spins.
     */
    private static void awaitGone(Matcher<View> view) {
        long deadline = SystemClock.uptimeMillis() + GONE_WITHIN_MS;
        while (true) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                onView(allOf(view, isDisplayed())).check(doesNotExist());
                return;
            } catch (AssertionError stillShown) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw stillShown;
                }
            }
        }
    }

    private static void awaitState(SuggestedRecipesViewModel viewModel, Predicate<UiState> test)
            throws InterruptedException {
        awaitValue(viewModel.getState(), test);
    }

    /** Observes {@code data} on the main thread until a value passes {@code test}, and returns that value. */
    private static <T> T awaitValue(LiveData<T> data, Predicate<T> test) throws InterruptedException {
        CountDownLatch seen = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        Observer<T> observer = v -> {
            if (test.test(v)) {
                value.set(v);
                seen.countDown();
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> data.observeForever(observer));
        try {
            assertTrue("No matching value within " + TIMEOUT_S + " s", seen.await(TIMEOUT_S, TimeUnit.SECONDS));
            return value.get();
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> data.removeObserver(observer));
        }
    }
}
