package com.btk.spm.data.db;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every value the converters handle, through SQLite and back (Issue 12): each of the eight units,
 * an expiry date and none, and a ten-step recipe with awkward text, in order. {@code ConvertersTest}
 * proves the converters on the JVM; this proves Room calls them for every column on a device.
 */
@RunWith(AndroidJUnit4.class)
public class EntityRoundTripTest {

    private static final long CREATED = 1_790_000_000_000L;

    // Room computes a LiveData and refreshes it after a write on a background thread. Without this
    // rule, a refresh started by a write could still be running when @After closed the database,
    // and its "connection pool has been closed" exception killed the whole test process. The rule
    // runs that work on the calling thread, so it has finished before the database closes.
    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private AppDatabase database;

    @Before
    public void openInMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void aPantryItemInEachOfTheEightUnits_readsBackEqual() {
        PantryItemDao dao = database.pantryItemDao();
        assertEquals("a unit was added: cover it here", 8, Unit.values().length);
        for (Unit unit : Unit.values()) {
            PantryItem item = new PantryItem("item in " + unit.name(), 1.5, unit, LocalDate.of(2026, 11, 1), CREATED);

            long id = dao.insert(item);

            assertEquals(unit.name(), withId(item, id), dao.getByIdSync(id));
        }
    }

    @Test
    public void bothExpiryStates_readBackEqual() {
        PantryItemDao dao = database.pantryItemDao();
        PantryItem expires = new PantryItem("milk", 1, Unit.L, LocalDate.of(1969, 12, 31), CREATED);
        PantryItem neverExpires = new PantryItem("salt", 1, Unit.KG, null, CREATED);

        long expiresId = dao.insert(expires);
        long neverId = dao.insert(neverExpires);

        assertEquals(withId(expires, expiresId), dao.getByIdSync(expiresId));
        assertEquals(withId(neverExpires, neverId), dao.getByIdSync(neverId));
    }

    @Test
    public void aTenStepRecipe_readsBackInOrder() {
        List<String> steps = Arrays.asList(
                "Weigh the flour, sugar and salt.",
                "Say \"stop\" when the butter foams, not before.",
                "Beat in the eggs, one at a time.",
                "",
                "Line one\nline two",
                "Fold in 200 g of oats, gently.",
                "Back\\slash and 'single' quotes.",
                "Bake at 180°C for 25 minutes.",
                "Crème fraîche on the side.",
                "Serve.");
        assertEquals(10, steps.size());
        RecipeDao dao = database.recipeDao();

        long id = dao.insertWithIngredients(new Recipe("Ten-step cake", 8, steps),
                Arrays.asList(new RecipeIngredient("flour", 200, Unit.G), new RecipeIngredient("egg", 2, Unit.PCS)));

        assertEquals(steps, dao.getAllWithIngredientsSync().get(0).getRecipe().getSteps());
        assertEquals(id, dao.getAllWithIngredientsSync().get(0).getRecipe().getId());
    }

    @Test
    public void aRecipeWithIngredients_readThroughObserveById_equalsWhatWasInserted() throws InterruptedException {
        RecipeDao dao = database.recipeDao();
        Recipe recipe = new Recipe("Cheese omelette", 1, Arrays.asList("Whisk.", "Cook.", "Fold."));
        List<RecipeIngredient> ingredients = Arrays.asList(
                new RecipeIngredient("egg", 3, Unit.PCS),
                new RecipeIngredient("cheese", 30, Unit.G),
                new RecipeIngredient("milk", 2, Unit.TBSP));

        long id = dao.insertWithIngredients(recipe, ingredients);
        RecipeWithIngredients read = getOrAwaitValue(dao.observeById(id));

        // What was inserted, with the ids the database gave it
        List<RecipeIngredient> expected = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            RecipeIngredient sent = ingredients.get(i);
            expected.add(new RecipeIngredient(read.getIngredients().get(i).getId(), id,
                    sent.getName(), sent.getQuantity(), sent.getUnit()));
        }
        assertEquals(new RecipeWithIngredients(new Recipe(id, recipe.getName(), recipe.getServings(), recipe.getSteps()),
                expected), read);
    }

    private static PantryItem withId(PantryItem item, long id) {
        return new PantryItem(id, item.getName(), item.getQuantity(), item.getUnit(),
                item.getExpiryDate(), item.getCreatedAt());
    }
}
