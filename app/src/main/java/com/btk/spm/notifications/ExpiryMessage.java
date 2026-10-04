package com.btk.spm.notifications;

import java.util.Objects;

/**
 * What the expiring-soon notification says: its title and its body, already worded.
 *
 * @param title "2 items expiring soon"
 * @param body  "tomato (tomorrow), milk (in 3 days)"
 */
public record ExpiryMessage(String title, String body) {

    /**
     * Creates a message.
     *
     * @throws NullPointerException if either part is {@code null}
     */
    public ExpiryMessage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }
}
