package com.btk.spm.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Opens {@link AppDatabase} on a device and reads what SQLite actually created (Issue 8): the three
 * tables, the cascading foreign key from {@code recipe_ingredients} to {@code recipes} and the index
 * on it. Its first job is to prove that Room, the converters and the instrumented-test dependencies
 * work together before Issues 9 and 10 add DAO tests on top.
 */
@RunWith(AndroidJUnit4.class)
public class AppDatabaseSmokeTest {

    private AppDatabase database;
    private SupportSQLiteDatabase sqlite;

    @Before
    public void openInMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        // Opening the connection runs Room's create statements
        sqlite = database.getOpenHelper().getWritableDatabase();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void createsExactlyTheThreeTables() {
        Set<String> tables = new HashSet<>();
        // Leaves out SQLite's and Room's own bookkeeping tables
        try (Cursor cursor = sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'"
                + " AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')")) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0));
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("pantry_items", "recipes", "recipe_ingredients")), tables);
    }

    @Test
    public void recipeIngredientsReferenceRecipesAndCascadeOnDelete() {
        try (Cursor cursor = sqlite.query("PRAGMA foreign_key_list(recipe_ingredients)")) {
            assertEquals(1, cursor.getCount());
            cursor.moveToFirst();
            assertEquals("recipes", cursor.getString(cursor.getColumnIndexOrThrow("table")));
            assertEquals("recipe_id", cursor.getString(cursor.getColumnIndexOrThrow("from")));
            assertEquals("id", cursor.getString(cursor.getColumnIndexOrThrow("to")));
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")));
        }
    }

    @Test
    public void recipeIdIsIndexed() {
        try (Cursor cursor = sqlite.query("PRAGMA index_info(index_recipe_ingredients_recipe_id)")) {
            assertTrue("index_recipe_ingredients_recipe_id is missing", cursor.moveToFirst());
            assertEquals("recipe_id", cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
    }
}
