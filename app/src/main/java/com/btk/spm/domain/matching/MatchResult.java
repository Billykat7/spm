package com.btk.spm.domain.matching;

import com.btk.spm.domain.MatchStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The matcher's verdict on one recipe: whether it can be made, and if not, exactly what is missing
 * or short. Screens render it; none of them decides it again (non-negotiable 1).
 *
 * <p>Immutable: record fields are final and the shortfalls are an unmodifiable copy.
 *
 * @param recipeId   the id of the recipe judged
 * @param status     {@link MatchStatus#CAN_MAKE} when {@code shortfalls} is empty, otherwise
 *                   {@link MatchStatus#CANNOT_MAKE} (Issue 22 adds {@code ALMOST_THERE})
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
     *     disagrees with the shortfalls
     */
    public MatchResult {
        if (status == null || shortfalls == null) {
            throw new IllegalArgumentException("Match result for recipe " + recipeId + " is incomplete");
        }
        if (haveCount < 0 || haveCount + shortfalls.size() != needCount) {
            throw new IllegalArgumentException("Recipe " + recipeId + ": " + haveCount + " covered and "
                    + shortfalls.size() + " short do not make " + needCount);
        }
        if ((status == MatchStatus.CAN_MAKE) != shortfalls.isEmpty()) {
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
}
