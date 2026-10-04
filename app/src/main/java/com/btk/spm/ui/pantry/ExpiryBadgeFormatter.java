package com.btk.spm.ui.pantry;

import android.content.res.Resources;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.R;
import com.btk.spm.util.ExpiryStatus;

/**
 * Turns an item's {@link ExpiryStatus} and day count into what its badge shows: the words, and the
 * theme colour roles for its background and its text.
 *
 * <p>It exists so the adapter stays thin: the adapter asks {@code ExpiryRules} for the status and
 * hands the answer here, and nothing on a row decides what "soon" means. The colours are theme
 * attributes, never values, so light and dark mode each get their own legible pair:
 * <ul>
 *   <li>{@link ExpiryStatus#EXPIRED}: {@code colorError} on {@code colorOnError}, "Expired 2 days ago";</li>
 *   <li>{@link ExpiryStatus#EXPIRING_SOON}: {@code colorTertiaryContainer}, "Expires today" or
 *       "Expires in 2 days";</li>
 *   <li>{@link ExpiryStatus#OK}: {@code colorSurfaceVariant}, "Expires in 10 days";</li>
 *   <li>{@link ExpiryStatus#NONE}: no badge at all.</li>
 * </ul>
 */
public final class ExpiryBadgeFormatter {

    /**
     * What one badge shows.
     *
     * @param text           the words on the badge
     * @param backgroundAttr the theme attribute of the badge's fill
     * @param textColorAttr  the theme attribute of its text, the "on" colour of the fill
     */
    public record Badge(@NonNull String text, @AttrRes int backgroundAttr, @AttrRes int textColorAttr) {
    }

    private ExpiryBadgeFormatter() {
        // Static helpers only; never instantiated
    }

    /**
     * Returns the badge for an item, or {@code null} when it should have none.
     *
     * @param resources where the strings and plurals are read from
     * @param status    the item's status, from {@code ExpiryRules.statusOf}
     * @param daysUntil the signed days to its expiry, from {@code ExpiryRules.daysUntil}; ignored for
     *                  {@link ExpiryStatus#NONE}
     * @return the badge, or {@code null} for an item with no expiry date
     */
    @Nullable
    public static Badge format(@NonNull Resources resources, @NonNull ExpiryStatus status, long daysUntil) {
        if (status == ExpiryStatus.NONE) {
            return null;
        }
        return new Badge(text(resources, status, daysUntil), backgroundAttr(status), textColorAttr(status));
    }

    /** The badge's words; the day count goes through plurals so 1 reads "day", not "days". */
    @NonNull
    private static String text(@NonNull Resources resources, @NonNull ExpiryStatus status, long daysUntil) {
        if (status == ExpiryStatus.EXPIRED) {
            int daysAgo = clamp(-daysUntil);
            return resources.getQuantityString(R.plurals.expiry_badge_expired_ago, daysAgo, daysAgo);
        }
        if (daysUntil == 0) {
            return resources.getString(R.string.expiry_badge_expires_today);
        }
        int days = clamp(daysUntil);
        return resources.getQuantityString(R.plurals.expiry_badge_expires_in, days, days);
    }

    /**
     * Returns the theme attribute of a badge's fill.
     *
     * @param status any status but {@link ExpiryStatus#NONE}, which has no badge
     * @return a {@code ?attr/color*} attribute
     */
    @AttrRes
    static int backgroundAttr(@NonNull ExpiryStatus status) {
        switch (status) {
            case EXPIRED:
                // colorError is AppCompat's attribute; the other roles are Material's
                return androidx.appcompat.R.attr.colorError;
            case EXPIRING_SOON:
                return com.google.android.material.R.attr.colorTertiaryContainer;
            case OK:
                return com.google.android.material.R.attr.colorSurfaceVariant;
            default:
                throw new IllegalArgumentException(status + " has no badge");
        }
    }

    /**
     * Returns the theme attribute of a badge's text: the "on" role that pairs with its fill.
     *
     * @param status any status but {@link ExpiryStatus#NONE}, which has no badge
     * @return a {@code ?attr/colorOn*} attribute
     */
    @AttrRes
    static int textColorAttr(@NonNull ExpiryStatus status) {
        switch (status) {
            case EXPIRED:
                return com.google.android.material.R.attr.colorOnError;
            case EXPIRING_SOON:
                return com.google.android.material.R.attr.colorOnTertiaryContainer;
            case OK:
                return com.google.android.material.R.attr.colorOnSurfaceVariant;
            default:
                throw new IllegalArgumentException(status + " has no badge");
        }
    }

    /** A day count as the int plurals take; a pantry date is never two billion days away. */
    private static int clamp(long days) {
        return (int) Math.min(days, Integer.MAX_VALUE);
    }
}
