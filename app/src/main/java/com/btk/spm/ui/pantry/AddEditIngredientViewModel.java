package com.btk.spm.ui.pantry;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
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
 * The state and the save behind the add and edit ingredient form.
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
 *
 * <p>In edit mode ({@link #startEditing(long)}) the row is read once, through
 * {@code PantryRepository.observeById}: the first value prefills the form, or, when it is
 * {@code null}, says the item is gone. The id and the row's creation time are kept in saved state, and
 * so is the fact that the form was prefilled, so a rotation keeps what the user has typed since
 * instead of loading the database's values over it. Save validates exactly as when adding, then
 * updates the row with the <b>same id</b>, so the edit changes that row and adds none.
 */
public class AddEditIngredientViewModel extends AndroidViewModel {

    /** Saved-state key of the chosen unit's {@code name()}; private to this form, so not an Intent extra. */
    @VisibleForTesting
    static final String STATE_UNIT_NAME = "com.btk.spm.state.UNIT_NAME";

    /** Saved-state key of the chosen expiry date's epoch day. */
    @VisibleForTesting
    static final String STATE_EXPIRY_EPOCH_DAY = "com.btk.spm.state.EXPIRY_EPOCH_DAY";

    /** Saved-state key of the id of the row being edited; absent when adding. */
    @VisibleForTesting
    static final String STATE_EDIT_ID = "com.btk.spm.state.EDIT_ID";

    /** Saved-state key of the edited row's creation time, which an edit keeps. Present once loaded. */
    @VisibleForTesting
    static final String STATE_EDIT_CREATED_AT = "com.btk.spm.state.EDIT_CREATED_AT";

    /** Saved-state key set once the form has shown the loaded row, so it is never prefilled twice. */
    @VisibleForTesting
    static final String STATE_PREFILLED = "com.btk.spm.state.PREFILLED";

    private final SavedStateHandle state;
    private final PantryRepository repository;
    private final Clock clock;
    private final LiveData<Unit> unit;
    private final LiveData<LocalDate> expiry;

    /** Whether the form can be used: at once when adding, once the row has loaded when editing. */
    private final MutableLiveData<Boolean> ready = new MutableLiveData<>(true);

    /**
     * The loaded row, until the screen has put its name and quantity in the fields. It starts at an
     * explicit {@code null}, so an observer is told "nothing to prefill" rather than nothing at all.
     */
    private final MutableLiveData<PantryItem> prefill = new MutableLiveData<>(null);

    /** Set when the row to edit does not exist (deleted elsewhere, or a stale id). */
    private final MutableLiveData<Boolean> notFound = new MutableLiveData<>(false);

    /** The one-time read of the row being edited, kept so it can be stopped if the screen goes first. */
    @Nullable
    private LiveData<PantryItem> loading;
    @Nullable
    private Observer<PantryItem> loadObserver;

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
     * Puts the form in edit mode for row {@code id} and reads that row once. Called by the screen in
     * {@code onCreate} every time it is created; after the first call it does nothing, so a rotation
     * neither reloads the row nor overwrites what the user has typed.
     *
     * @param id the id from the Intent's {@code IntentKeys.EXTRA_PANTRY_ITEM_ID}
     */
    @MainThread
    public void startEditing(long id) {
        state.set(STATE_EDIT_ID, id);
        if (loading != null) {
            return; // this ViewModel outlived a rotation: the row is loaded or on its way
        }
        if (Boolean.TRUE.equals(state.get(STATE_PREFILLED))) {
            return; // restored after the process was killed: the fields restore their own text
        }
        ready.setValue(false);
        loading = repository.observeById(id);
        loadObserver = new Observer<PantryItem>() {
            @Override
            public void onChanged(PantryItem item) {
                stopLoading();
                if (item == null) {
                    notFound.setValue(true);
                    return;
                }
                state.set(STATE_EDIT_CREATED_AT, item.getCreatedAt());
                state.set(STATE_UNIT_NAME, item.getUnit().name());
                state.set(STATE_EXPIRY_EPOCH_DAY,
                        item.getExpiryDate() == null ? null : item.getExpiryDate().toEpochDay());
                prefill.setValue(item);
                ready.setValue(true);
            }
        };
        loading.observeForever(loadObserver);
    }

    /**
     * Returns whether the form may be used: {@code true} at once when adding; when editing,
     * {@code false} until the row has loaded, so nothing can be saved before it is shown.
     *
     * @return the observed readiness
     */
    @NonNull
    public LiveData<Boolean> isReady() {
        return ready;
    }

    /**
     * Returns the row just loaded for editing, for the screen to put its name and quantity in the
     * fields (the unit and the date are already in this ViewModel); {@code null} once that is done.
     *
     * @return the row to prefill from, or {@code null}
     */
    @NonNull
    public LiveData<PantryItem> getPrefill() {
        return prefill;
    }

    /** Records that the screen has shown the loaded row, so it is never put in the fields again. */
    @MainThread
    public void onPrefillShown() {
        state.set(STATE_PREFILLED, true);
        prefill.setValue(null);
    }

    /**
     * Returns whether the row to edit was not found; the screen then says so and closes.
     *
     * @return the observed flag, {@code false} until a load finds nothing
     */
    @NonNull
    public LiveData<Boolean> isNotFound() {
        return notFound;
    }

    /**
     * Validates the form and, only when it is valid, inserts the item, or when editing, updates the
     * edited row with its own id and creation time. "Today" for the expiry rule and a new item's
     * creation time both come from the clock, read once here.
     *
     * @param name         the name field's text
     * @param quantityText the quantity field's text
     * @return the validation result: ok when the item was handed to the repository (or already had
     *     been), otherwise every field error, in field order, with nothing written
     * @throws IllegalStateException if called in edit mode before the row has loaded; the screen
     *     keeps Save disabled until {@link #isReady()} is {@code true}
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
            // Valid means both are present and the quantity parses, so neither can fail here
            String trimmedName = name.trim();
            double quantity = Validators.parseQuantity(quantityText).getAsDouble();
            Long editId = state.get(STATE_EDIT_ID);
            if (editId == null) {
                repository.insert(new PantryItem(trimmedName, quantity, chosenUnit, chosenExpiry, clock.millis()));
            } else {
                Long createdAt = state.get(STATE_EDIT_CREATED_AT);
                if (createdAt == null) {
                    throw new IllegalStateException("Save before row " + editId + " has loaded");
                }
                // The same id and creation time: Room's @Update rewrites that row and adds none
                repository.update(new PantryItem(editId, trimmedName, quantity, chosenUnit, chosenExpiry, createdAt));
            }
            saved = true;
        }
        return result;
    }

    @Override
    protected void onCleared() {
        stopLoading();
        super.onCleared();
    }

    /** Stops the one-time read, after its first value or when the screen goes before it arrives. */
    private void stopLoading() {
        if (loading != null && loadObserver != null) {
            loading.removeObserver(loadObserver);
        }
        loadObserver = null;
    }

    /** Reads a unit back from its saved {@code name()}; {@code null} when none was chosen. */
    @Nullable
    private static Unit unitNamed(@Nullable String name) {
        return name == null ? null : Unit.valueOf(name);
    }
}
