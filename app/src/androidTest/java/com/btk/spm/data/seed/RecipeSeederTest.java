package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * {@link RecipeSeeder} on an in-memory database with the real asset from the APK (Issue 11): the
 * first call seeds all twenty, a second call adds none, and a seed that fails part way leaves no
 * recipe behind.
 */
@RunWith(AndroidJUnit4.class)
public class RecipeSeederTest {

    private Context context;
    private AppDatabase database;
    private RecipeDao dao;

    @Before
    public void openInMemoryDatabase() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        dao = database.recipeDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void seedIfEmpty_seedsTwentyOnce_andAddsNoneTheSecondTime() {
        RecipeSeeder seeder = new RecipeSeeder(context, database);

        assertEquals(SeedResult.SEEDED, seeder.seedIfEmpty());
        assertEquals(20, dao.count());

        assertEquals(SeedResult.ALREADY_SEEDED, seeder.seedIfEmpty());
        assertEquals(20, dao.count());
    }

    @Test
    public void aNewSeederOnTheSameDatabase_addsNone() {
        // What the next app start does: a fresh RecipeSeeder, the same database
        new RecipeSeeder(context, database).seedIfEmpty();

        assertEquals(SeedResult.ALREADY_SEEDED, new RecipeSeeder(context, database).seedIfEmpty());
        assertEquals(20, dao.count());
    }

    @Test
    public void everyIngredientInTheAssetIsStored() throws IOException {
        new RecipeSeeder(context, database).seedIfEmpty();

        int inAsset = 0;
        for (RecipeWithIngredients recipe : RecipeJsonParser.parse(readAsset())) {
            inAsset += recipe.getIngredients().size();
        }
        assertEquals(inAsset, dao.countIngredients());
    }

    @Test
    public void cheeseOmelette_readsBackWithItsFourIngredients() {
        new RecipeSeeder(context, database).seedIfEmpty();

        RecipeWithIngredients omelette = null;
        for (RecipeWithIngredients recipe : dao.getAllWithIngredientsSync()) {
            if (recipe.getRecipe().getName().equals("Cheese omelette")) {
                omelette = recipe;
            }
        }
        assertTrue("Cheese omelette was not seeded", omelette != null);
        List<String> names = new ArrayList<>();
        for (RecipeIngredient ingredient : omelette.getIngredients()) {
            names.add(ingredient.getName());
        }
        assertEquals(Arrays.asList("egg", "cheese", "butter", "salt"), names);
        assertEquals(Unit.PCS, omelette.getIngredients().get(0).getUnit());
    }

    @Test
    public void aSeedThatFailsOnTheSeventhRecipe_leavesNoRecipes() {
        // Six good recipes, then one whose ingredient breaks NOT NULL: the inserts of the first six
        // have run when the seventh throws, and the one transaction takes them all back
        List<RecipeWithIngredients> recipes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            recipes.add(recipe("Recipe " + i, new RecipeIngredient("egg", 2, Unit.PCS)));
        }
        recipes.add(recipe("Recipe 7", new RecipeIngredient(null, 2, Unit.PCS)));
        RecipeSeeder seeder = new RecipeSeeder(database, () -> recipes);

        assertThrows(RuntimeException.class, seeder::seedIfEmpty);

        assertEquals("a failed seed must leave 0 recipes, not 6", 0, dao.count());
        assertEquals(0, dao.countIngredients());
    }

    @Test
    public void aSeedThatCannotParse_leavesNoRecipes_andTheNextStartSeeds() {
        RecipeSeeder broken = new RecipeSeeder(database, () -> RecipeJsonParser.parse("[{\"name\": \"Half\"}]"));

        assertThrows(IllegalArgumentException.class, broken::seedIfEmpty);
        assertEquals(0, dao.count());

        // The count check, not a once-only callback, is what lets the next start try again
        assertEquals(SeedResult.SEEDED, new RecipeSeeder(context, database).seedIfEmpty());
        assertEquals(20, dao.count());
    }

    @Test
    public void aTableThatAlreadyHoldsARecipe_isNotSeeded() {
        dao.insertWithIngredients(new Recipe("My own soup", 2, Collections.singletonList("Heat.")),
                Collections.singletonList(new RecipeIngredient("lentil", 100, Unit.G)));

        assertEquals(SeedResult.ALREADY_SEEDED, new RecipeSeeder(context, database).seedIfEmpty());
        assertEquals(1, dao.count());
    }

    private static RecipeWithIngredients recipe(String name, RecipeIngredient ingredient) {
        return new RecipeWithIngredients(new Recipe(name, 1, Collections.singletonList("Cook.")),
                Collections.singletonList(ingredient));
    }

    private String readAsset() throws IOException {
        try (InputStream in = context.getAssets().open(RecipeSeeder.ASSET_NAME);
             Scanner scanner = new Scanner(in, "UTF-8")) {
            return scanner.useDelimiter("\\A").next();
        }
    }
}
