package com.btk.spm.ui.pantry;

import static com.btk.spm.testing.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.R;
import com.btk.spm.data.db.AppDatabase;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.repo.PantryRepository;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.validation.Field;
import com.btk.spm.domain.validation.FieldError;
import com.btk.spm.domain.validation.ValidationResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link AddEditIngredientViewModel} over an in-memory database (Issue 14): an invalid form writes
 * nothing, a valid one inserts exactly what was checked, and the chosen unit and date outlive the
 * ViewModel through its saved state. In edit mode (Issue 15) the row is read once and prefilled, an
 * unknown id is reported, and a save updates the same row, also after a rotation or a process death.
 */
@RunWith(AndroidJUnit4.class)
public class AddEditIngredientViewModelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final Instant NOW = TODAY.atTime(9, 30).toInstant(ZoneOffset.UTC);

    /** Room's {@code LiveData} work and saved-state {@code setValue} run on the test thread. */
    @Rule
    public final InstantTaskExecutorRule instantTasks = new InstantTaskExecutorRule();

    private final Application application = ApplicationProvider.getApplicationContext();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AppDatabase database;
    private PantryRepository repository;
    private SavedStateHandle state;
    private AddEditIngredientViewModel viewModel;

    @Before
    public void createViewModelOverAnInMemoryDatabase() {
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        // A direct executor: an insert is in the table before save() returns
        repository = new PantryRepository(database, Runnable::run);
        state = new SavedStateHandle();
        viewModel = new AddEditIngredientViewModel(application, state, repository, clock);
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void theEmptyForm_reportsNameQuantityAndUnit_andWritesNothing() throws InterruptedException {
        ValidationResult result = viewModel.save("", "", Locale.US);

        assertEquals(ValidationResult.error(
                new FieldError(Field.NAME, R.string.error_name_required),
                new FieldError(Field.QUANTITY, R.string.error_quantity_required),
                new FieldError(Field.UNIT, R.string.error_unit_required)), result);
        assertTrue(getOrAwaitValue(repository.observeAll()).isEmpty());
    }

    @Test
    public void aPastExpiry_isRefused_andWritesNothing() throws InterruptedException {
        viewModel.setUnit(Unit.PCS);
        viewModel.setExpiry(TODAY.minusDays(1));

        ValidationResult result = viewModel.save("Tomatoes", "4", Locale.US);

        assertEquals(ValidationResult.error(new FieldError(Field.EXPIRY, R.string.error_expiry_past)), result);
        assertTrue(getOrAwaitValue(repository.observeAll()).isEmpty());
    }

    @Test
    public void aValidForm_insertsTheTrimmedNameAndTheParsedQuantity() throws InterruptedException {
        viewModel.setUnit(Unit.KG);
        viewModel.setExpiry(TODAY);

        ValidationResult result = viewModel.save("  Plain flour ", "1,5", Locale.GERMANY);

        assertTrue(result.isOk());
        List<PantryItem> pantry = getOrAwaitValue(repository.observeAll());
        assertEquals(1, pantry.size());
        PantryItem saved = pantry.get(0);
        assertEquals("Plain flour", saved.getName());
        assertEquals(1.5, saved.getQuantity(), 0);
        assertEquals(Unit.KG, saved.getUnit());
        assertEquals(TODAY, saved.getExpiryDate());
        assertEquals(NOW.toEpochMilli(), saved.getCreatedAt());
    }

    @Test
    public void noExpiry_isSavedAsNull() throws InterruptedException {
        viewModel.setUnit(Unit.PCS);
        viewModel.setExpiry(TODAY.plusDays(3));
        viewModel.setExpiry(null);

        assertTrue(viewModel.save("tomatoes", "4", Locale.US).isOk());
        assertNull(getOrAwaitValue(repository.observeAll()).get(0).getExpiryDate());
    }

    @Test
    public void saveTwice_insertsOnce() throws InterruptedException {
        viewModel.setUnit(Unit.PCS);

        assertTrue(viewModel.save("tomatoes", "4", Locale.US).isOk());
        assertTrue(viewModel.save("tomatoes", "4", Locale.US).isOk());

        assertEquals(1, getOrAwaitValue(repository.observeAll()).size());
    }

    @Test
    public void theChosenUnitAndDate_comeBackFromSavedState() throws InterruptedException {
        viewModel.setUnit(Unit.TBSP);
        viewModel.setExpiry(TODAY.plusDays(10));

        // What a process death keeps: the saved state's values, copied into a new handle
        Map<String, Object> saved = new HashMap<>();
        for (String key : state.keys()) {
            saved.put(key, state.get(key));
        }
        AddEditIngredientViewModel restored =
                new AddEditIngredientViewModel(application, new SavedStateHandle(saved), repository, clock);

        assertEquals(Unit.TBSP, getOrAwaitValue(restored.getUnit()));
        assertEquals(TODAY.plusDays(10), getOrAwaitValue(restored.getExpiry()));
    }

    // Edit mode (Issue 15)

    @Test
    public void startEditing_prefillsFromTheRow_andBecomesReady() throws InterruptedException {
        PantryItem tomatoes = stored("tomatoes", 4, Unit.PCS, TODAY.plusDays(2));

        viewModel.startEditing(tomatoes.getId());

        assertEquals(tomatoes, getOrAwaitValue(viewModel.getPrefill()));
        assertEquals(Unit.PCS, getOrAwaitValue(viewModel.getUnit()));
        assertEquals(TODAY.plusDays(2), getOrAwaitValue(viewModel.getExpiry()));
        assertTrue(getOrAwaitValue(viewModel.isReady()));
        assertFalse(getOrAwaitValue(viewModel.isNotFound()));
    }

    @Test
    public void savingAnEdit_updatesTheSameRow_andAddsNone() throws InterruptedException {
        PantryItem tomatoes = stored("tomatoes", 4, Unit.PCS, null);
        viewModel.startEditing(tomatoes.getId());
        viewModel.onPrefillShown();

        assertTrue(viewModel.save("tomatoes", "6", Locale.US).isOk());

        List<PantryItem> pantry = getOrAwaitValue(repository.observeAll());
        assertEquals(1, pantry.size());
        PantryItem edited = pantry.get(0);
        assertEquals(tomatoes.getId(), edited.getId());
        assertEquals(6, edited.getQuantity(), 0);
        assertEquals("the creation time is kept", tomatoes.getCreatedAt(), edited.getCreatedAt());
    }

    @Test
    public void anInvalidEdit_writesNothing() throws InterruptedException {
        PantryItem tomatoes = stored("tomatoes", 4, Unit.PCS, null);
        viewModel.startEditing(tomatoes.getId());

        ValidationResult result = viewModel.save("   ", "6", Locale.US);

        assertEquals(ValidationResult.error(new FieldError(Field.NAME, R.string.error_name_required)), result);
        assertEquals(List.of(tomatoes), getOrAwaitValue(repository.observeAll()));
    }

    @Test
    public void anUnknownId_isReportedNotFound_andNothingIsPrefilled() throws InterruptedException {
        viewModel.startEditing(999_999L);

        assertTrue(getOrAwaitValue(viewModel.isNotFound()));
        assertFalse(getOrAwaitValue(viewModel.isReady()));
        assertNull(getOrAwaitValue(viewModel.getPrefill()));
    }

    @Test
    public void startEditingAgain_afterARotation_keepsWhatTheUserChose() throws InterruptedException {
        PantryItem tomatoes = stored("tomatoes", 4, Unit.PCS, null);
        viewModel.startEditing(tomatoes.getId());
        viewModel.onPrefillShown();
        viewModel.setUnit(Unit.KG);

        // The re-created screen calls it again with the same id
        viewModel.startEditing(tomatoes.getId());

        assertEquals(Unit.KG, getOrAwaitValue(viewModel.getUnit()));
        assertNull("not prefilled a second time", getOrAwaitValue(viewModel.getPrefill()));
        assertTrue(getOrAwaitValue(viewModel.isReady()));
    }

    @Test
    public void anEditRestoredAfterProcessDeath_isReady_andStillUpdatesTheSameRow() throws InterruptedException {
        PantryItem tomatoes = stored("tomatoes", 4, Unit.PCS, null);
        viewModel.startEditing(tomatoes.getId());
        viewModel.onPrefillShown();
        Map<String, Object> saved = new HashMap<>();
        for (String key : state.keys()) {
            saved.put(key, state.get(key));
        }
        AddEditIngredientViewModel restored =
                new AddEditIngredientViewModel(application, new SavedStateHandle(saved), repository, clock);

        restored.startEditing(tomatoes.getId());

        assertTrue(getOrAwaitValue(restored.isReady()));
        assertNull(getOrAwaitValue(restored.getPrefill()));
        assertTrue(restored.save("tomatoes", "5", Locale.US).isOk());
        PantryItem edited = getOrAwaitValue(repository.observeAll()).get(0);
        assertEquals(tomatoes.getId(), edited.getId());
        assertEquals(5, edited.getQuantity(), 0);
        assertEquals(tomatoes.getCreatedAt(), edited.getCreatedAt());
    }

    /** Inserts an item and returns it as stored, with its generated id. */
    private PantryItem stored(String name, double quantity, Unit unit, LocalDate expiry) throws InterruptedException {
        repository.insert(new PantryItem(name, quantity, unit, expiry, NOW.toEpochMilli() - 86_400_000L));
        return getOrAwaitValue(repository.observeAll()).get(0);
    }

    @Test
    public void nothingChosen_readsAsNoUnitAndNoDate() throws InterruptedException {
        assertNull(getOrAwaitValue(viewModel.getUnit()));
        assertNull(getOrAwaitValue(viewModel.getExpiry()));
        assertFalse(viewModel.save("tomatoes", "4", Locale.US).isOk());
    }
}
