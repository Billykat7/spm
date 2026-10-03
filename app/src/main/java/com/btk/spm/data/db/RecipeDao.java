package com.btk.spm.data.db;

import androidx.room.Dao;

/**
 * Reads {@code recipes} with their {@code recipe_ingredients}, and inserts them for the first-run
 * seed. Recipes are read-only seed data (decision 7), so nothing outside {@code data/} writes
 * through it.
 *
 * <p>Declared empty in Issue 8, with its getter on {@link AppDatabase}, so that Issue 10 fills this
 * file and no other while Issue 9 fills {@link PantryItemDao} in parallel.
 */
@Dao
public interface RecipeDao {
}
