package com.btk.spm.domain.matching;

import com.btk.spm.domain.MatchStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Splits match results into the three groups a screen may show, so no screen ever filters results
 * by status itself (non-negotiables 1 and 3).
 *
 * <p>The Suggested Recipes list shows {@link Partition#canMake()} and nothing else, and counts only
 * it. The "Almost there" section (Issue 27) shows {@link Partition#almostThere()} under its own
 * heading. The brief allows that list for bonus credit only if it is "clearly separated from the
 * strict suggestions", and here the separation is in the types: the two come out as different lists
 * and no result can be in both.
 */
public final class MatchResults {

    private MatchResults() {
        // Static helper; never instantiated
    }

    /**
     * Splits results by status, each group in the order the results came in.
     *
     * @param results the matcher's results, such as {@link StrictMatcher#matchAll}'s
     * @return the three groups; disjoint, and together exactly {@code results}
     * @throws NullPointerException if {@code results} or a result in it is {@code null}
     */
    public static Partition partition(List<MatchResult> results) {
        List<MatchResult> canMake = new ArrayList<>();
        List<MatchResult> almostThere = new ArrayList<>();
        List<MatchResult> cannotMake = new ArrayList<>();
        for (MatchResult result : Objects.requireNonNull(results, "results")) {
            switch (Objects.requireNonNull(result, "result").status()) {
                case CAN_MAKE -> canMake.add(result);
                case ALMOST_THERE -> almostThere.add(result);
                case CANNOT_MAKE -> cannotMake.add(result);
            }
        }
        return new Partition(canMake, almostThere, cannotMake);
    }

    /**
     * The three groups of a {@link #partition}, each unmodifiable and in input order.
     *
     * @param canMake     the suggestions: every result with status {@code CAN_MAKE}, and no shortfall
     * @param almostThere every result with status {@code ALMOST_THERE}: exactly one shortfall
     * @param cannotMake  every result with status {@code CANNOT_MAKE}: two or more shortfalls
     */
    public record Partition(List<MatchResult> canMake, List<MatchResult> almostThere,
                            List<MatchResult> cannotMake) {

        /**
         * Creates a partition, keeping unmodifiable copies of the three lists. Each list may only hold
         * results of its own status, so a partition built by hand cannot put an almost-there recipe
         * among the suggestions either.
         *
         * @throws NullPointerException if a list or a result in it is {@code null}
         * @throws IllegalArgumentException if a result is in the list of another status
         */
        public Partition {
            canMake = only(MatchStatus.CAN_MAKE, canMake);
            almostThere = only(MatchStatus.ALMOST_THERE, almostThere);
            cannotMake = only(MatchStatus.CANNOT_MAKE, cannotMake);
        }

        private static List<MatchResult> only(MatchStatus status, List<MatchResult> results) {
            List<MatchResult> copy = new ArrayList<>(Objects.requireNonNull(results, "results"));
            for (MatchResult result : copy) {
                if (Objects.requireNonNull(result, "result").status() != status) {
                    throw new IllegalArgumentException("Recipe " + result.recipeId() + " is "
                            + result.status() + ", not " + status);
                }
            }
            return Collections.unmodifiableList(copy);
        }
    }
}
