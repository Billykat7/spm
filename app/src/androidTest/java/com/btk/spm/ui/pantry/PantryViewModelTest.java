package com.btk.spm.ui.pantry;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.repo.PantryRepository;
import com.btk.spm.domain.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link PantryViewModel} over an in-memory database (Issue 13): the list it exposes is the table,
 * sorted by the current {@link SortOrder}, and it follows every write without being asked.
 */
@RunWith(AndroidJUnit4.class)
public class PantryViewModelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    /** Room's {@code LiveData} work and {@code setValue} run on the test thread. */
    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private AppDatabase database;
    private PantryRepository repository;
    private PantryViewModel viewModel;

    @Before
    public void createViewModelOverAnInMemoryDatabase() {
        Application application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        // A direct executor: each write is in the table before the call returns
        repository = new PantryRepository(database, Runnable::run);
        viewModel = new PantryViewModel(application, repository);
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void items_onAnEmptyPantry_isAnEmptyList() throws InterruptedException {
        assertTrue(getOrAwaitValue(viewModel.getItems()).isEmpty());
    }

    @Test
    public void items_areSortedByName_ignoringCase() throws InterruptedException {
        repository.insert(item("flour", TODAY.plusDays(1), 1));
        repository.insert(item("Eggs", null, 2));
        repository.insert(item("banana", TODAY, 3));

        assertEquals(List.of("banana", "Eggs", "flour"), names(getOrAwaitValue(viewModel.getItems())));
    }

    @Test
    public void setSortOrder_reordersTheSameRows() throws InterruptedException {
        repository.insert(item("flour", TODAY.plusDays(1), 1));
        repository.insert(item("Eggs", null, 2));
        repository.insert(item("banana", TODAY.plusDays(5), 3));

        viewModel.setSortOrder(SortOrder.EXPIRY_SOONEST);

        assertEquals(List.of("flour", "banana", "Eggs"), names(getOrAwaitValue(viewModel.getItems())));
    }

    @Test
    public void items_followEveryWrite_withNoRefresh() throws InterruptedException {
        repository.insert(item("rice", null, 1));
        List<PantryItem> first = getOrAwaitValue(viewModel.getItems());
        assertEquals(List.of("rice"), names(first));

        repository.insert(item("apple", null, 2));
        assertEquals(List.of("apple", "rice"), names(getOrAwaitValue(viewModel.getItems())));

        PantryItem rice = first.get(0);
        repository.update(new PantryItem(rice.getId(), rice.getName(), 3, Unit.KG, null, rice.getCreatedAt()));
        List<PantryItem> afterUpdate = getOrAwaitValue(viewModel.getItems());
        assertEquals(3, afterUpdate.get(1).getQuantity(), 0);
        assertEquals(Unit.KG, afterUpdate.get(1).getUnit());

        repository.deleteById(rice.getId());
        assertEquals(List.of("apple"), names(getOrAwaitValue(viewModel.getItems())));
    }

    private static PantryItem item(String name, LocalDate expiry, long createdAt) {
        return new PantryItem(name, 1, Unit.PCS, expiry, createdAt);
    }

    private static List<String> names(List<PantryItem> items) {
        return items.stream().map(PantryItem::getName).collect(Collectors.toList());
    }
}
