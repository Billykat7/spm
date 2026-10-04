package com.btk.spm.util;

/**
 * Where a pantry item stands against its expiry date, as {@code domain.ExpiryRules.statusOf} decides
 * it (decision 6).
 *
 * <p>The badge on a pantry row (Issue 17), the expiring-soon notification (Issue 29) and the
 * matcher's treatment of expired items (Issue 20) all start from this one value, so they cannot
 * disagree about an item. It is never stored: it depends on today's date, so it is worked out each
 * time it is shown.
 */
public enum ExpiryStatus {

    /** No expiry date: the item never expires and shows no badge. */
    NONE,

    /** The date is further away than the expiring-soon threshold. */
    OK,

    /** The date is today or within the threshold: use it first. */
    EXPIRING_SOON,

    /** The date is before today. */
    EXPIRED
}
