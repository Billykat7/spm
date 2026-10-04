package com.btk.spm.notifications;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Words the expiring-soon notification from the items the daily check found (Issue 29).
 *
 * <p>A pure function with no Android import, so its rules are tested on the JVM in milliseconds:
 * <ol>
 *   <li>nothing to say, no message: an empty list gives {@link Optional#empty()};</li>
 *   <li>the soonest first, expired ones before today's, then by name, so the item to use first is
 *       read first;</li>
 *   <li>at most {@link #MAX_NAMED} items named, then "and N more", so the body still fits a phone's
 *       expanded notification;</li>
 *   <li>the title counts every item, with the singular for one.</li>
 * </ol>
 * The words come from {@link ExpiryTexts}, so the app reads them from {@code strings.xml}.
 */
public final class ExpiryMessageBuilder {

    /** The most items the body names before "and N more". */
    public static final int MAX_NAMED = 5;

    /** Soonest first; the same day in name order, ignoring case, so the order never depends on the database's. */
    private static final Comparator<ExpiringItem> SOONEST_FIRST = Comparator
            .comparingLong(ExpiringItem::daysLeft)
            .thenComparing(ExpiringItem::name, String.CASE_INSENSITIVE_ORDER);

    private ExpiryMessageBuilder() {
        // A pure function; never instantiated
    }

    /**
     * Builds the notification's title and body.
     *
     * @param items the items expiring within the threshold, or already expired, in any order
     * @param texts the words to use
     * @return the message, or empty when {@code items} is empty and there is nothing to post
     * @throws NullPointerException if an argument or an item is {@code null}
     */
    public static Optional<ExpiryMessage> build(List<ExpiringItem> items, ExpiryTexts texts) {
        Objects.requireNonNull(texts, "texts");
        if (items.isEmpty()) {
            return Optional.empty();
        }
        List<ExpiringItem> sorted = new ArrayList<>(items);
        sorted.sort(SOONEST_FIRST);

        int named = Math.min(sorted.size(), MAX_NAMED);
        List<String> parts = new ArrayList<>(named);
        for (ExpiringItem item : sorted.subList(0, named)) {
            parts.add(texts.item(item.name(), texts.when(item.daysLeft())));
        }
        String body = String.join(texts.separator(), parts);
        if (sorted.size() > named) {
            body = texts.andMore(body, sorted.size() - named);
        }
        return Optional.of(new ExpiryMessage(texts.title(sorted.size()), body));
    }
}
