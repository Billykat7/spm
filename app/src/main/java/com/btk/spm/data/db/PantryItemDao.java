package com.btk.spm.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.btk.spm.data.model.PantryItem;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads and writes {@code pantry_items}: the create, read, update and delete cycle the brief asks for
 * (§3.2), which decision 7 puts on pantry items.
 *
 * <ul>
 *   <li><b>C</b>reate: {@link #insert(PantryItem)} adds a row and returns its generated id.</li>
 *   <li><b>R</b>ead: {@link #observeAll()} is the pantry list, a {@code LiveData} that Room re-runs
 *       whenever the table changes, so the list screen and the suggested recipes update themselves
 *       (non-negotiable 6). {@link #observeById(long)} is one item, for the edit form.</li>
 *   <li><b>U</b>pdate: {@link #update(PantryItem)} rewrites the row with the same id.</li>
 *   <li><b>D</b>elete: {@link #delete(PantryItem)} and {@link #deleteById(long)} remove a row.</li>
 * </ul>
 *
 * <p>The {@code *Sync} reads ({@link #getByIdSync(long)}, {@link #getAllSync()},
 * {@link #countSync()}) return their result directly, for tests and background work; Room refuses
 * them on the main thread. Only {@code PantryRepository} calls this DAO; {@code DaoBoundaryTest}
 * fails the build if anything outside {@code data/} reaches it.
 */
@Dao
public interface PantryItemDao {

    /**
     * <b>C</b>: inserts a new item. An item whose id is {@code 0} gets a generated one; an item that
     * carries the id of a deleted row (undo, Issue 16) gets that row back.
     *
     * @param item the item to add, already validated (Issue 14)
     * @return the new row's id, above 0
     * @throws android.database.sqlite.SQLiteConstraintException if a row with the item's id exists
     */
    @Insert
    long insert(@NonNull PantryItem item);

    /**
     * <b>R</b>: the whole pantry, ordered by name without regard to case ({@code banana},
     * {@code Eggs}, {@code flour}), then by when each item was added, then by id, so two rows never
     * swap places between emissions. The {@code LiveData} emits again after every change to the
     * table.
     *
     * @return every item, never {@code null} once delivered
     */
    @Query("SELECT * FROM pantry_items ORDER BY name COLLATE NOCASE ASC, created_at ASC, id ASC")
    @NonNull
    LiveData<List<PantryItem>> observeAll();

    /**
     * <b>R</b>: one item, kept current while it is observed.
     *
     * @param id the item's id
     * @return a {@code LiveData} that delivers the item, or {@code null} if there is no such row (or
     *     once it has been deleted)
     */
    @Query("SELECT * FROM pantry_items WHERE id = :id")
    @NonNull
    LiveData<PantryItem> observeById(long id);

    /**
     * <b>R</b>, synchronously: one item as it is now.
     *
     * @param id the item's id
     * @return the item, or {@code null} if there is no such row
     */
    @WorkerThread
    @Query("SELECT * FROM pantry_items WHERE id = :id")
    @Nullable
    PantryItem getByIdSync(long id);

    /**
     * <b>R</b>, synchronously: the whole pantry as it is now, in the order of {@link #observeAll()}.
     *
     * @return every item; empty, never {@code null}, when the pantry is empty
     */
    @WorkerThread
    @Query("SELECT * FROM pantry_items ORDER BY name COLLATE NOCASE ASC, created_at ASC, id ASC")
    @NonNull
    List<PantryItem> getAllSync();

    /**
     * <b>R</b>, synchronously: every item that has an expiry date on or before {@code limit}, soonest
     * first. With {@code limit} at today plus the expiring-soon threshold, one call returns both the
     * expired items and the ones expiring soon, for the daily check (Issue 29); an item with no date
     * never expires and is never returned.
     *
     * @param limit the last expiry date to include, stored as its epoch day by {@link Converters}
     * @return the matching items, by expiry date and then name; empty, never {@code null}
     */
    @WorkerThread
    @Query("SELECT * FROM pantry_items WHERE expiry_date IS NOT NULL AND expiry_date <= :limit "
            + "ORDER BY expiry_date ASC, name COLLATE NOCASE ASC, id ASC")
    @NonNull
    List<PantryItem> findWithExpiryOnOrBefore(@NonNull LocalDate limit);

    /**
     * Counts the rows, synchronously.
     *
     * @return how many items the pantry holds
     */
    @WorkerThread
    @Query("SELECT COUNT(*) FROM pantry_items")
    int countSync();

    /**
     * <b>U</b>: rewrites every column of the row with {@code item}'s id. The id and
     * {@code created_at} are the caller's to keep, so an edit stays in its place in the list.
     *
     * @param item the edited item, with the id of an existing row
     * @return 1 if the row was updated, 0 if no row has that id
     */
    @Update
    int update(@NonNull PantryItem item);

    /**
     * <b>D</b>: deletes the row with {@code item}'s id.
     *
     * @param item the item to remove; only its id is used
     * @return 1 if the row was deleted, 0 if no row has that id
     */
    @Delete
    int delete(@NonNull PantryItem item);

    /**
     * <b>D</b>: deletes the row with this id, for a caller that has only the id.
     *
     * @param id the item's id
     * @return 1 if the row was deleted, 0 if no row has that id
     */
    @Query("DELETE FROM pantry_items WHERE id = :id")
    int deleteById(long id);
}
