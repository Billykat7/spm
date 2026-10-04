package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.db.RecipeDao;
import com.btk.spm.data.model.RecipeWithIngredients;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link RecipeSeeder} given damaged files on a device (Issue 31): it seeds what it can, logs each
 * recipe it leaves out by its position, and never throws. The fixtures live in the test APK's own
 * {@code androidTest/assets/}, so the app never ships them; the database is in memory.
 */
@RunWith(AndroidJUnit4.class)
public class RecipeSeederMalformedTest {

    /** The test APK's assets, where the broken fixtures are. */
    private final AssetManager fixtures = InstrumentationRegistry.getInstrumentation().getContext().getAssets();

    private AppDatabase database;
    private RecipeDao dao;
    private final List<String> logged = new ArrayList<>();

    @Before
    public void openAnEmptyDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        dao = database.recipeDao();
    }

    @After
    public void close() {
        database.close();
    }

    @Test
    public void aFileWithThreeBrokenRecipes_seedsTheTwoGoodOnes_andLogsTheOthersByPosition() {
        RecipeSeeder seeder = new RecipeSeeder(database, sourceOf("recipes_malformed.json"), logged::add);

        assertEquals(SeedResult.SEEDED_SKIPPING_SOME, seeder.seedIfEmpty());

        List<String> names = dao.getAllWithIngredientsSync().stream()
                .map(r -> r.getRecipe().getName()).sorted().collect(Collectors.toList());
        assertEquals(List.of("Fixture salad", "Fixture soup"), names);
        assertEquals(3, logged.size());
        assertTrue(logged.get(0), logged.get(0).contains("recipe 2") && logged.get(0).contains("\"name\""));
        assertTrue(logged.get(1), logged.get(1).contains("No ingredients") && logged.get(1).contains("\"ingredients\""));
        assertTrue(logged.get(2), logged.get(2).contains("Handful of rice") && logged.get(2).contains("unit"));
    }

    @Test
    public void anEmptyFile_seedsNothing_andLeavesTheTableEmpty() {
        RecipeSeeder seeder = new RecipeSeeder(database, sourceOf("recipes_empty.json"), logged::add);

        assertEquals(SeedResult.NOTHING_TO_SEED, seeder.seedIfEmpty());

        assertEquals(0, dao.count());
        assertEquals(1, logged.size());
    }

    @Test
    public void aFileThatIsNotJson_seedsNothing_andSaysWhy() {
        RecipeSeeder seeder = new RecipeSeeder(database,
                () -> RecipeJsonParser.parseSkippingBroken("<recipes>not json</recipes>"), logged::add);

        assertEquals(SeedResult.NOTHING_TO_SEED, seeder.seedIfEmpty());

        assertEquals(0, dao.count());
        assertTrue(logged.toString(), logged.get(0).contains("not a JSON array"));
    }

    @Test
    public void aFileThatIsMissing_seedsNothing_insteadOfThrowing() {
        RecipeSeeder seeder = new RecipeSeeder(database, sourceOf("no_such_file.json"), logged::add);

        assertEquals(SeedResult.NOTHING_TO_SEED, seeder.seedIfEmpty());

        assertEquals(0, dao.count());
        assertTrue(logged.toString(), logged.get(0).startsWith("cannot read the recipes"));
    }

    @Test
    public void theFixtureSeeder_readsTheSameWayAsTheAppsOwn() {
        assertEquals(SeedResult.SEEDED_SKIPPING_SOME,
                RecipeSeeder.fromAsset(database, fixtures, "recipes_malformed.json").seedIfEmpty());
        for (RecipeWithIngredients recipe : dao.getAllWithIngredientsSync()) {
            assertTrue(recipe.getIngredients().size() > 0);
        }
    }

    /** The fixture named {@code name}, read and parsed as the app reads its own file. */
    private RecipeSeeder.RecipeSource sourceOf(String name) {
        return () -> RecipeJsonParser.parseSkippingBroken(AssetText.read(fixtures, name));
    }
}
