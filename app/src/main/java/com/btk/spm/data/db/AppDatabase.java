package com.btk.spm.data.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;

/**
 * The app's one database: Room over SQLite, on the device (decision 1). Three tables:
 * {@code pantry_items} (what the user has), {@code recipes} (what can be cooked) and
 * {@code recipe_ingredients} (what each recipe needs, one row per ingredient, pointing at its recipe).
 *
 * <p>{@code exportSchema = true} with the {@code room.schemaLocation} processor argument makes every
 * build write {@code app/schemas/com.btk.spm.data.db.AppDatabase/<version>.json}. Version 1 is
 * committed: the report's ER diagram is drawn from it (Issue 34), and the first schema change will
 * bump {@code version}, add a {@code Migration} and test it against that file. There is no
 * destructive fallback, so a schema change without a migration fails instead of wiping the pantry.
 *
 * <p>{@code SpmApplication} builds the one instance; nothing else calls {@code Room.databaseBuilder}.
 * Tests build their own with {@code Room.inMemoryDatabaseBuilder}.
 */
@Database(
        entities = {PantryItem.class, Recipe.class, RecipeIngredient.class},
        version = 1,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    /** The file name under the app's {@code databases/} directory, which the Database Inspector shows. */
    @NonNull
    public static final String DB_NAME = "spm.db";

    /**
     * Returns the DAO for {@code pantry_items}. Only {@code data/repo/} calls it.
     *
     * @return the pantry DAO
     */
    @NonNull
    public abstract PantryItemDao pantryItemDao();

    /**
     * Returns the DAO for {@code recipes} and {@code recipe_ingredients}. Only {@code data/} calls it.
     *
     * @return the recipe DAO
     */
    @NonNull
    public abstract RecipeDao recipeDao();
}
