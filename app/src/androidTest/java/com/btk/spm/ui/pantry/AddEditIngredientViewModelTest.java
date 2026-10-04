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
import java.util.Map;

/**
 * {@link AddEditIngredientViewModel} over an in-memory database (Issue 14): an invalid form writes
 * nothing, a valid one inserts exactly what was checked, and the chosen unit and date outlive the
 * ViewModel through its saved state.
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
        ValidationResult result = viewModel.save("", "");

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

        ValidationResult result = viewModel.save("Tomatoes", "4");

        assertEquals(ValidationResult.error(new FieldError(Field.EXPIRY, R.string.error_expiry_in_past)), result);
        assertTrue(getOrAwaitValue(repository.observeAll()).isEmpty());
    }

    @Test
    public void aValidForm_insertsTheTrimmedNameAndTheParsedQuantity() throws InterruptedException {
        viewModel.setUnit(Unit.KG);
        viewModel.setExpiry(TODAY);

        ValidationResult result = viewModel.save("  Plain flour ", "1,5");

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

        assertTrue(viewModel.save("tomatoes", "4").isOk());
        assertNull(getOrAwaitValue(repository.observeAll()).get(0).getExpiryDate());
    }

    @Test
    public void saveTwice_insertsOnce() throws InterruptedException {
        viewModel.setUnit(Unit.PCS);

        assertTrue(viewModel.save("tomatoes", "4").isOk());
        assertTrue(viewModel.save("tomatoes", "4").isOk());

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

    @Test
    public void nothingChosen_readsAsNoUnitAndNoDate() throws InterruptedException {
        assertNull(getOrAwaitValue(viewModel.getUnit()));
        assertNull(getOrAwaitValue(viewModel.getExpiry()));
        assertFalse(viewModel.save("tomatoes", "4").isOk());
    }
}
