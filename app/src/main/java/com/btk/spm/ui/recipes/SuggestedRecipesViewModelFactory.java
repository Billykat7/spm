package com.btk.spm.ui.recipes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.btk.spm.SpmApplication;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.settings.AppPreferences;

import java.time.LocalDate;
import java.util.List;

/**
 * Builds {@link SuggestedRecipesViewModel} from the app's single instances, so the Fragment never
 * sees a repository, the matcher or a thread.
 *
 * <p>This is where the real sources are wired in: the pantry and recipe queries from
 * {@link SpmApplication}'s repositories, its shared matcher and matching thread, the
 * {@code COUNT_EXPIRED_ITEMS} setting through {@link AppPreferences}, and the clock as
 * {@code LocalDate::now}. The clock is read here, at the edge of the app, and nowhere in
 * {@code domain/}: the engine is told the day.
 *
 * <p>The recipes are passed on only once the first-run seed has finished. Before that, Room can emit
 * an empty table that is merely not filled yet, and the tab would say the recipes did not load
 * ({@link EmptyReason#NO_RECIPES}) for a moment on a fresh install. Held back, the tab stays
 * {@link UiState.Loading} instead, and an empty table after the seed really is a failed seed.
 */
public final class SuggestedRecipesViewModelFactory implements ViewModelProvider.Factory {

    private final SpmApplication app;

    /**
     * Creates the factory.
     *
     * @param application the running app, whose {@link SpmApplication} holds the repositories
     */
    public SuggestedRecipesViewModelFactory(@NonNull Application application) {
        this.app = SpmApplication.from(application);
    }

    /**
     * Creates the ViewModel; called once per Fragment, and not again after a rotation.
     *
     * @throws IllegalArgumentException if asked for any other ViewModel
     */
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(SuggestedRecipesViewModel.class)) {
            throw new IllegalArgumentException("Cannot create " + modelClass.getName());
        }
        AppPreferences preferences = AppPreferences.from(app);
        // null while the seed runs: switchMap then has no source, so nothing is emitted
        LiveData<List<RecipeWithIngredients>> seededRecipes = Transformations.switchMap(app.getRecipeSeedDone(),
                done -> Boolean.TRUE.equals(done) ? app.getRecipeRepository().observeAllWithIngredients() : null);
        return modelClass.cast(new SuggestedRecipesViewModel(
                app.getPantryRepository().observeAll(),
                seededRecipes,
                app::getStrictMatcher,
                preferences::isCountExpiredItems,
                LocalDate::now,
                app.getMatchExecutor()));
    }
}
