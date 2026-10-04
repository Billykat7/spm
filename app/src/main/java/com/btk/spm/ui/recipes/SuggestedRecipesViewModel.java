package com.btk.spm.ui.recipes;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.btk.spm.data.mapping.PantryEntryMapper;
import com.btk.spm.data.mapping.RecipeSpecMapper;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.matching.MatchOptions;
import com.btk.spm.domain.matching.MatchResult;
import com.btk.spm.domain.matching.MatchResults;
import com.btk.spm.domain.matching.StrictMatcher;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The state behind the Recipes tab: which recipes the pantry can make right now, worked out by
 * {@link StrictMatcher} and kept up to date by Room.
 *
 * <p><b>Three sources.</b> {@link #getState()} is a {@link MediatorLiveData} over the pantry's
 * {@code LiveData} query, the recipes' {@code LiveData} query and the {@code COUNT_EXPIRED_ITEMS}
 * setting as {@code LiveData} ({@code AppPreferences.observeBoolean}, Issue 26). When either table
 * changes, Room emits again and the match runs again, so a change made on the Pantry tab is already
 * in the list when the user comes back to Recipes (non-negotiable 6); when the setting is turned on or
 * off, the match runs again too (decision 6). Nothing is computed once on open, and there is no
 * refresh button. Until all three have delivered, the state stays {@link UiState.Loading}: a pantry
 * with no recipes yet is not "nothing matches". {@code Loading} is the first state only: once a result has been posted,
 * the next match leaves it on screen until its own result replaces it.
 *
 * <p><b>The executor.</b> Matching never runs on the main thread. Each change submits one job to the
 * executor it was given (in the app, {@code SpmApplication}'s single matching thread). The job maps
 * the entities to engine values, builds {@link MatchOptions} for today, calls
 * {@link StrictMatcher#matchAll}, splits the results with {@link MatchResults#partition} and
 * {@code postValue}s the new state to the main thread. The screen only ever receives that posted
 * state. "Today" comes from the clock supplier it was given, so the engine never reads the clock.
 *
 * <p><b>The generation counter.</b> Every job is numbered when it is submitted. A job posts its state
 * only if no newer job has been submitted since it started, so a match of an old pantry that finishes
 * late can never overwrite the match of the current one.
 *
 * <p>The only decision made here is which list to render: {@code partition}'s {@code canMake}, as it
 * comes. Whether a recipe can be made is the engine's answer alone (non-negotiable 1).
 */
public class SuggestedRecipesViewModel extends ViewModel {

    private final MediatorLiveData<UiState> state = new MediatorLiveData<>(UiState.Loading.INSTANCE);

    private final Supplier<StrictMatcher> matcher;
    private final Supplier<LocalDate> today;
    private final Executor matchExecutor;

    /** The number of the newest job; a job whose number is not this one throws its result away. */
    private final AtomicInteger generation = new AtomicInteger();

    /** Jobs submitted and not finished yet; a device test waits for it to reach 0 (Issue 26). */
    private final AtomicInteger pendingMatches = new AtomicInteger();

    /** The last pantry Room emitted, or {@code null} before the first. Main thread only. */
    @Nullable
    private List<PantryItem> pantry;

    /** The last recipes Room emitted, or {@code null} before the first. Main thread only. */
    @Nullable
    private List<RecipeWithIngredients> recipes;

    /** Whether expired items count, as the setting last said, or {@code null} before it said. Main thread only. */
    @Nullable
    private Boolean countExpiredItems;

    /**
     * Creates the ViewModel over its sources. The app's {@link SuggestedRecipesViewModelFactory} passes
     * the repositories' queries, the setting, the shared matcher, the clock and the matching thread; a
     * test passes {@code MutableLiveData} for the three sources and an executor it controls.
     *
     * @param pantrySource      the pantry as Room emits it
     * @param recipeSource      every recipe with its ingredients, as Room emits them
     * @param countExpiredItems whether expired items count (decision 6), as the setting changes
     * @param matcher           gives the matcher; called on the executor, because building it the
     *                          first time reads an asset
     * @param today             the day to match on; read on the executor, once per job
     * @param matchExecutor     where every match runs; never the main thread in the app
     */
    SuggestedRecipesViewModel(@NonNull LiveData<List<PantryItem>> pantrySource,
                              @NonNull LiveData<List<RecipeWithIngredients>> recipeSource,
                              @NonNull LiveData<Boolean> countExpiredItems,
                              @NonNull Supplier<StrictMatcher> matcher,
                              @NonNull Supplier<LocalDate> today,
                              @NonNull Executor matchExecutor) {
        this.matcher = matcher;
        this.today = today;
        this.matchExecutor = matchExecutor;
        state.addSource(pantrySource, items -> {
            pantry = items;
            recompute();
        });
        state.addSource(recipeSource, items -> {
            recipes = items;
            recompute();
        });
        state.addSource(countExpiredItems, count -> {
            this.countExpiredItems = count;
            recompute();
        });
    }

    /**
     * Returns what the Recipes tab shows: {@link UiState.Loading} once, first, then a new
     * {@link UiState.Content} or {@link UiState.Empty} with its {@link EmptyReason} after every change
     * to the pantry, the recipes or the setting.
     * Observe it with the view's lifecycle owner.
     *
     * @return the observed state; it always has a value
     */
    @NonNull
    public LiveData<UiState> getState() {
        return state;
    }

    /**
     * Returns how many matches have been submitted and have not finished. An instrumented test wraps it
     * in an {@code IdlingResource}, so Espresso waits for the matcher as it waits for the main thread;
     * nothing in the app reads it.
     *
     * @return 0 when no match is running or queued
     */
    @VisibleForTesting
    public int pendingMatches() {
        return pendingMatches.get();
    }

    /** Submits a match of the latest pantry, recipes and setting, once all three have been read. */
    @MainThread
    private void recompute() {
        List<PantryItem> pantryNow = pantry;
        List<RecipeWithIngredients> recipesNow = recipes;
        Boolean countExpiredNow = countExpiredItems;
        if (pantryNow == null || recipesNow == null || countExpiredNow == null) {
            return; // still Loading: one source has not delivered
        }
        int job = generation.incrementAndGet();
        pendingMatches.incrementAndGet();
        matchExecutor.execute(() -> {
            try {
                UiState result = match(pantryNow, recipesNow, countExpiredNow);
                // A newer job was submitted while this one ran: its pantry is the current one
                if (job == generation.get()) {
                    state.postValue(result);
                }
            } finally {
                // After postValue, so the state is already on its way to the main thread when this reaches 0
                pendingMatches.decrementAndGet();
            }
        });
    }

    /** Maps, matches and splits; runs on the matching executor. */
    @WorkerThread
    @NonNull
    private UiState match(@NonNull List<PantryItem> pantryNow, @NonNull List<RecipeWithIngredients> recipesNow,
                          boolean countExpiredNow) {
        MatchOptions options = MatchOptions.on(today.get(), countExpiredNow);
        List<MatchResult> results = matcher.get().matchAll(PantryEntryMapper.toEntries(pantryNow),
                RecipeSpecMapper.toSpecs(recipesNow), options);
        MatchResults.Partition groups = MatchResults.partition(results);
        if (groups.canMake().isEmpty()) {
            // The engine has already said no; the reason only chooses the words and the button
            return new UiState.Empty(EmptyReason.of(recipesNow.isEmpty(), pantryNow.isEmpty()));
        }
        Map<Long, RecipeWithIngredients> byId = new HashMap<>();
        for (RecipeWithIngredients recipe : recipesNow) {
            byId.put(recipe.getRecipe().getId(), recipe);
        }
        return new UiState.Content(pair(groups.canMake(), byId), pair(groups.almostThere(), byId));
    }

    /** Puts each result next to the recipe it judged, keeping the results' order. */
    @NonNull
    private static List<MatchedRecipe> pair(@NonNull List<MatchResult> results,
                                            @NonNull Map<Long, RecipeWithIngredients> byId) {
        List<MatchedRecipe> rows = new ArrayList<>(results.size());
        for (MatchResult result : results) {
            rows.add(new MatchedRecipe(byId.get(result.recipeId()), result));
        }
        return rows;
    }

    /** A job still running when the screen is gone for good finishes without posting. */
    @Override
    protected void onCleared() {
        generation.incrementAndGet();
        super.onCleared();
    }
}
