package com.btk.spm.notifications;

import java.util.Objects;

/**
 * One pantry item the expiring-soon alert names: what it is called and how many days it has left.
 *
 * <p>{@code ExpiryCheckWorker} builds these from the rows the expiry rule picked (decision 6, Issue 17),
 * so the alert and the pantry badges always agree on the same item on the same day.
 *
 * @param name     the item's name as the user typed it
 * @param daysLeft days until it expires: 0 today, 1 tomorrow, negative once it has expired
 */
public record ExpiringItem(String name, long daysLeft) {

    /**
     * Creates an item.
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public ExpiringItem {
        Objects.requireNonNull(name, "name");
    }
}
