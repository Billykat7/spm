package com.btk.spm.ui.recipes;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the Recipes tab shows, one of three states and nothing in between.
 *
 * <ul>
 *   <li>{@link Loading}: the pantry or the recipes have not been read yet, so nothing can be said;</li>
 *   <li>{@link Empty}: both have been read and there is nothing to show, neither a recipe that can
 *       be made nor one that is one ingredient short; it carries the reason;</li>
 *   <li>{@link Content}: at least one recipe can be made, or is one ingredient short (Issue 27).</li>
 * </ul>
 *
 * <p>A sealed class, so the three are the only kinds there can be and each is a separate type: a
 * screen cannot show a list while it says it is loading. {@link Content} carries the almost-there
 * recipes beside the suggestions, in a separate list, never mixed with them: the tab shows them only
 * in their own section under their own heading (Issue 27).
 */
public abstract sealed class UiState permits UiState.Loading, UiState.Empty, UiState.Content {

    private UiState() {
        // Only the three states below
    }

    /**
     * The first state, kept until both the pantry and the recipes have been read. It is emitted once
     * and never again: a later match keeps the previous state on screen until its result lands, so
     * the list never flashes to a spinner (Issue 24).
     */
    public static final class Loading extends UiState {

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

    /**
     * Both sources have been read and there is nothing to show: no recipe can be made and none is one
     * ingredient short. It carries the {@link EmptyReason}, which picks the full-screen message.
     */
    public static final class Empty extends UiState {

        private final EmptyReason reason;

        /**
         * Creates the state.
         *
         * @param reason why nothing can be shown, which picks the message and the button
         * @throws NullPointerException if {@code reason} is {@code null}
         */
        public Empty(@NonNull EmptyReason reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        /**
         * Returns why nothing can be shown.
         *
         * @return the reason, never {@code null}
         */
        @NonNull
        public EmptyReason reason() {
            return reason;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Empty other && reason == other.reason;
        }

        @Override
        public int hashCode() {
            return reason.hashCode();
        }

        @NonNull
        @Override
        public String toString() {
            return "Empty(" + reason + ")";
        }
    }

    /**
     * Something to show: at least one recipe that can be made, or at least one that is one ingredient
     * short. Both lists come straight from {@code MatchResults.partition}: {@link #canMake()} is its
     * {@code canMake} list and {@link #almostThere()} its {@code almostThere} list, each in the recipes'
     * order. The two never share a recipe. With {@code canMake} empty the tab shows the brief's
     * zero-match sentence as its first row, above the "Almost there" section (Issue 27).
     */
    public static final class Content extends UiState {

        private final List<MatchedRecipe> canMake;
        private final List<MatchedRecipe> almostThere;

        /**
         * Creates the state, keeping unmodifiable copies of both lists.
         *
         * @param canMake     the suggestions; may be empty when {@code almostThere} is not
         * @param almostThere the recipes one ingredient short; may be empty when {@code canMake} is not
         * @throws IllegalArgumentException if both are empty, which is {@link Empty}
         */
        public Content(@NonNull List<MatchedRecipe> canMake, @NonNull List<MatchedRecipe> almostThere) {
            if (canMake.isEmpty() && almostThere.isEmpty()) {
                throw new IllegalArgumentException("Content needs a recipe to show; use Empty");
            }
            this.canMake = Collections.unmodifiableList(new ArrayList<>(canMake));
            this.almostThere = Collections.unmodifiableList(new ArrayList<>(almostThere));
        }

        /**
         * Returns the recipes that can be made now: the only list the tab shows as suggested, and the
         * only one counted in its title.
         *
         * @return the suggestions, unmodifiable, possibly empty
         */
        @NonNull
        public List<MatchedRecipe> canMake() {
            return canMake;
        }

        /**
         * Returns the recipes one ingredient short, shown only under their own heading and never
         * counted with {@link #canMake()} (Issue 27).
         *
         * @return the almost-there recipes, unmodifiable, possibly empty
         */
        @NonNull
        public List<MatchedRecipe> almostThere() {
            return almostThere;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Content other)) {
                return false;
            }
            return canMake.equals(other.canMake) && almostThere.equals(other.almostThere);
        }

        @Override
        public int hashCode() {
            return Objects.hash(canMake, almostThere);
        }

        @NonNull
        @Override
        public String toString() {
            return "Content{canMake=" + canMake.size() + ", almostThere=" + almostThere.size() + "}";
        }
    }
}
