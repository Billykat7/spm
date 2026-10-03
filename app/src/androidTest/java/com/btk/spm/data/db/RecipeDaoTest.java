package com.btk.spm.data.db;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The recipe tables on a real SQLite database in memory (Issue 10): a recipe and its ingredients go
 * in as one transaction and come back together through {@code @Relation}, the foreign key and its
 * cascade hold, and an unknown id is {@code null}, not an error.
 *
 * <p>{@link InstantTaskExecutorRule} and {@code allowMainThreadQueries()} make Room's {@code LiveData}
 * work run on the calling thread, so an emission has arrived by the time a write returns.
 */
@RunWith(AndroidJUnit4.class)
public class RecipeDaoTest {

    private static final List<String> OMELETTE_STEPS = Arrays.asList(
            "Whisk the eggs with a pinch of salt.",
            "Melt the butter in a pan, then pour in the eggs.",
            "Scatter the cheese over, fold, and serve.");

    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private AppDatabase database;
    private RecipeDao dao;

    @Before
    public void openInMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.recipeDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    // Insert as one transaction

    @Test
    public void insertWithIngredients_stampsTheRecipeIdOnEveryIngredient() {
        long id = dao.insertWithIngredients(omelette(), omeletteIngredients());

        assertTrue(id > 0);
        assertEquals(4, dao.countIngredients());
        for (RecipeIngredient ingredient : dao.getAllWithIngredientsSync().get(0).getIngredients()) {
            assertEquals(id, ingredient.getRecipeId());
        }
    }

    @Test
    public void insertWithIngredients_isAllOrNothing_whenAnIngredientFails() {
        // A null name breaks the NOT NULL constraint on the third row, after the recipe row went in
        List<RecipeIngredient> broken = new ArrayList<>(omeletteIngredients());
        broken.set(2, new RecipeIngredient(null, 30, Unit.G));

        assertThrows(SQLiteConstraintException.class, () -> dao.insertWithIngredients(omelette(), broken));

        assertEquals("the recipe row should have rolled back", 0, dao.count());
        assertEquals(0, dao.countIngredients());
    }

    // The relation

    @Test
    public void observeAllWithIngredients_returnsTheRecipeWithItsFourIngredientsInInsertionOrder()
            throws InterruptedException {
        long id = dao.insertWithIngredients(omelette(), omeletteIngredients());

        List<RecipeWithIngredients> recipes = getOrAwaitValue(dao.observeAllWithIngredients());

        assertEquals(1, recipes.size());
        RecipeWithIngredients read = recipes.get(0);
        assertEquals(id, read.getRecipe().getId());
        assertEquals(Arrays.asList("egg", "butter", "cheddar", "salt"), names(read.getIngredients()));
        long previousId = 0;
        for (RecipeIngredient ingredient : read.getIngredients()) {
            assertEquals(id, ingredient.getRecipeId());
            assertTrue("ingredients should come back in id order", ingredient.getId() > previousId);
            previousId = ingredient.getId();
        }
    }

    @Test
    public void observeAllWithIngredients_keepsEachRecipesIngredientsApart() throws InterruptedException {
        long omelette = dao.insertWithIngredients(omelette(), omeletteIngredients());
        long toast = dao.insertWithIngredients(new Recipe("Buttered toast", 1, Collections.singletonList("Toast and butter.")),
                Arrays.asList(new RecipeIngredient("bread", 2, Unit.PCS), new RecipeIngredient("butter", 10, Unit.G)));

        List<RecipeWithIngredients> recipes = getOrAwaitValue(dao.observeAllWithIngredients());

        assertEquals(toast, recipes.get(0).getRecipe().getId());
        assertEquals(Arrays.asList("bread", "butter"), names(recipes.get(0).getIngredients()));
        assertEquals(omelette, recipes.get(1).getRecipe().getId());
        assertEquals(4, recipes.get(1).getIngredients().size());
    }

    @Test
    public void observeAllWithIngredients_ordersByNameIgnoringCase() throws InterruptedException {
        dao.insertWithIngredients(new Recipe("pancakes", 4, OMELETTE_STEPS), omeletteIngredients());
        dao.insertWithIngredients(new Recipe("Cheese omelette", 1, OMELETTE_STEPS), omeletteIngredients());
        dao.insertWithIngredients(new Recipe("apple crumble", 6, OMELETTE_STEPS), omeletteIngredients());

        List<String> names = new ArrayList<>();
        for (RecipeWithIngredients recipe : getOrAwaitValue(dao.observeAllWithIngredients())) {
            names.add(recipe.getRecipe().getName());
        }
        assertEquals(Arrays.asList("apple crumble", "Cheese omelette", "pancakes"), names);
    }

