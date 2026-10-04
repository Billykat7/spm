package com.btk.spm.domain.matching;

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
         * Creates a partition, keeping unmodifiable copies of the three lists.
         *
         * @throws NullPointerException if a list is {@code null}
         */
        public Partition {
            canMake = Collections.unmodifiableList(new ArrayList<>(canMake));
            almostThere = Collections.unmodifiableList(new ArrayList<>(almostThere));
            cannotMake = Collections.unmodifiableList(new ArrayList<>(cannotMake));
        }
    }
}
