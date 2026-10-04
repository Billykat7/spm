package com.btk.spm.ui.pantry;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.repo.PantryRepository;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.settings.AppPreferences;
import com.btk.spm.settings.PrefKey;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * {@link PantryViewModel} over an in-memory database (Issue 13): the list it exposes is the table,
 * sorted by the current {@link SortOrder}, and it follows every write without being asked. The order
 * is read from and remembered in preferences of the test's own, cleared before each test (Issue 17).
 */
@RunWith(AndroidJUnit4.class)
public class PantryViewModelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    /** Room's {@code LiveData} work and {@code setValue} run on the test thread. */
    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    /** The test's own preferences file, never the app's. */
    private static final String PREFERENCES_FILE = "PantryViewModelTest";

    private Application application;
    private AppDatabase database;
    private PantryRepository repository;
    private SharedPreferences stored;
    private PantryViewModel viewModel;

    @Before
    public void createViewModelOverAnInMemoryDatabase() {
        application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        // A direct executor: each write is in the table before the call returns
        repository = new PantryRepository(database, Runnable::run);
        stored = application.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        stored.edit().clear().commit();
        viewModel = new PantryViewModel(application, repository, new AppPreferences(stored));
    }

    @After
    public void closeDatabaseAndClearPreferences() {
        database.close();
        stored.edit().clear().commit();
    }

    @Test
    public void items_onAnEmptyPantry_isAnEmptyList() throws InterruptedException {
        assertTrue(getOrAwaitValue(viewModel.getItems()).isEmpty());
    }

    @Test
    public void byDefault_theSoonestExpiryComesFirst_andUndatedRowsLast() throws InterruptedException {
        repository.insert(item("flour", TODAY.plusDays(1), 1));
        repository.insert(item("Eggs", null, 2));
        repository.insert(item("banana", TODAY, 3));

        assertEquals(SortOrder.EXPIRY_SOONEST, getOrAwaitValue(viewModel.getSortOrder()));
        assertEquals(List.of("banana", "flour", "Eggs"), names(getOrAwaitValue(viewModel.getItems())));
    }

    @Test
    public void setSortOrder_reordersTheSameRows_byNameIgnoringCase() throws InterruptedException {
        repository.insert(item("flour", TODAY.plusDays(1), 1));
        repository.insert(item("Eggs", null, 2));
        repository.insert(item("banana", TODAY.plusDays(5), 3));

        viewModel.setSortOrder(SortOrder.NAME);

        assertEquals(SortOrder.NAME, getOrAwaitValue(viewModel.getSortOrder()));
        assertEquals(List.of("banana", "Eggs", "flour"), names(getOrAwaitValue(viewModel.getItems())));
    }

    @Test
    public void theChosenOrder_isRemembered_byTheNextViewModel() throws InterruptedException {
        viewModel.setSortOrder(SortOrder.NAME);

        // What the app reads after a force-stop: a new ViewModel over the same stored preferences
        PantryViewModel reopened = new PantryViewModel(application, repository, new AppPreferences(stored));

        assertEquals(SortOrder.NAME.name(), stored.getString(PrefKey.PANTRY_SORT.key(), null));
        assertEquals(SortOrder.NAME, getOrAwaitValue(reopened.getSortOrder()));
    }

    @Test
    public void anUnknownStoredOrder_opensOnTheDefault_insteadOfCrashing() throws InterruptedException {
        stored.edit().putString(PrefKey.PANTRY_SORT.key(), "BY_COLOUR").commit();

        PantryViewModel reopened = new PantryViewModel(application, repository, new AppPreferences(stored));

        assertEquals(SortOrder.EXPIRY_SOONEST, getOrAwaitValue(reopened.getSortOrder()));
    }

    @Test
    public void theDisplay_isThreeDaysAndMetric_thenFollowsTheSettings() {
        List<PantryDisplay> seen = new CopyOnWriteArrayList<>();
        // Observed for the whole test, as the Fragment does, so each change is seen as it happens
        onMain(() -> viewModel.getDisplay().observeForever(seen::add));
        assertEquals(List.of(new PantryDisplay(3, UnitsSystem.METRIC)), seen);

        // Written on the main thread, where SharedPreferences calls its change listeners
        onMain(() -> stored.edit().putInt(PrefKey.EXPIRY_THRESHOLD_DAYS.key(), 10).commit());
        onMain(() -> stored.edit().putString(PrefKey.UNITS_SYSTEM.key(), UnitsSystem.IMPERIAL.name()).commit());

        assertEquals(List.of(new PantryDisplay(3, UnitsSystem.METRIC), new PantryDisplay(10, UnitsSystem.METRIC),
                new PantryDisplay(10, UnitsSystem.IMPERIAL)), seen);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
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

    @Test
    public void deleteThenUndo_bringsTheSameRowBack_inItsSortedPlace() throws InterruptedException {
        repository.insert(item("apple", null, 1));
        repository.insert(item("banana", null, 2));
        repository.insert(item("cherry", null, 3));
        List<PantryItem> before = getOrAwaitValue(viewModel.getItems());
        PantryItem banana = before.get(1);

        viewModel.delete(banana);
        assertEquals(List.of("apple", "cherry"), names(getOrAwaitValue(viewModel.getItems())));

        viewModel.undoDelete(banana);
        List<PantryItem> after = getOrAwaitValue(viewModel.getItems());
        assertEquals(before, after);
        assertEquals(banana.getId(), after.get(1).getId());
    }

    @Test
    public void deletingTheLastItem_emptiesTheList_andUndoRefillsIt() throws InterruptedException {
        repository.insert(item("rice", null, 1));
        PantryItem rice = getOrAwaitValue(viewModel.getItems()).get(0);

        viewModel.delete(rice);
        assertTrue(getOrAwaitValue(viewModel.getItems()).isEmpty());

        viewModel.undoDelete(rice);
        assertEquals(List.of(rice), getOrAwaitValue(viewModel.getItems()));
    }

    private static PantryItem item(String name, LocalDate expiry, long createdAt) {
        return new PantryItem(name, 1, Unit.PCS, expiry, createdAt);
    }

    private static List<String> names(List<PantryItem> items) {
        return items.stream().map(PantryItem::getName).collect(Collectors.toList());
    }
}
