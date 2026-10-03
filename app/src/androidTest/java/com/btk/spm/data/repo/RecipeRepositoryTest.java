package com.btk.spm.data.repo;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.db.RecipeDao;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link RecipeRepository} over an in-memory database (Issue 10): what it returns is exactly what the
 * DAO returns. Recipes are written here through the DAO, as the seeder will, because the repository
 * has no write method.
 */
@RunWith(AndroidJUnit4.class)
public class RecipeRepositoryTest {

    private AppDatabase database;
    private RecipeDao dao;
    private RecipeRepository repository;

    @Before
    public void openInMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        dao = database.recipeDao();
        repository = new RecipeRepository(database);
        dao.insertWithIngredients(new Recipe("Cheese omelette", 1, Arrays.asList("Whisk.", "Cook.")),
                Arrays.asList(new RecipeIngredient("egg", 3, Unit.PCS), new RecipeIngredient("cheddar", 30, Unit.G)));
        dao.insertWithIngredients(new Recipe("Buttered toast", null, Collections.singletonList("Toast and butter.")),
                Collections.singletonList(new RecipeIngredient("bread", 2, Unit.PCS)));
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void observeAllWithIngredients_matchesTheDao() throws InterruptedException {
        List<RecipeWithIngredients> recipes = getOrAwaitValue(repository.observeAllWithIngredients());

        assertEquals(dao.getAllWithIngredientsSync(), recipes);
        assertEquals(2, recipes.size());
    }

    @Test
    public void observeById_matchesTheDao_andIsNullForAnUnknownId() throws InterruptedException {
        RecipeWithIngredients first = dao.getAllWithIngredientsSync().get(0);

        assertEquals(first, getOrAwaitValue(repository.observeById(first.getRecipe().getId())));
        assertNull(getOrAwaitValue(repository.observeById(9_999)));
    }

    @Test
    public void count_matchesTheDao() {
        assertEquals(dao.count(), repository.count());
        assertEquals(2, repository.count());
    }
}
