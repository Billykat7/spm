package com.btk.spm.data.seed;

/**
 * What {@link RecipeSeeder#seedIfEmpty()} did. An enum, not a boolean or a string, so a caller and a
 * test name the outcome rather than decode it (non-negotiable 7).
 */
public enum SeedResult {

    /** The recipe table was empty, and the whole asset has just been inserted. */
    SEEDED,

    /** The recipe table already held recipes, so nothing was inserted. */
    ALREADY_SEEDED
}
