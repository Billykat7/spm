package com.btk.spm.domain;

/**
 * The verdict the strict matcher (Issue 20) gives a recipe against the pantry.
 *
 * <p>Only {@link #CAN_MAKE} puts a recipe on the Suggested Recipes list (non-negotiable 1).
 * {@link #ALMOST_THERE} is shown, if at all, under its own heading and is never counted, sorted or
 * mixed with the suggestions (non-negotiable 3).
 */
public enum MatchStatus {

    /** Every required ingredient is in the pantry in at least the required quantity. */
    CAN_MAKE,

    /** Exactly one required ingredient is missing, or present but short of the required quantity. */
    ALMOST_THERE,

    /** Two or more required ingredients are missing or short. */
    CANNOT_MAKE
}