    @Test
    public void observeAllWithIngredients_emitsAgainAfterAnInsert() {
        List<List<RecipeWithIngredients>> emissions = new ArrayList<>();
        LiveData<List<RecipeWithIngredients>> recipes = dao.observeAllWithIngredients();
        Observer<List<RecipeWithIngredients>> recorder = emissions::add;
        recipes.observeForever(recorder);
        try {
            assertEquals(1, emissions.size());
            assertTrue(emissions.get(0).isEmpty());

            dao.insertWithIngredients(omelette(), omeletteIngredients());

            assertEquals("one emission for the whole transaction", 2, emissions.size());
            assertEquals(4, emissions.get(1).get(0).getIngredients().size());
        } finally {
            recipes.removeObserver(recorder);
        }
    }

    @Test
    public void observeById_returnsTheRecipeWithItsIngredientsAndStepsInOrder() throws InterruptedException {
        long id = dao.insertWithIngredients(omelette(), omeletteIngredients());

        RecipeWithIngredients read = getOrAwaitValue(dao.observeById(id));

        assertNotNull(read);
        assertEquals("Cheese omelette", read.getRecipe().getName());
        assertEquals(Integer.valueOf(1), read.getRecipe().getServings());
        assertEquals(OMELETTE_STEPS, read.getRecipe().getSteps());
        assertEquals(Arrays.asList("egg", "butter", "cheddar", "salt"), names(read.getIngredients()));
    }

    @Test
    public void observeById_ofAnUnknownId_emitsNull() throws InterruptedException {
        dao.insertWithIngredients(omelette(), omeletteIngredients());

        assertNull(getOrAwaitValue(dao.observeById(9_999)));
    }

    @Test
    public void aRecipeWithNoServingsAndNoIngredients_readsBackEmptyNotNull() throws InterruptedException {
        long id = dao.insertWithIngredients(new Recipe("Glass of water", null, Collections.singletonList("Pour.")),
                Collections.emptyList());

        RecipeWithIngredients read = getOrAwaitValue(dao.observeById(id));

        assertNull(read.getRecipe().getServings());
        assertNotNull(read.getIngredients());
        assertTrue(read.getIngredients().isEmpty());
    }

    // The foreign key and its cascade

    @Test
    public void deletingARecipe_deletesItsIngredients() {
        dao.insertWithIngredients(omelette(), omeletteIngredients());
        assertEquals(4, dao.countIngredients());

        dao.deleteAll();

        assertEquals(0, dao.count());
        assertEquals("ON DELETE CASCADE should have removed the ingredients", 0, dao.countIngredients());
    }

    @Test
    public void deletingOneRecipe_deletesOnlyItsOwnIngredients() {
        long omelette = dao.insertWithIngredients(omelette(), omeletteIngredients());
        dao.insertWithIngredients(new Recipe("Buttered toast", 1, Collections.singletonList("Toast and butter.")),
                Arrays.asList(new RecipeIngredient("bread", 2, Unit.PCS), new RecipeIngredient("butter", 10, Unit.G)));

        // The app never deletes a single recipe (decision 7), so the row goes with plain SQL
        database.getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM recipes WHERE id = ?", new Object[] {omelette});

        assertEquals(1, dao.count());
        assertEquals(2, dao.countIngredients());
        assertEquals("Buttered toast", dao.getAllWithIngredientsSync().get(0).getRecipe().getName());
    }

    @Test
    public void anIngredientForAMissingRecipe_isRejected() {
        dao.insertWithIngredients(omelette(), omeletteIngredients());
        RecipeIngredient orphan = new RecipeIngredient("flour", 200, Unit.G).withRecipeId(12_345);

        assertThrows(SQLiteConstraintException.class,
                () -> dao.insertIngredients(Collections.singletonList(orphan)));
        assertEquals(4, dao.countIngredients());
    }

    // Counts

    @Test
    public void count_isZeroWhenEmpty_andTwoAfterTwoInserts() {
        assertEquals(0, dao.count());
        assertEquals(0, dao.countIngredients());

        dao.insertWithIngredients(omelette(), omeletteIngredients());
        dao.insertWithIngredients(new Recipe("Buttered toast", 1, Collections.singletonList("Toast and butter.")),
                Collections.singletonList(new RecipeIngredient("bread", 2, Unit.PCS)));

        assertEquals(2, dao.count());
        assertEquals(5, dao.countIngredients());
    }

    private static Recipe omelette() {
        return new Recipe("Cheese omelette", 1, OMELETTE_STEPS);
    }

    private static List<RecipeIngredient> omeletteIngredients() {
        return Arrays.asList(
                new RecipeIngredient("egg", 3, Unit.PCS),
                new RecipeIngredient("butter", 10, Unit.G),
                new RecipeIngredient("cheddar", 30, Unit.G),
                new RecipeIngredient("salt", 0.25, Unit.TSP));
    }

    private static List<String> names(List<RecipeIngredient> ingredients) {
        List<String> names = new ArrayList<>();
        for (RecipeIngredient ingredient : ingredients) {
            names.add(ingredient.getName());
        }
        return names;
    }
}
