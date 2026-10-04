package com.btk.spm.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.db.PantryItemDao;
import com.btk.spm.data.model.PantryItem;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The only way the rest of the app reads or writes the pantry (non-negotiable 6).
 *
 * <p>Reads are the DAO's {@code LiveData}: a screen observes the list and is told when it changes,
 * so it never keeps a copy. Writes are handed to the executor and return nothing; the caller does not
 * wait for them, it sees their effect when the observed list emits again. In the app that executor
 * is {@code SpmApplication}'s single write thread, so writes keep the order they were submitted in and
 * never run on the main thread; a test passes one that runs a task at once, or one that holds it.
 *
 * <p>The repository takes the database and pulls the DAO itself, so no class outside {@code data/}
 * ever holds a DAO. Nothing here validates: a {@link PantryItem} handed to a write is assumed to have
 * passed {@code Validators} (Issue 14).
 */
public class PantryRepository {

    private final PantryItemDao dao;
    private final Executor writes;

    /**
     * Creates the repository over {@code database}'s pantry table.
     *
     * @param database the app's database
     * @param writes where every write runs; the app's single write thread
     */
    public PantryRepository(@NonNull AppDatabase database, @NonNull Executor writes) {
        this.dao = database.pantryItemDao();
        this.writes = writes;
    }

    /**
     * Returns the whole pantry, ordered by name without regard to case, updating whenever the table
     * changes.
     *
     * @return the observed list
     */
    @NonNull
    public LiveData<List<PantryItem>> observeAll() {
        return dao.observeAll();
    }

    /**
     * Returns one item, updating whenever it changes.
     *
     * @param id the item's id
     * @return a {@code LiveData} that delivers the item, or {@code null} if there is no such row
     */
    @NonNull
    public LiveData<PantryItem> observeById(long id) {
        return dao.observeById(id);
    }

    /**
     * Reads one item as it is now. Blocks, so only call it from a background thread.
     *
     * @param id the item's id
     * @return the item, or {@code null} if there is no such row
     */
    @WorkerThread
    @Nullable
    public PantryItem getByIdSync(long id) {
        return dao.getByIdSync(id);
    }

    /**
     * Reads every item that expires on or before {@code limit}, soonest first: expired items and those
     * expiring soon, when {@code limit} is today plus the threshold. The daily check (Issue 29) runs it
     * on WorkManager's thread. Blocks, so only call it from a background thread.
     *
     * @param limit the last expiry date to include
     * @return the items with a date on or before {@code limit}; empty, never {@code null}
     */
    @WorkerThread
    @NonNull
    public List<PantryItem> findWithExpiryOnOrBefore(@NonNull LocalDate limit) {
        return dao.findWithExpiryOnOrBefore(limit);
    }

    /**
     * Adds {@code item} on the write executor. An id of {@code 0} gets a generated one.
     *
     * @param item a validated item
     */
    public void insert(@NonNull PantryItem item) {
        writes.execute(() -> dao.insert(item));
    }

    /**
     * Rewrites the row with {@code item}'s id on the write executor.
     *
     * @param item the edited item, keeping the id and creation time of the row it replaces
     */
    public void update(@NonNull PantryItem item) {
        writes.execute(() -> dao.update(item));
    }

    /**
     * Removes the row with {@code item}'s id on the write executor.
     *
     * @param item the item to remove
     */
    public void delete(@NonNull PantryItem item) {
        writes.execute(() -> dao.delete(item));
    }

    /**
     * Removes the row with this id on the write executor.
     *
     * @param id the item's id
     */
    public void deleteById(long id) {
        writes.execute(() -> dao.deleteById(id));
    }
}
