package com.btk.spm.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ExpiryMessageBuilder} on the JVM, with a fixed English {@link ExpiryTexts} that says what
 * {@code strings.xml} says: the empty case, singular and plural titles, each "when" wording, the order
 * and the five-name cap.
 */
public class ExpiryMessageBuilderTest {

    /** The app's English wording, written out so the test needs no Android resources. */
    static final ExpiryTexts ENGLISH = new ExpiryTexts() {
        @Override
        public String title(int count) {
            return count + (count == 1 ? " item expiring soon" : " items expiring soon");
        }

        @Override
        public String when(long daysLeft) {
            if (daysLeft < 0) {
                return "expired " + -daysLeft + (daysLeft == -1 ? " day ago" : " days ago");
            }
            if (daysLeft == 0) {
                return "today";
            }
            if (daysLeft == 1) {
                return "tomorrow";
            }
            return "in " + daysLeft + " days";
        }

        @Override
        public String item(String name, String when) {
            return name + " (" + when + ")";
        }

        @Override
        public String separator() {
            return ", ";
        }

        @Override
        public String andMore(String listed, int more) {
            return listed + " and " + more + " more";
        }
    };

    @Test
    public void nothingExpiring_givesNoMessage() {
        assertFalse(ExpiryMessageBuilder.build(List.of(), ENGLISH).isPresent());
    }

    @Test
    public void oneItem_hasASingularTitle() {
        ExpiryMessage message = build(new ExpiringItem("tomato", 1));

        assertEquals("1 item expiring soon", message.title());
        assertEquals("tomato (tomorrow)", message.body());
    }

    @Test
    public void twoItems_theIssuesExample() {
        ExpiryMessage message = build(new ExpiringItem("milk", 3), new ExpiringItem("tomato", 1));

        assertEquals("2 items expiring soon", message.title());
        assertEquals("tomato (tomorrow), milk (in 3 days)", message.body());
    }

    @Test
    public void everyWhen_isWorded() {
        ExpiryMessage message = build(new ExpiringItem("bread", 0), new ExpiringItem("yoghurt", -2),
                new ExpiringItem("cream", -1), new ExpiringItem("cheddar", 5));

        assertEquals("yoghurt (expired 2 days ago), cream (expired 1 day ago), bread (today), cheddar (in 5 days)",
                message.body());
    }

    @Test
    public void theSameDay_isInNameOrder_ignoringCase() {
        ExpiryMessage message = build(new ExpiringItem("spinach", 2), new ExpiringItem("Basil", 2),
                new ExpiringItem("apples", 2));

        assertEquals("apples (in 2 days), Basil (in 2 days), spinach (in 2 days)", message.body());
    }

    @Test
    public void fiveItems_areAllNamed() {
        ExpiryMessage message = build(items(5));

        assertEquals("5 items expiring soon", message.title());
        assertFalse(message.body().contains("more"));
        assertEquals(5, message.body().split(", ").length);
    }

    @Test
    public void sevenItems_nameTheFiveSoonest_andCountTheRest() {
        ExpiryMessage message = build(items(7));

        assertEquals("7 items expiring soon", message.title());
        assertEquals("item0 (today), item1 (tomorrow), item2 (in 2 days), item3 (in 3 days), "
                + "item4 (in 4 days) and 2 more", message.body());
    }

    @Test
    public void theBuilder_importsNothingFromAndroid() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "btk", "spm",
                "notifications", "ExpiryMessageBuilder.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("package com.btk.spm.notifications;"));
        assertFalse("ExpiryMessageBuilder must stay plain Java", source.matches("(?s).*\\bimport\\s+(android|androidx)\\..*"));
    }

    /** {@code count} items, the n-th expiring in n days, given in reverse so the builder must sort them. */
    private static ExpiringItem[] items(int count) {
        List<ExpiringItem> items = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            items.add(new ExpiringItem("item" + i, i));
        }
        return items.toArray(new ExpiringItem[0]);
    }

    private static ExpiryMessage build(ExpiringItem... items) {
        return ExpiryMessageBuilder.build(List.of(items), ENGLISH).orElseThrow();
    }
}
