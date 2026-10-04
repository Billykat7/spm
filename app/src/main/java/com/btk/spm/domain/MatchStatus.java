package com.btk.spm.domain;

/**
 * The verdict the strict matcher (Issue 20) gives a recipe against the pantry, decided only by how
 * many of its required ingredients are not covered.
 *
 * <p><b>Only {@link #CAN_MAKE} may be shown as suggested</b> (non-negotiable 1). {@link #ALMOST_THERE}
 * is the brief's optional bonus and is "clearly separated from the strict suggestions": it is shown,
 * if at all, under its own heading, and is never counted, sorted or mixed with the suggestions
 * (non-negotiable 3). Screens get the three groups from {@code MatchResults.partition} and never
 * filter by status themselves.
 */
public enum MatchStatus {

    /** Every required ingredient is in the pantry in at least the required quantity. */
    CAN_MAKE,

    /** Exactly one required ingredient is missing, or present but short of the required quantity. */
    ALMOST_THERE,

    /** Two or more required ingredients are missing or short. */
    CANNOT_MAKE;

    /**
     * Returns the status for a number of shortfalls: none is {@link #CAN_MAKE}, exactly one is
     * {@link #ALMOST_THERE}, two or more is {@link #CANNOT_MAKE}. The one place the thresholds are
     * written; the matcher and {@code MatchResult} both ask it.
     *
     * @param shortfalls how many required ingredients are missing or short
     * @return the status those shortfalls give
     * @throws IllegalArgumentException if {@code shortfalls} is negative
     */
    public static MatchStatus forShortfalls(int shortfalls) {
        if (shortfalls < 0) {
            throw new IllegalArgumentException("A recipe cannot have " + shortfalls + " shortfalls");
        }
        if (shortfalls == 0) {
            return CAN_MAKE;
        }
        return shortfalls == 1 ? ALMOST_THERE : CANNOT_MAKE;
    }
}
