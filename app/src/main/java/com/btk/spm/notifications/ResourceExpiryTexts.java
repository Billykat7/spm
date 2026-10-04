package com.btk.spm.notifications;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import com.btk.spm.R;

/**
 * The expiring-soon notification's words from {@code strings.xml}: plurals for every count, so "1 day"
 * and "1 item" read right, and nothing typed in Java.
 */
public final class ResourceExpiryTexts implements ExpiryTexts {

    private final Resources resources;

    /**
     * Reads the words from {@code resources}.
     *
     * @param resources the app's resources, in the device's language
     */
    public ResourceExpiryTexts(@NonNull Resources resources) {
        this.resources = resources;
    }

    @Override
    public String title(int count) {
        return resources.getQuantityString(R.plurals.expiry_alert_title, count, count);
    }

    @Override
    public String when(long daysLeft) {
        if (daysLeft < 0) {
            int ago = clamp(-daysLeft);
            return resources.getQuantityString(R.plurals.expiry_alert_expired_ago, ago, ago);
        }
        if (daysLeft == 0) {
            return resources.getString(R.string.expiry_alert_today);
        }
        if (daysLeft == 1) {
            return resources.getString(R.string.expiry_alert_tomorrow);
        }
        int days = clamp(daysLeft);
        return resources.getQuantityString(R.plurals.expiry_alert_in_days, days, days);
    }

    @Override
    public String item(String name, String when) {
        return resources.getString(R.string.expiry_alert_item, name, when);
    }

    @Override
    public String separator() {
        return resources.getString(R.string.expiry_alert_separator);
    }

    @Override
    public String andMore(String listed, int more) {
        return resources.getQuantityString(R.plurals.expiry_alert_and_more, more, listed, more);
    }

    /** A day count as the int plurals take; a pantry date is never two billion days away. */
    private static int clamp(long days) {
        return (int) Math.min(days, Integer.MAX_VALUE);
    }
}
