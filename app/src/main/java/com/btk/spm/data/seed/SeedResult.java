package com.btk.spm.data.seed;

/**
 * What {@link RecipeSeeder#seedIfEmpty()} did. An enum, not a boolean or a string, so a caller and a
 * test name the outcome rather than decode it (non-negotiable 7).
 */
public enum SeedResult {

    /** The recipe table was empty, and the whole asset has just been inserted. */
    SEEDED,

    /**
     * The recipe table was empty, and the recipes that passed every rule have been inserted; the
     * broken ones were left out and logged (Issue 31).
     */
    SEEDED_SKIPPING_SOME,

    /**
     * The recipe table was empty and stays empty: the asset could not be read, was not JSON, or held
     * no good recipe. The Recipes tab shows its error state (Issue 31).
     */
    NOTHING_TO_SEED,

    /** The recipe table already held recipes, so nothing was inserted. */
    ALREADY_SEEDED
}
