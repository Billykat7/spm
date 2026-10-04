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
 * <p>Immutable: record fields are final and the lists are unmodifiable copies.
 *
 * @param recipeId   the id of the recipe judged
 * @param status     {@link MatchStatus#forShortfalls} of the shortfalls' count: {@code CAN_MAKE} for
 *                   none, {@code ALMOST_THERE} for one, {@code CANNOT_MAKE} for two or more
 * @param shortfalls every required ingredient not covered, in the recipe's order
 * @param haveCount  how many of the recipe's ingredients are covered
 * @param needCount  how many ingredients the recipe requires
 * @param checks     one {@link IngredientCheck} per required ingredient, in the recipe's order, the
 *                   covered ones included (Issue 25); empty only for a result built by hand with the
 *                   five-argument constructor. Every result {@link StrictMatcher} returns has them
 */
public record MatchResult(long recipeId, MatchStatus status, List<Shortfall> shortfalls,
                          int haveCount, int needCount, List<IngredientCheck> checks) {

    /**
     * Creates a result without per-ingredient checks, as tests that judge the status alone build one.
     * {@link #checks()} is then empty.
     *
     * @throws IllegalArgumentException as the full constructor does
     */
    public MatchResult(long recipeId, MatchStatus status, List<Shortfall> shortfalls, int haveCount, int needCount) {
        this(recipeId, status, shortfalls, haveCount, needCount, List.of());
    }

    /**
     * Creates a result, keeping unmodifiable copies of the shortfalls and the checks.
     *
     * @throws IllegalArgumentException if {@code status} or {@code shortfalls} is {@code null}, the
     *     counts do not add up ({@code haveCount + shortfalls.size() != needCount}), or the status
     *     is not the one {@link MatchStatus#forShortfalls} gives for the shortfalls, so no result can
     *     be {@code CAN_MAKE} with a shortfall or {@code ALMOST_THERE} with two; or, when there are
     *     checks, they are not one per required ingredient, or the ones not covered are not exactly the
     *     shortfalls, in order
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
        checks = Collections.unmodifiableList(new ArrayList<>(checksAgreeingWith(recipeId, shortfalls, needCount,
                checks)));
    }

    /**
     * Refuses checks that tell another story than the counts and the shortfalls: the screens trust
     * both, so they must never disagree.
     */
    private static List<IngredientCheck> checksAgreeingWith(long recipeId, List<Shortfall> shortfalls,
                                                            int needCount, List<IngredientCheck> checks) {
        if (checks == null) {
            throw new IllegalArgumentException("Match result for recipe " + recipeId + " has no list of checks");
        }
        if (checks.isEmpty()) {
            return checks;
        }
        List<Shortfall> notCovered = new ArrayList<>();
        for (IngredientCheck check : checks) {
            if (check == null) {
                throw new IllegalArgumentException("Recipe " + recipeId + " has a missing check");
            }
            if (!check.satisfied()) {
                notCovered.add(check.asShortfall());
            }
        }
        if (checks.size() != needCount || !notCovered.equals(shortfalls)) {
            throw new IllegalArgumentException("Recipe " + recipeId + ": " + checks.size() + " checks with "
                    + notCovered.size() + " not covered do not match " + needCount + " lines and "
                    + shortfalls.size() + " shortfalls");
        }
        return checks;
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
