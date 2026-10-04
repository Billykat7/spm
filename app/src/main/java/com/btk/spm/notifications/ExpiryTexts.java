package com.btk.spm.notifications;

/**
 * The words the expiring-soon notification is made of, one method per phrase.
 *
 * <p>It exists so {@link ExpiryMessageBuilder} can decide the order, the cap and the punctuation
 * without importing Android: the app passes {@link ResourceExpiryTexts}, which reads
 * {@code strings.xml} and its plurals, and the JVM test passes a fixed English one. No visible string
 * is written in Java.
 */
public interface ExpiryTexts {

    /**
     * Returns the notification's title.
     *
     * @param count how many items it names, 1 or more
     * @return "1 item expiring soon", "2 items expiring soon"
     */
    String title(int count);

    /**
     * Returns when an item expires, relative to today.
     *
     * @param daysLeft 0 today, 1 tomorrow, more in the future, negative in the past
     * @return "today", "tomorrow", "in 3 days", "expired 1 day ago", "expired 2 days ago"
     */
    String when(long daysLeft);

    /**
     * Returns one item as the body lists it.
     *
     * @param name the item's name
     * @param when what {@link #when(long)} returned for it
     * @return "tomato (tomorrow)"
     */
    String item(String name, String when);

    /**
     * Returns what goes between two items of the list.
     *
     * @return ", " in English
     */
    String separator();

    /**
     * Returns the list followed by how many items were left out of it.
     *
     * @param listed the items named, already joined
     * @param more   how many more there are, 1 or more
     * @return "tomato (tomorrow), milk (in 3 days) and 2 more"
     */
    String andMore(String listed, int more);
}
