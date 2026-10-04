package com.btk.spm.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.btk.spm.util.ExpiryStatus;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Decision 6, row by row: each row is an expiry date and a threshold judged on a fixed day, with the
 * status and the day count it must give. {@code isExpired} is checked against {@code statusOf} on
 * every row, so the matcher and the badge cannot drift apart.
 */
@RunWith(Enclosed.class)
public class ExpiryRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    static final int THRESHOLD = 3;

    /** One date per row; {@code days} is {@code null} for an item with no date. */
    @RunWith(Parameterized.class)
    public static class Rows {

        @Parameters(name = "{0}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                    row("no date never expires", null, TODAY, THRESHOLD, ExpiryStatus.NONE, null),
                    row("a year ago is expired", TODAY.minusYears(1), TODAY, THRESHOLD, ExpiryStatus.EXPIRED, -365L),
                    row("yesterday is expired", TODAY.minusDays(1), TODAY, THRESHOLD, ExpiryStatus.EXPIRED, -1L),
                    row("today is expiring soon", TODAY, TODAY, THRESHOLD, ExpiryStatus.EXPIRING_SOON, 0L),
                    row("tomorrow is expiring soon", TODAY.plusDays(1), TODAY, THRESHOLD, ExpiryStatus.EXPIRING_SOON, 1L),
                    row("today + threshold is still expiring soon", TODAY.plusDays(THRESHOLD), TODAY, THRESHOLD,
                            ExpiryStatus.EXPIRING_SOON, 3L),
                    row("today + threshold + 1 is ok", TODAY.plusDays(THRESHOLD + 1), TODAY, THRESHOLD, ExpiryStatus.OK, 4L),
                    row("ten days away is ok", TODAY.plusDays(10), TODAY, THRESHOLD, ExpiryStatus.OK, 10L),
                    row("threshold 0: today is expiring soon", TODAY, TODAY, 0, ExpiryStatus.EXPIRING_SOON, 0L),
                    row("threshold 0: tomorrow is ok", TODAY.plusDays(1), TODAY, 0, ExpiryStatus.OK, 1L),
                    row("threshold 0: yesterday is expired", TODAY.minusDays(1), TODAY, 0, ExpiryStatus.EXPIRED, -1L),
                    row("a larger threshold: 7 days away with threshold 7 is soon", TODAY.plusDays(7), TODAY, 7,
                            ExpiryStatus.EXPIRING_SOON, 7L),
                    row("across a year boundary: 2 Jan from 31 Dec is 2 days", LocalDate.of(2027, 1, 2),
                            LocalDate.of(2026, 12, 31), THRESHOLD, ExpiryStatus.EXPIRING_SOON, 2L),
                    row("across a year boundary: 31 Dec from 1 Jan is expired", LocalDate.of(2026, 12, 31),
                            LocalDate.of(2027, 1, 1), THRESHOLD, ExpiryStatus.EXPIRED, -1L),
                    row("across a leap day: 1 Mar 2028 from 28 Feb is 2 days", LocalDate.of(2028, 3, 1),
                            LocalDate.of(2028, 2, 28), THRESHOLD, ExpiryStatus.EXPIRING_SOON, 2L),
            });
        }

        @Parameter(0)
        public String description;
        @Parameter(1)
        public LocalDate expiry;
        @Parameter(2)
        public LocalDate today;
        @Parameter(3)
        public int thresholdDays;
        @Parameter(4)
        public ExpiryStatus status;
        @Parameter(5)
        public Long days;

        @Test
        public void statusOf_followsDecision6() {
            assertEquals(description, status, ExpiryRules.statusOf(expiry, today, thresholdDays));
        }

        @Test
        public void daysUntil_isTheSignedCalendarDayCount() {
            if (expiry != null) {
                assertEquals(description, days.longValue(), ExpiryRules.daysUntil(expiry, today));
            }
        }

        @Test
        public void isExpired_agreesWithStatusOf() {
            assertEquals(description, status == ExpiryStatus.EXPIRED, ExpiryRules.isExpired(expiry, today));
        }

        private static Object[] row(String description, LocalDate expiry, LocalDate today, int thresholdDays,
                                    ExpiryStatus status, Long days) {
            return new Object[]{description, expiry, today, thresholdDays, status, days};
        }
    }

    /** What the rows cannot say. */
    public static class Limits {

        @Test
        public void aNegativeThreshold_isRefused() {
            assertThrows(IllegalArgumentException.class, () -> ExpiryRules.statusOf(TODAY, TODAY, -1));
        }

        @Test
        public void isExpired_ignoresTheThreshold_soTheMatcherNeedsNone() {
            for (int threshold = 0; threshold <= 30; threshold++) {
                for (int offset = -3; offset <= 3; offset++) {
                    LocalDate expiry = TODAY.plusDays(offset);
                    assertEquals(ExpiryRules.statusOf(expiry, TODAY, threshold) == ExpiryStatus.EXPIRED,
                            ExpiryRules.isExpired(expiry, TODAY));
                }
            }
        }
    }
}
