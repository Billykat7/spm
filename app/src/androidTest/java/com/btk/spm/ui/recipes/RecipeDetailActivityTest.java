package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.PantryItemDao;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * The recipe detail screen on a device (Issue 25), against the app's own database and its seeded
 * recipes. It opens Garlic bread through {@link RecipeDetailActivity#intentFor}, and inserts a missing
 * ingredient through {@code PantryRepository} while the screen is open, to watch that row's cross
 * become a check in the same Activity. An id that leads nowhere is {@link RecipeDetailUnknownIdTest}'s.
 *
 * <p>Every wait is for the ViewModel's posted state or the list's own notice, never a fixed
 * sleep. The pantry row the test inserts carries its own creation time and is deleted afterwards; any
 * other row is left alone.
 */
@RunWith(AndroidJUnit4.class)
public class RecipeDetailActivityTest {

    private static final long TIMEOUT_S = 5;

    /** Marks the pantry row this test inserts, so the clean-up deletes that row and no other. */
    private static final long TEST_CREATED_AT = 1_234_567_890_123L;

    private final SpmApplication app = ApplicationProvider.getApplicationContext();
    private final PantryItemDao pantryDao = app.getDatabase().pantryItemDao();

    private RecipeWithIngredients garlicBread;

    @Before
    public void findGarlicBread() throws InterruptedException {
        List<RecipeWithIngredients> recipes = awaitValue(app.getRecipeRepository().observeAllWithIngredients(),
                list -> list != null && !list.isEmpty());
        garlicBread = recipes.stream()
                .filter(r -> r.getRecipe().getName().equals("Garlic bread"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Garlic bread is not in the seed"));
    }

    @After
    public void deleteTheTestsPantryRow() {
        for (PantryItem item : pantryDao.getAllSync()) {
            if (item.getCreatedAt() == TEST_CREATED_AT) {
                pantryDao.deleteById(item.getId());
            }
        }
    }

    @Test
    public void aSeededId_showsTheRecipe_everyIngredient_andEveryStep() throws InterruptedException {
        try (ActivityScenario<RecipeDetailActivity> scenario = ActivityScenario.launch(
                RecipeDetailActivity.intentFor(app, garlicBread.getRecipe().getId()))) {
            awaitLoaded(scenario, loaded -> true);
            Espresso.onIdle();

            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.detail_list);
                int ingredients = garlicBread.getIngredients().size();
                int steps = garlicBread.getRecipe().getSteps().size();
                // header + ingredients + "Method" + steps
                assertEquals(1 + ingredients + 1 + steps, list.getAdapter().getItemCount());
                TextView name = list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.name);
                assertEquals("Garlic bread", name.getText().toString());
            });
        }
    }

    @Test
    public void insertingTheMissingItem_turnsItsCrossIntoACheck_inTheSameActivity() throws InterruptedException {
        try (ActivityScenario<RecipeDetailActivity> scenario = ActivityScenario.launch(
                RecipeDetailActivity.intentFor(app, garlicBread.getRecipe().getId()))) {
            DetailUiState.Loaded before = awaitLoaded(scenario, loaded -> true);
            int index = firstNeed(before);
            assumeTrue("The pantry on this device already covers every line of Garlic bread", index >= 0);
            Espresso.onIdle();
            AtomicReference<Activity> host = new AtomicReference<>();
            // ListAdapter works out the change on a background thread Espresso does not wait for, so
            // wait for the list's own "row changed" notice before looking at the row
            CountDownLatch rowRebound = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                host.set(activity);
                assertEquals(activity.getString(R.string.ingredient_need), markOf(activity, index).getContentDescription());
                RecyclerView list = activity.findViewById(R.id.detail_list);
                list.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount) {
                        if (positionStart <= 1 + index && 1 + index < positionStart + itemCount) {
                            rowRebound.countDown();
                        }
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                        onItemRangeChanged(positionStart, itemCount);
                    }
                });
            });

            // Exactly what the recipe needs of that line, through the repository, as the add form writes
            RecipeIngredient line = garlicBread.getIngredients().get(index);
            app.getPantryRepository().insert(new PantryItem(line.getName(), line.getQuantity(), line.getUnit(),
                    null, TEST_CREATED_AT));

            awaitLoaded(scenario, loaded -> loaded.rows().get(index).have());
            assertTrue("The row was not rebound", rowRebound.await(TIMEOUT_S, TimeUnit.SECONDS));
            Espresso.onIdle();
            scenario.onActivity(activity -> {
                assertSame("The screen was opened again", host.get(), activity);
                assertEquals(activity.getString(R.string.ingredient_have), markOf(activity, index).getContentDescription());
            });
        }
    }

    /** The mark of ingredient {@code index}, which sits after the header in the list. */
    private static ImageView markOf(Activity activity, int index) {
        RecyclerView list = activity.findViewById(R.id.detail_list);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(1 + index);
        assertNotNull("Ingredient row " + index + " is not laid out", holder);
        return holder.itemView.findViewById(R.id.mark);
    }

    /** The first line the matcher said the pantry does not cover, or -1. */
    private static int firstNeed(DetailUiState.Loaded loaded) {
        for (int i = 0; i < loaded.rows().size(); i++) {
            if (!loaded.rows().get(i).have()) {
                return i;
            }
        }
        return -1;
    }

    /** Waits until the screen's ViewModel posts a loaded state that passes {@code test}, and returns it. */
    private static DetailUiState.Loaded awaitLoaded(ActivityScenario<RecipeDetailActivity> scenario,
                                                    Predicate<DetailUiState.Loaded> test) throws InterruptedException {
        AtomicReference<LiveData<DetailUiState>> state = new AtomicReference<>();
        // The Activity created it, so the store hands back that same instance
        scenario.onActivity(activity -> state.set(new ViewModelProvider(activity).get(RecipeDetailViewModel.class).getState()));
        DetailUiState found = awaitValue(state.get(), s -> s instanceof DetailUiState.Loaded loaded && test.test(loaded));
        return (DetailUiState.Loaded) found;
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
