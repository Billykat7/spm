package com.btk.spm.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Pins the conversion between a picker's UTC-midnight selection and the day it means. */
public class PickerDatesTest {

    /** 2026-10-04T00:00:00Z, the selection {@code MaterialDatePicker} gives for 4 October 2026. */
    private static final long OCT_4_UTC_MIDNIGHT = 1_791_072_000_000L;
    private static final LocalDate OCT_4 = LocalDate.of(2026, 10, 4);

    @Test
    public void aSelection_isTheDayItsUtcMidnightFallsOn() {
        assertEquals(OCT_4, PickerDates.toLocalDate(OCT_4_UTC_MIDNIGHT));
    }

    @Test
    public void aDay_becomesItsUtcMidnight() {
        assertEquals(OCT_4_UTC_MIDNIGHT, PickerDates.toSelection(OCT_4));
    }

    @Test
    public void everyDayOfAYear_survivesTheRoundTrip() {
        for (LocalDate day = OCT_4; day.isBefore(OCT_4.plusYears(1)); day = day.plusDays(1)) {
            assertEquals(day, PickerDates.toLocalDate(PickerDates.toSelection(day)));
        }
    }

    @Test
    public void readingTheSelectionInAZoneWestOfUtc_wouldGiveThePreviousDay() {
        // Why the conversion must not use the device zone: in Los Angeles it is still 3 October
        LocalDate inLosAngeles = Instant.ofEpochMilli(OCT_4_UTC_MIDNIGHT)
                .atZone(ZoneId.of("America/Los_Angeles")).toLocalDate();

        assertNotEquals(OCT_4, inLosAngeles);
        assertEquals(OCT_4, PickerDates.toLocalDate(OCT_4_UTC_MIDNIGHT));
    }
}
