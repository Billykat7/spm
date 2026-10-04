package com.btk.spm.domain.matching;

import com.btk.spm.domain.MatchStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The matcher's verdict on one recipe: whether it can be made, and if not, exactly what is missing
 * or short. Screens render it; none of them decides it again (non-negotiable 1).
 *
 * <p>Immutable: record fields are final and the shortfalls are an unmodifiable copy.
 *
 * @param recipeId   the id of the recipe judged
 * @param status     {@link MatchStatus#forShortfalls} of the shortfalls' count: {@code CAN_MAKE} for
 *                   none, {@code ALMOST_THERE} for one, {@code CANNOT_MAKE} for two or more
 * @param shortfalls every required ingredient not covered, in the recipe's order
 * @param haveCount  how many of the recipe's ingredients are covered
 * @param needCount  how many ingredients the recipe requires
 */
public record MatchResult(long recipeId, MatchStatus status, List<Shortfall> shortfalls,
                          int haveCount, int needCount) {

    /**
     * Creates a result, keeping an unmodifiable copy of the shortfalls.
     *
     * @throws IllegalArgumentException if {@code status} or {@code shortfalls} is {@code null}, the
     *     counts do not add up ({@code haveCount + shortfalls.size() != needCount}), or the status
     *     is not the one {@link MatchStatus#forShortfalls} gives for the shortfalls, so no result can
     *     be {@code CAN_MAKE} with a shortfall or {@code ALMOST_THERE} with two
     */
    public MatchResult {
        if (status == null || shortfalls == null) {
            throw new IllegalArgumentException("Match result for recipe " + recipeId + " is incomplete");
        }
        if (haveCount < 0 || haveCount + shortfalls.size() != needCount) {
            throw new IllegalArgumentException("Recipe " + recipeId + ": " + haveCount + " covered and "
                    + shortfalls.size() + " short do not make " + needCount);
        }
        if (status != MatchStatus.forShortfalls(shortfalls.size())) {
            throw new IllegalArgumentException("Recipe " + recipeId + " is " + status + " with "
                    + shortfalls.size() + " shortfalls");
        }
        shortfalls = Collections.unmodifiableList(new ArrayList<>(shortfalls));
    }

    /**
     * Says whether the recipe can be made now: the only results the Suggested Recipes list shows.
     *
     * @return {@code true} if the status is {@link MatchStatus#CAN_MAKE}
     */
    public boolean canMake() {
        return status == MatchStatus.CAN_MAKE;
    }

    /**
     * Returns the one thing that keeps an almost-there recipe off the suggestions, for its own
     * section (Issue 27): "missing basil" or "short of flour".
     *
     * @return the single shortfall if the status is {@link MatchStatus#ALMOST_THERE}, otherwise empty
     */
    public Optional<Shortfall> missingOne() {
        return status == MatchStatus.ALMOST_THERE ? Optional.of(shortfalls.get(0)) : Optional.empty();
    }
}
