package com.btk.spm.domain.matching;

import java.time.LocalDate;

/**
 * What a match is judged against besides the pantry and the recipe: the day, and whether expired
 * rows count (decision 6).
 *
 * <p>{@code today} is passed in, never read from the clock inside the engine, so a test fixes the day
 * and the answer never depends on when it runs.
 *
 * @param today          the day the match is made on; a row whose expiry is before it is expired
 * @param includeExpired whether expired rows still count (the Settings toggle, Issue 28; off by
 *                       default)
 */
public record MatchOptions(LocalDate today, boolean includeExpired) {

    /**
     * Creates match options.
     *
     * @throws IllegalArgumentException if {@code today} is {@code null}
     */
    public MatchOptions {
        if (today == null) {
            throw new IllegalArgumentException("Match options need today's date");
        }
    }

    /**
     * Options for {@code today} with the default of decision 6: expired rows do not count.
     *
     * @param today the day the match is made on
     * @return options that skip expired rows
     */
    public static MatchOptions on(LocalDate today) {
        return new MatchOptions(today, false);
    }

    /**
     * Options for {@code today}, counting expired rows or not.
     *
     * @param today          the day the match is made on
     * @param includeExpired whether expired rows still count
     * @return the options
     */
    public static MatchOptions on(LocalDate today, boolean includeExpired) {
        return new MatchOptions(today, includeExpired);
    }
}
