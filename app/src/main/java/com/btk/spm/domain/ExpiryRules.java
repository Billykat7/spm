package com.btk.spm.domain;

import com.btk.spm.util.ExpiryStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Decision 6 in code: what "expired" and "expiring soon" mean, decided once.
 *
 * <p>An item is <b>expired</b> when its expiry date is before today, and <b>expiring soon</b> when it
 * is today or at most the threshold's number of days away; an item with no date never expires. The
 * pantry badge (Issue 17), the matcher (Issue 20) and the notification (Issue 29) all ask this class,
 * so they can never disagree about the same item on the same day.
 *
 * <p>Pure Java with no clock: {@code today} is always a parameter, chosen by the caller, so the rules
 * are tested on fixed dates and the answer never depends on when a test runs.
 */
public final class ExpiryRules {

    private ExpiryRules() {
        // Static rules only; never instantiated
    }

    /**
     * Returns where an item stands on {@code today}.
     *
     * @param expiry        the item's expiry date, or {@code null} when it never expires
     * @param today         the day to judge it on
     * @param thresholdDays how many days ahead still count as expiring soon, 0 or more; with 0, only
     *                      today does
     * @return {@link ExpiryStatus#NONE} for no date; {@link ExpiryStatus#EXPIRED} before today;
     *     {@link ExpiryStatus#EXPIRING_SOON} from today to {@code thresholdDays} days away, both ends
     *     included; {@link ExpiryStatus#OK} after that
     * @throws IllegalArgumentException if {@code thresholdDays} is negative
     */
    public static ExpiryStatus statusOf(LocalDate expiry, LocalDate today, int thresholdDays) {
        if (thresholdDays < 0) {
            throw new IllegalArgumentException("thresholdDays must be 0 or more, was " + thresholdDays);
        }
        if (expiry == null) {
            return ExpiryStatus.NONE;
        }
        long days = daysUntil(expiry, today);
        if (days < 0) {
            return ExpiryStatus.EXPIRED;
        }
        return days <= thresholdDays ? ExpiryStatus.EXPIRING_SOON : ExpiryStatus.OK;
    }

    /**
     * Returns how many days are left until {@code expiry}: 0 on the day itself, negative once it has
     * passed ({@code -1} the day after). Calendar days, so a year boundary counts like any other.
     *
     * @param expiry the expiry date, never {@code null}
     * @param today  the day to count from
     * @return the signed number of days from {@code today} to {@code expiry}
     */
    public static long daysUntil(LocalDate expiry, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiry);
    }

    /**
     * Tells whether an item has expired on {@code today}; the matcher calls this (Issue 20), so it is
     * exactly {@code statusOf(...) == EXPIRED}, whatever the threshold.
     *
     * @param expiry the item's expiry date, or {@code null} when it never expires
     * @param today  the day to judge it on
     * @return {@code true} only when there is a date and it is before {@code today}
     */
    public static boolean isExpired(LocalDate expiry, LocalDate today) {
        return expiry != null && expiry.isBefore(today);
    }
}
