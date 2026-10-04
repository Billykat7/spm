package com.btk.spm.ui.recipes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.btk.spm.SpmApplication;
import com.btk.spm.settings.AppPreferences;

import java.time.LocalDate;

/**
 * Builds {@link SuggestedRecipesViewModel} from the app's single instances, so the Fragment never
 * sees a repository, the matcher or a thread.
 *
 * <p>This is where the real sources are wired in: the pantry and recipe queries from
 * {@link SpmApplication}'s repositories, its shared matcher and matching thread, the
 * {@code COUNT_EXPIRED_ITEMS} setting through {@link AppPreferences}, and the clock as
 * {@code LocalDate::now}. The clock is read here, at the edge of the app, and nowhere in
 * {@code domain/}: the engine is told the day.
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
        return modelClass.cast(new SuggestedRecipesViewModel(
                app.getPantryRepository().observeAll(),
                app.getRecipeRepository().observeAllWithIngredients(),
                app::getStrictMatcher,
                preferences::isCountExpiredItems,
                LocalDate::now,
                app.getMatchExecutor()));
    }
}
