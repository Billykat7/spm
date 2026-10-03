package com.btk.spm.data.db;

import androidx.room.Dao;

/**
 * Reads and writes {@code pantry_items}, the table the app's create, read, update and delete cycle
 * runs on (decision 7).
 *
 * <p>Declared empty in Issue 8, with its getter on {@link AppDatabase}, so that Issue 9 fills this
 * file and no other while Issue 10 fills {@link RecipeDao} in parallel. Only the repositories under
 * {@code data/} call it (non-negotiable 6).
 */
@Dao
public interface PantryItemDao {
}
