package com.btk.spm.ui.recipes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.btk.spm.SpmApplication;
import com.btk.spm.settings.AppPreferences;

import java.time.LocalDate;

/**
 * Builds {@link RecipeDetailViewModel} for one recipe id from the app's single instances: that
 * recipe's query and the pantry's, the shared matcher and its thread, the {@code COUNT_EXPIRED_ITEMS}
 * setting and the clock. As in {@code SuggestedRecipesViewModelFactory}, {@code LocalDate::now} is
 * read here and never inside the engine.
 */
public final class RecipeDetailViewModelFactory implements ViewModelProvider.Factory {

    private final SpmApplication app;
    private final long recipeId;

    /**
     * Creates the factory.
     *
     * @param application the running app, whose {@link SpmApplication} holds the repositories
     * @param recipeId    the id of the recipe to show, as the Intent carried it
     */
    public RecipeDetailViewModelFactory(@NonNull Application application, long recipeId) {
        this.app = SpmApplication.from(application);
        this.recipeId = recipeId;
    }

    /**
     * Creates the ViewModel; called once per screen, and not again after a rotation.
     *
     * @throws IllegalArgumentException if asked for any other ViewModel
     */
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(RecipeDetailViewModel.class)) {
            throw new IllegalArgumentException("Cannot create " + modelClass.getName());
        }
        AppPreferences preferences = AppPreferences.from(app);
        return modelClass.cast(new RecipeDetailViewModel(
                app.getRecipeRepository().observeById(recipeId),
                app.getPantryRepository().observeAll(),
                app::getStrictMatcher,
                preferences::isCountExpiredItems,
                LocalDate::now,
                app.getMatchExecutor()));
    }
}
