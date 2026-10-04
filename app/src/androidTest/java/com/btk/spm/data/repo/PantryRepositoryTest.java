package com.btk.spm.data.repo;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * {@link PantryRepository} over an in-memory database (Issue 9): the whole create, read, update and
 * delete cycle through the repository alone, and proof that a write is handed to the executor rather
 * than run on the caller's thread.
 */
@RunWith(AndroidJUnit4.class)
public class PantryRepositoryTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 10, 20);
    private static final long CREATED = 1_790_000_000_000L;

    // Room computes a LiveData and refreshes it after a write on a background thread. Without this
    // rule, a refresh started by a write could still be running when @After closed the database,
    // and its "connection pool has been closed" exception killed the whole test process. The rule
    // runs that work on the calling thread, so it has finished before the database closes.
    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private AppDatabase database;

    @Before
    public void openInMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void fullCrudCycle_throughTheRepositoryOnly() throws InterruptedException {
        // A direct executor runs each write before the call returns, so the cycle reads in order
        PantryRepository repository = new PantryRepository(database, Runnable::run);

        // C
        repository.insert(new PantryItem("Eggs", 6, Unit.PCS, EXPIRY, CREATED));
        List<PantryItem> afterInsert = getOrAwaitValue(repository.observeAll());
        assertEquals(1, afterInsert.size());
        PantryItem stored = afterInsert.get(0);
        assertTrue(stored.getId() > 0);

        // R
        assertEquals(stored, getOrAwaitValue(repository.observeById(stored.getId())));
        assertEquals(stored, repository.getByIdSync(stored.getId()));

        // U
        PantryItem edited = new PantryItem(stored.getId(), "Eggs", 4, Unit.PCS, null, CREATED);
        repository.update(edited);
        assertEquals(edited, getOrAwaitValue(repository.observeById(stored.getId())));

        // D
        repository.delete(edited);
        assertTrue(getOrAwaitValue(repository.observeAll()).isEmpty());
        assertNull(repository.getByIdSync(stored.getId()));
    }

    @Test
    public void deleteById_removesTheRow() throws InterruptedException {
        PantryRepository repository = new PantryRepository(database, Runnable::run);
        repository.insert(new PantryItem("Bread", 1, Unit.PCS, EXPIRY, CREATED));
        long id = getOrAwaitValue(repository.observeAll()).get(0).getId();

        repository.deleteById(id);

        assertTrue(getOrAwaitValue(repository.observeAll()).isEmpty());
    }

    @Test
    public void insert_isSubmittedToTheExecutor_notRunInline() {
        RecordingExecutor writes = new RecordingExecutor();
        PantryRepository repository = new PantryRepository(database, writes);

        repository.insert(new PantryItem("Rice", 500, Unit.G, null, CREATED));

        assertEquals(1, writes.pending());
        assertEquals("the insert ran before the executor ran it", 0, database.pantryItemDao().countSync());

        writes.runAll();

        assertEquals(1, database.pantryItemDao().countSync());
    }

    @Test
    public void everyWrite_isSubmittedToTheExecutor() {
        long id = database.pantryItemDao().insert(new PantryItem("Rice", 500, Unit.G, null, CREATED));
        PantryItem rice = database.pantryItemDao().getByIdSync(id);
        RecordingExecutor writes = new RecordingExecutor();
        PantryRepository repository = new PantryRepository(database, writes);

        repository.update(new PantryItem(id, "Rice", 250, Unit.G, null, CREATED));
        repository.delete(rice);
        repository.deleteById(id);

        assertEquals(3, writes.pending());
        assertEquals("a write ran before the executor ran it", rice, database.pantryItemDao().getByIdSync(id));
    }

    @Test
    public void reads_doNotUseTheWriteExecutor() {
        RecordingExecutor writes = new RecordingExecutor();
        PantryRepository repository = new PantryRepository(database, writes);

        repository.observeAll();
        repository.observeById(1);
        repository.getByIdSync(1);

        assertEquals(0, writes.pending());
    }

    /** Holds every task it is given until {@link #runAll()}, so a test can see a write before it runs. */
    private static final class RecordingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(@NonNull Runnable task) {
            tasks.add(task);
        }

        int pending() {
            return tasks.size();
        }

        void runAll() {
            for (Runnable task : tasks) {
                task.run();
            }
            tasks.clear();
        }
    }
}
