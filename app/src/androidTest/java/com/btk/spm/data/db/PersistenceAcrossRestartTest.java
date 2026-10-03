package com.btk.spm.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.data.seed.RecipeJsonParser;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.data.seed.SeedResult;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Scanner;

/**
 * The rubric's "data persists between sessions" (brief §3.2), proven on a real file (Issue 12).
 *
 * <p>The in-memory tests of Issues 9 to 11 prove the queries and nothing about the disk: an in-memory
 * database passes every one of them and loses everything on close. This test does what the app does
 * between two launches, in three steps:
 * <ol>
 *   <li><b>write</b>: build {@link AppDatabase} with {@code Room.databaseBuilder}, the app's own
 *       builder, on a file; insert two pantry items, one with an expiry date and one without, and run
 *       the recipe seed;</li>
 *   <li><b>close</b> that instance, as a process does when it ends;</li>
 *   <li><b>reopen</b>: build a new instance on the same file and read everything back.</li>
 * </ol>
 * It uses its own file name, so it never touches the app's {@code spm.db}, and deletes the file, with
 * its journal, before and after.
 */
@RunWith(AndroidJUnit4.class)
public class PersistenceAcrossRestartTest {

    /** A file of its own under the app's {@code databases/} directory. */
    private static final String DB_FILE = "spm-restart-test.db";

    private Context context;
    private AppDatabase database;

    @Before
    public void deleteAnyLeftoverFile() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_FILE);
    }

    @After
    public void closeAndDeleteTheFile() {
        if (database != null && database.isOpen()) {
            database.close();
        }
        // deleteDatabase removes the -wal, -shm and -journal files too
        context.deleteDatabase(DB_FILE);
        assertFalse("the test left its database file behind", context.getDatabasePath(DB_FILE).exists());
    }

    @Test
    public void pantryItemsAndSeededRecipes_surviveCloseAndReopen() throws IOException {
        PantryItem expires = new PantryItem("Milk", 1, Unit.L, LocalDate.of(2026, 10, 20), 1_790_000_000_000L);
        PantryItem neverExpires = new PantryItem("Salt", 500, Unit.G, null, 1_790_000_000_001L);

        // 1. Write
        database = open();
        long expiresId = database.pantryItemDao().insert(expires);
        long neverId = database.pantryItemDao().insert(neverExpires);
        assertEquals(SeedResult.SEEDED, new RecipeSeeder(context, database).seedIfEmpty());

        // 2. Close
        database.close();
        File file = context.getDatabasePath(DB_FILE);
        assertTrue("no database file at " + file, file.exists() && file.length() > 0);

        // 3. Reopen: a new instance on the same file
        database = open();
        assertEquals(2, database.pantryItemDao().countSync());
        assertEquals(withId(expires, expiresId), database.pantryItemDao().getByIdSync(expiresId));
        assertEquals(withId(neverExpires, neverId), database.pantryItemDao().getByIdSync(neverId));
        assertEquals(20, database.recipeDao().count());
        assertEquals(ingredientsInAsset(), database.recipeDao().countIngredients());
    }

    @Test
    public void aReopenedDatabase_isNeverSeededAgain() {
        database = open();
        new RecipeSeeder(context, database).seedIfEmpty();
        database.close();

        database = open();

        assertEquals(SeedResult.ALREADY_SEEDED, new RecipeSeeder(context, database).seedIfEmpty());
        assertEquals(20, database.recipeDao().count());
    }

    /** The app's builder on this test's file. */
    private AppDatabase open() {
        return Room.databaseBuilder(context, AppDatabase.class, DB_FILE).build();
    }

    private int ingredientsInAsset() throws IOException {
        try (InputStream in = context.getAssets().open(RecipeSeeder.ASSET_NAME);
             Scanner scanner = new Scanner(in, "UTF-8")) {
            int count = 0;
            for (RecipeWithIngredients recipe : RecipeJsonParser.parse(scanner.useDelimiter("\\A").next())) {
                count += recipe.getIngredients().size();
            }
            return count;
        }
    }

    private static PantryItem withId(PantryItem item, long id) {
        return new PantryItem(id, item.getName(), item.getQuantity(), item.getUnit(),
                item.getExpiryDate(), item.getCreatedAt());
    }
}
