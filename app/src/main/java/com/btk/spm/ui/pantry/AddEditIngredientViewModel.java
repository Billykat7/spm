package com.btk.spm.ui.pantry;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.btk.spm.SpmApplication;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.repo.PantryRepository;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.validation.ValidationResult;
import com.btk.spm.domain.validation.Validators;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The state and the save behind the add ingredient form.
 *
 * <p>The name and quantity fields keep their own text across a rotation; the unit and the expiry
 * date are chosen from a dropdown and a date picker, so nothing on screen keeps them. They live here,
 * in a {@link SavedStateHandle}, which survives a rotation with the ViewModel and a process death
 * through the saved instance state. The unit is stored by its enum {@code name()} and the date as its
 * epoch day, both values a {@code Bundle} can hold.
 *
 * <p>{@link #save(String, String)} is the one way the form writes. It validates with
 * {@link Validators#validatePantryItem} and calls the repository only when the result is ok, so an
 * invalid form can never reach the database (non-negotiable 8). The repository's write runs on the
 * app's write thread; the screen does not wait for it, and the pantry list shows the row when Room
 * emits it.
 */
public class AddEditIngredientViewModel extends AndroidViewModel {

    /** Saved-state key of the chosen unit's {@code name()}; private to this form, so not an Intent extra. */
    @VisibleForTesting
    static final String STATE_UNIT_NAME = "com.btk.spm.state.UNIT_NAME";

    /** Saved-state key of the chosen expiry date's epoch day. */
    @VisibleForTesting
    static final String STATE_EXPIRY_EPOCH_DAY = "com.btk.spm.state.EXPIRY_EPOCH_DAY";

    private final SavedStateHandle state;
    private final PantryRepository repository;
    private final Clock clock;
    private final LiveData<Unit> unit;
    private final LiveData<LocalDate> expiry;

    /** Set once an item has been handed to the repository, so a second tap on Save cannot add it twice. */
    private boolean saved;

    /**
     * Creates the ViewModel over the app's one {@link PantryRepository} and the system clock. Called
     * by the default {@code ViewModelProvider} factory, which passes the application and the saved state.
     *
     * @param application the running app, whose {@link SpmApplication} holds the repository
     * @param state       the form's saved state
     */
    public AddEditIngredientViewModel(@NonNull Application application, @NonNull SavedStateHandle state) {
        this(application, state, SpmApplication.from(application).getPantryRepository(),
                Clock.systemDefaultZone());
    }

    /**
     * Creates the ViewModel with its collaborators given; a test passes an in-memory repository and a
     * fixed clock.
     *
     * @param application the running app
     * @param state       the form's saved state
     * @param repository  where a valid item is inserted
     * @param clock       what "today" and the creation time are read from
     */
    @VisibleForTesting
    AddEditIngredientViewModel(@NonNull Application application, @NonNull SavedStateHandle state,
                               @NonNull PantryRepository repository, @NonNull Clock clock) {
        super(application);
        this.state = state;
        this.repository = repository;
        this.clock = clock;
        // An initial value of null, so the form is told "nothing chosen" at once rather than never
        unit = Transformations.map(state.<String>getLiveData(STATE_UNIT_NAME, null),
                AddEditIngredientViewModel::unitNamed);
        expiry = Transformations.map(state.<Long>getLiveData(STATE_EXPIRY_EPOCH_DAY, null),
                epochDay -> epochDay == null ? null : LocalDate.ofEpochDay(epochDay));
    }

    /**
     * Returns the chosen unit, for the dropdown to show; it emits {@code null} until one is chosen.
     *
     * @return the observed unit
     */
    @NonNull
    public LiveData<Unit> getUnit() {
        return unit;
    }

    /**
     * Records the unit chosen from the dropdown.
     *
     * @param chosen the unit at the position the user tapped
     */
    @MainThread
    public void setUnit(@NonNull Unit chosen) {
        state.set(STATE_UNIT_NAME, chosen.name());
    }

    /**
     * Returns the chosen expiry date, for the date field to show; {@code null} means none.
     *
     * @return the observed date
     */
    @NonNull
    public LiveData<LocalDate> getExpiry() {
        return expiry;
    }

    /**
     * Records the date chosen in the picker, or clears it.
     *
     * @param chosen the chosen day, or {@code null} for an item that never expires
     */
    @MainThread
    public void setExpiry(@Nullable LocalDate chosen) {
        state.set(STATE_EXPIRY_EPOCH_DAY, chosen == null ? null : chosen.toEpochDay());
    }

    /**
     * Validates the form and, only when it is valid, inserts the item. "Today" for the expiry rule and
     * the item's creation time both come from the clock, read once here.
     *
     * @param name         the name field's text
     * @param quantityText the quantity field's text
     * @return the validation result: ok when the item was handed to the repository (or already had
     *     been), otherwise every field error, in field order, with nothing written
     */
    @MainThread
    @NonNull
    public ValidationResult save(@Nullable String name, @Nullable String quantityText) {
        Unit chosenUnit = unitNamed(state.get(STATE_UNIT_NAME));
        Long epochDay = state.get(STATE_EXPIRY_EPOCH_DAY);
        LocalDate chosenExpiry = epochDay == null ? null : LocalDate.ofEpochDay(epochDay);

        ValidationResult result = Validators.validatePantryItem(
                name, quantityText, chosenUnit, chosenExpiry, LocalDate.now(clock));
        if (result.isOk() && !saved) {
            saved = true;
            // Valid means both are present and the quantity parses, so neither can fail here
            repository.insert(new PantryItem(name.trim(), Validators.parseQuantity(quantityText).getAsDouble(),
                    chosenUnit, chosenExpiry, clock.millis()));
        }
        return result;
    }

    /** Reads a unit back from its saved {@code name()}; {@code null} when none was chosen. */
    @Nullable
    private static Unit unitNamed(@Nullable String name) {
        return name == null ? null : Unit.valueOf(name);
    }
}
