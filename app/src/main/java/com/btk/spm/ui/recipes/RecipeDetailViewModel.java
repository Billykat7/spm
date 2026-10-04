package com.btk.spm.ui.recipes;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.btk.spm.data.mapping.PantryEntryMapper;
import com.btk.spm.data.mapping.RecipeSpecMapper;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.domain.matching.IngredientCheck;
import com.btk.spm.domain.matching.MatchOptions;
import com.btk.spm.domain.matching.MatchResult;
import com.btk.spm.domain.matching.StrictMatcher;
import com.btk.spm.domain.matching.UnitConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The state behind the recipe detail screen: one recipe, and the matcher's verdict on each of its
 * ingredients against the pantry as it is now.
 *
 * <p>{@link #getState()} is a {@link MediatorLiveData} over Room's query for this one recipe, the
 * pantry's query and two settings, the same pattern as {@code SuggestedRecipesViewModel}. When the
 * pantry changes while the screen is open, the match runs again and a cross can turn into a check
 * without the screen being opened again; so does turning <i>Count expired items</i> on or off, and
 * switching the units redraws the amounts (Issue 28). An id Room has no recipe for gives
 * {@link DetailUiState.NotFound}.
 *
 * <p>The match runs on the executor it was given, never the main thread: map the entities, call
 * {@link StrictMatcher#match} for this recipe with today and the {@code COUNT_EXPIRED_ITEMS} setting,
 * and turn each {@link IngredientCheck} of the result into an {@link IngredientRow}. Whether a line is
 * covered, and how much the pantry has of it, is the matcher's answer; this class only converts the
 * amounts to the display units the user chose. A generation counter drops a result that a newer
 * pantry has overtaken.
 */
public class RecipeDetailViewModel extends ViewModel {

    private final MediatorLiveData<DetailUiState> state = new MediatorLiveData<>(DetailUiState.Loading.INSTANCE);

    private final Supplier<StrictMatcher> matcher;
    private final Supplier<LocalDate> today;
    private final Executor matchExecutor;
    private final UnitConverter display = new UnitConverter();

    /** The number of the newest job; a job whose number is not this one throws its result away. */
    private final AtomicInteger generation = new AtomicInteger();

    /** The recipe Room last emitted, or {@code null} before the first. Main thread only. */
    @Nullable
    private RecipeWithIngredients recipe;

    /** The pantry Room last emitted, or {@code null} before the first. Main thread only. */
    @Nullable
    private List<PantryItem> pantry;

    /** Whether expired items count, as the setting last said, or {@code null} before it said. Main thread only. */
    @Nullable
    private Boolean countExpiredItems;

    /** The units amounts are shown in, as the setting last said, or {@code null} before it said. Main thread only. */
    @Nullable
    private UnitsSystem unitsSystem;

    /**
     * Creates the ViewModel over its sources. {@link RecipeDetailViewModelFactory} passes the
     * repositories' queries for the recipe's id; a test passes {@code MutableLiveData}.
     *
     * @param recipeSource      the recipe as Room emits it; {@code null} when no recipe has the id
     * @param pantrySource      the pantry as Room emits it
     * @param countExpiredItems whether expired items count (decision 6), as the setting changes
     * @param unitsSystem       the units amounts are shown in (decision 5), as the setting changes
     * @param matcher           gives the matcher; called on the executor
     * @param today             the day to match on; read on the executor, once per job
     * @param matchExecutor     where every match runs; never the main thread in the app
     */
    RecipeDetailViewModel(@NonNull LiveData<RecipeWithIngredients> recipeSource,
                          @NonNull LiveData<List<PantryItem>> pantrySource,
                          @NonNull LiveData<Boolean> countExpiredItems,
                          @NonNull LiveData<UnitsSystem> unitsSystem,
                          @NonNull Supplier<StrictMatcher> matcher,
                          @NonNull Supplier<LocalDate> today,
                          @NonNull Executor matchExecutor) {
        this.matcher = matcher;
        this.today = today;
        this.matchExecutor = matchExecutor;
        state.addSource(recipeSource, read -> {
            if (read == null) {
                // No such recipe: nothing to match, and no job still running may post over this
                generation.incrementAndGet();
                state.setValue(DetailUiState.NotFound.INSTANCE);
                return;
            }
            recipe = read;
            recompute();
        });
        state.addSource(pantrySource, items -> {
            pantry = items;
            recompute();
        });
        state.addSource(countExpiredItems, count -> {
            this.countExpiredItems = count;
            recompute();
        });
        state.addSource(unitsSystem, units -> {
            this.unitsSystem = units;
            recompute();
        });
    }

    /**
     * Returns what the screen shows: {@link DetailUiState.Loading} first, then
     * {@link DetailUiState.Loaded} after every change to the recipe, the pantry or either setting, or
     * {@link DetailUiState.NotFound}.
     *
     * @return the observed state; it always has a value
     */
    @NonNull
    public LiveData<DetailUiState> getState() {
        return state;
    }

    /** Submits a match of the recipe against the latest pantry, once all four sources have been read. */
    @MainThread
    private void recompute() {
        RecipeWithIngredients recipeNow = recipe;
        List<PantryItem> pantryNow = pantry;
        Boolean countExpiredNow = countExpiredItems;
        UnitsSystem unitsNow = unitsSystem;
        if (recipeNow == null || pantryNow == null || countExpiredNow == null || unitsNow == null
                || state.getValue() instanceof DetailUiState.NotFound) {
            return;
        }
        int job = generation.incrementAndGet();
        matchExecutor.execute(() -> {
            DetailUiState result = match(recipeNow, pantryNow, countExpiredNow, unitsNow);
            if (job == generation.get()) {
                state.postValue(result);
            }
        });
    }

    /** Matches this one recipe and builds a row from each check; runs on the matching executor. */
    @WorkerThread
    @NonNull
    private DetailUiState match(@NonNull RecipeWithIngredients recipeNow, @NonNull List<PantryItem> pantryNow,
                                boolean countExpiredNow, @NonNull UnitsSystem unitsNow) {
        MatchOptions options = MatchOptions.on(today.get(), countExpiredNow);
        MatchResult result = matcher.get().match(PantryEntryMapper.toEntries(pantryNow),
                RecipeSpecMapper.toSpec(recipeNow), options);
        List<RecipeIngredient> lines = recipeNow.getIngredients();
        List<IngredientRow> rows = new ArrayList<>(lines.size());
        // checks() is in the recipe's order, the order the mapper wrote the lines in
        for (int i = 0; i < lines.size(); i++) {
            IngredientCheck check = result.checks().get(i);
            rows.add(new IngredientRow(lines.get(i).getId(), check.required().name(),
                    display.toPreferredDisplay(check.required().quantity(), unitsNow),
                    check.available() == null ? null : display.toPreferredDisplay(check.available(), unitsNow),
                    check.satisfied()));
        }
        return new DetailUiState.Loaded(recipeNow, result, rows);
    }

    /** A job still running when the screen is gone for good finishes without posting. */
    @Override
    protected void onCleared() {
        generation.incrementAndGet();
        super.onCleared();
    }
}
