package com.btk.spm.ui.recipes;

import androidx.annotation.NonNull;

import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.matching.MatchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the recipe detail screen shows: nothing yet, a recipe with its verdict, or the news that the
 * recipe does not exist.
 *
 * <p>A sealed class, so these three are the only states and each is its own type, as with the
 * Recipes tab's {@code UiState}.
 */
public abstract sealed class DetailUiState permits DetailUiState.Loading, DetailUiState.NotFound, DetailUiState.Loaded {

    private DetailUiState() {
        // Only the three states below
    }

    /** The recipe or the pantry has not been read yet. Shown as an empty list, for a moment. */
    public static final class Loading extends DetailUiState {

        /** The one instance; the state carries nothing. */
        public static final Loading INSTANCE = new Loading();

        private Loading() {
        }

        @NonNull
        @Override
        public String toString() {
            return "Loading";
        }
    }

    /** No recipe has this id; the screen says so and closes. */
    public static final class NotFound extends DetailUiState {

        /** The one instance; the state carries nothing. */
        public static final NotFound INSTANCE = new NotFound();

        private NotFound() {
        }

        @NonNull
        @Override
        public String toString() {
            return "NotFound";
        }
    }

    /**
     * The recipe, the matcher's verdict on it against the pantry now, and one row per ingredient built
     * from that verdict.
     */
    public static final class Loaded extends DetailUiState {

        private final RecipeWithIngredients recipe;
        private final MatchResult result;
        private final List<IngredientRow> rows;

        /**
         * Creates the state, keeping an unmodifiable copy of the rows.
         *
         * @param recipe the recipe with its ingredients and steps
         * @param result the matcher's verdict on it
         * @param rows   one row per ingredient, in the recipe's order
         */
        public Loaded(@NonNull RecipeWithIngredients recipe, @NonNull MatchResult result,
                      @NonNull List<IngredientRow> rows) {
            this.recipe = Objects.requireNonNull(recipe, "recipe");
            this.result = Objects.requireNonNull(result, "result");
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }

        /**
         * Returns the recipe.
         *
         * @return the recipe with its ingredients and steps
         */
        @NonNull
        public RecipeWithIngredients recipe() {
            return recipe;
        }

        /**
         * Returns the matcher's verdict, which the header's status line shows.
         *
         * @return the result for this recipe
         */
        @NonNull
        public MatchResult result() {
            return result;
        }

        /**
         * Returns the ingredient rows.
         *
         * @return one per ingredient, in the recipe's order, unmodifiable
         */
        @NonNull
        public List<IngredientRow> rows() {
            return rows;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Loaded other && recipe.equals(other.recipe) && result.equals(other.result)
                    && rows.equals(other.rows);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipe, result, rows);
        }

        @NonNull
        @Override
        public String toString() {
            return "Loaded{" + recipe.getRecipe().getName() + ", " + result.status() + "}";
        }
    }
}
