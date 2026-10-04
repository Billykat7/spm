package com.btk.spm.data.db;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The pantry's create, read, update and delete on a real SQLite database in memory (Issue 9): each
 * operation does what {@link PantryItemDao} says, and the observed list emits again after each one,
 * which is what lets the pantry screen update itself (non-negotiable 6).
 *
 * <p>{@link InstantTaskExecutorRule} runs Room's {@code LiveData} work on the calling thread, and
 * {@code allowMainThreadQueries()} lets it, so every emission has arrived by the time a write
 * returns. Neither is ever set in the app ({@code DaoBoundaryTest} checks the second).
 */
@RunWith(AndroidJUnit4.class)
public class PantryItemDaoTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 10, 20);
    private static final long CREATED = 1_790_000_000_000L;

    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private AppDatabase database;
    private PantryItemDao dao;

    @Before
    public void openInMemoryDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.pantryItemDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    // C

    @Test
    public void insert_returnsGeneratedId_andTheItemReadsBackEqual() {
        PantryItem item = new PantryItem("Eggs", 6, Unit.PCS, EXPIRY, CREATED);

        long id = dao.insert(item);

        assertTrue("id should be generated, was " + id, id > 0);
        assertEquals(withId(item, id), dao.getByIdSync(id));
    }

    @Test
    public void insert_withNoExpiry_readsBackWithNullExpiry() {
        // A null expiry means the item never expires (decision 6)
        PantryItem item = new PantryItem("Salt", 1, Unit.KG, null, CREATED);

        long id = dao.insert(item);

        PantryItem read = dao.getByIdSync(id);
        assertEquals(withId(item, id), read);
        assertNull(read.getExpiryDate());
    }

    @Test
    public void insert_givesEachItemItsOwnId() {
        long first = dao.insert(new PantryItem("Rice", 500, Unit.G, null, CREATED));
        long second = dao.insert(new PantryItem("Rice", 500, Unit.G, null, CREATED));

        assertTrue(second != first);
        assertEquals(2, dao.countSync());
    }

    // R

    @Test
    public void observeAll_ordersByNameIgnoringCase() throws InterruptedException {
        dao.insert(new PantryItem("flour", 1, Unit.KG, null, CREATED));
        dao.insert(new PantryItem("Eggs", 6, Unit.PCS, null, CREATED));
        dao.insert(new PantryItem("banana", 3, Unit.PCS, null, CREATED));

        assertEquals(Arrays.asList("banana", "Eggs", "flour"), names(getOrAwaitValue(dao.observeAll())));
        assertEquals(Arrays.asList("banana", "Eggs", "flour"), names(dao.getAllSync()));
    }

    @Test
    public void observeAll_keepsItemsWithTheSameNameInTheOrderTheyWereAdded() throws InterruptedException {
        long later = dao.insert(new PantryItem("milk", 1, Unit.L, null, CREATED + 1000));
        long earlier = dao.insert(new PantryItem("Milk", 500, Unit.ML, null, CREATED));

        List<PantryItem> items = getOrAwaitValue(dao.observeAll());

        assertEquals(earlier, items.get(0).getId());
        assertEquals(later, items.get(1).getId());
    }

    @Test
    public void observeAll_onAnEmptyPantry_emitsAnEmptyList() throws InterruptedException {
        List<PantryItem> items = getOrAwaitValue(dao.observeAll());

        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    public void observeById_emitsTheItem_andNullForAnUnknownId() throws InterruptedException {
        long id = dao.insert(new PantryItem("Butter", 250, Unit.G, EXPIRY, CREATED));

        assertEquals("Butter", getOrAwaitValue(dao.observeById(id)).getName());
        assertNull(getOrAwaitValue(dao.observeById(id + 100)));
    }

    @Test
    public void getByIdSync_ofAnUnknownId_isNull() {
        assertNull(dao.getByIdSync(42));
    }

    // U

    @Test
    public void update_changesQuantityUnitAndExpiry_andKeepsTheRowCount() {
        long id = dao.insert(new PantryItem("Milk", 1, Unit.L, EXPIRY, CREATED));
        PantryItem edited = new PantryItem(id, "Milk", 500, Unit.ML, EXPIRY.plusDays(3), CREATED);

        assertEquals(1, dao.update(edited));

        assertEquals(edited, dao.getByIdSync(id));
        assertEquals(1, dao.countSync());
    }

    @Test
    public void update_canClearTheExpiry() {
        long id = dao.insert(new PantryItem("Honey", 340, Unit.G, EXPIRY, CREATED));

        dao.update(new PantryItem(id, "Honey", 340, Unit.G, null, CREATED));

        assertNull(dao.getByIdSync(id).getExpiryDate());
    }

    @Test
    public void update_ofAnUnknownId_changesNothing() {
        dao.insert(new PantryItem("Milk", 1, Unit.L, null, CREATED));

        assertEquals(0, dao.update(new PantryItem(999, "Cream", 1, Unit.L, null, CREATED)));
        assertEquals(1, dao.countSync());
        assertEquals("Milk", dao.getAllSync().get(0).getName());
    }

    // D

    @Test
    public void delete_removesTheRow_andObserveAllEmitsTheShorterList() throws InterruptedException {
        long keep = dao.insert(new PantryItem("Apple", 4, Unit.PCS, null, CREATED));
        long gone = dao.insert(new PantryItem("Bread", 1, Unit.PCS, EXPIRY, CREATED));

        assertEquals(1, dao.delete(dao.getByIdSync(gone)));

        assertNull(dao.getByIdSync(gone));
        List<PantryItem> items = getOrAwaitValue(dao.observeAll());
        assertEquals(1, items.size());
        assertEquals(keep, items.get(0).getId());
    }

    @Test
    public void deleteById_removesTheRow() {
        long id = dao.insert(new PantryItem("Bread", 1, Unit.PCS, EXPIRY, CREATED));

        assertEquals(1, dao.deleteById(id));
        assertEquals(0, dao.countSync());
    }

    @Test
    public void deleteById_ofAnUnknownId_returnsZero() {
        dao.insert(new PantryItem("Bread", 1, Unit.PCS, EXPIRY, CREATED));

        assertEquals(0, dao.deleteById(12_345));
        assertEquals(1, dao.countSync());
    }

    @Test
    public void insertingADeletedItemWithItsId_bringsTheSameRowBack() {
        // What undo after a delete relies on (Issue 16)
        long id = dao.insert(new PantryItem("Cheese", 200, Unit.G, EXPIRY, CREATED));
        PantryItem deleted = dao.getByIdSync(id);
        dao.delete(deleted);

        assertEquals(id, dao.insert(deleted));
        assertEquals(deleted, dao.getByIdSync(id));
    }

    @Test
    public void insertDeleteInsertOfTheSameObject_leavesExactlyOneRow_withTheOriginalIdAndContents() {
        // Undo (Issue 16) inserts the very object that was deleted, its id still set
        PantryItem tomatoes = dao.getByIdSync(dao.insert(new PantryItem("tomatoes", 4, Unit.PCS, EXPIRY, CREATED)));
        dao.delete(tomatoes);
        assertEquals(0, dao.countSync());

        dao.insert(tomatoes);

        assertEquals(1, dao.countSync());
        PantryItem restored = dao.getAllSync().get(0);
        assertEquals(tomatoes.getId(), restored.getId());
        assertEquals(tomatoes, restored);
    }

    @Test
    public void undoAfterAnotherInsert_keepsBothRows_andNeverReusesTheDeletedId() {
        // AUTOINCREMENT never hands out an id that was used before, so a row added between the
        // delete and the undo cannot take the deleted row's id and make the undo fail
        PantryItem eggs = dao.getByIdSync(dao.insert(new PantryItem("Eggs", 6, Unit.PCS, null, CREATED)));
        dao.delete(eggs);
        long milkId = dao.insert(new PantryItem("Milk", 1, Unit.L, EXPIRY, CREATED + 1));

        dao.insert(eggs);

        assertTrue(milkId > eggs.getId());
        assertEquals(Arrays.asList(eggs, dao.getByIdSync(milkId)), dao.getAllSync());
    }

    // The list updates itself

    @Test
    public void observeAll_emitsAgainAfterAnInsertAnUpdateAndADelete() {
        List<List<PantryItem>> emissions = new ArrayList<>();
        LiveData<List<PantryItem>> pantry = dao.observeAll();
        Observer<List<PantryItem>> recorder = emissions::add;
        pantry.observeForever(recorder);
        try {
            assertEquals(1, emissions.size());
            assertTrue(last(emissions).isEmpty());

            long id = dao.insert(new PantryItem("Tomato", 4, Unit.PCS, EXPIRY, CREATED));
            assertEquals(2, emissions.size());
            assertEquals(Arrays.asList("Tomato"), names(last(emissions)));

            dao.update(new PantryItem(id, "Tomato", 2, Unit.PCS, EXPIRY, CREATED));
            assertEquals(3, emissions.size());
            assertEquals(2.0, last(emissions).get(0).getQuantity(), 0.0);

            dao.deleteById(id);
            assertEquals(4, emissions.size());
            assertTrue(last(emissions).isEmpty());
        } finally {
            pantry.removeObserver(recorder);
        }
    }

    private static PantryItem withId(PantryItem item, long id) {
        return new PantryItem(id, item.getName(), item.getQuantity(), item.getUnit(),
                item.getExpiryDate(), item.getCreatedAt());
    }

    private static List<String> names(List<PantryItem> items) {
        List<String> names = new ArrayList<>();
        for (PantryItem item : items) {
            names.add(item.getName());
        }
        return names;
    }

    private static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }
}
