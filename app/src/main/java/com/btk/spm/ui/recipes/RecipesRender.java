package com.btk.spm.ui.recipes;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.Objects;

/**
 * What the Recipes tab draws for one {@link UiState}: which of its three views is visible, and, for
 * the empty view, its message and whether it has the "Add ingredients" button.
 *
 * <p>A pure mapping with no view in it, so a JVM test can go through every state and check that
 * exactly one of the progress indicator, the list and the empty view is shown. A blank screen
 * (none) or a list under a spinner (two) cannot happen. {@code SuggestedRecipesFragment} only
 * applies the result to its binding.
 *
 * @param progress       whether the progress indicator is shown ({@link UiState.Loading} only)
 * @param list           whether the list is shown ({@link UiState.Content} only)
 * @param empty          whether the empty view is shown ({@link UiState.Empty} only)
 * @param emptyMessage   the empty view's message, or {@code 0} when it is hidden
 * @param emptyBody      the empty view's second sentence, or {@code 0} for none
 * @param addIngredients whether the empty view shows the "Add ingredients" button
 * @param rows           the rows to hand the adapter: {@code canMake}, or empty
 */
public record RecipesRender(boolean progress, boolean list, boolean empty, @StringRes int emptyMessage,
                            @StringRes int emptyBody,
                            boolean addIngredients, @NonNull List<MatchedRecipe> rows) {

    /**
     * Works out what to draw for {@code state}.
     *
     * @param state the ViewModel's state
     * @return exactly one of progress, list and empty set
     * @throws NullPointerException if {@code state} is {@code null}
     */
    @NonNull
    public static RecipesRender of(@NonNull UiState state) {
        Objects.requireNonNull(state, "state");
        if (state instanceof UiState.Content content) {
            return new RecipesRender(false, true, false, 0, 0, false, content.canMake());
        }
        if (state instanceof UiState.Empty emptyState) {
            EmptyReason reason = emptyState.reason();
            return new RecipesRender(false, false, true, reason.messageRes(), reason.bodyRes(),
                    reason.offersAddIngredients(), List.of());
        }
        // The sealed class has three kinds, so what is left is Loading
        return new RecipesRender(true, false, false, 0, 0, false, List.of());
    }

    /**
     * Returns how many recipes the toolbar title counts.
     *
     * @return the number of rows, 0 when nothing can be made
     */
    public int count() {
        return rows.size();
    }
}
