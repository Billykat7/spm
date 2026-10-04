package com.btk.spm.util;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Converts between a {@link LocalDate} and the selection a Material date picker works in.
 *
 * <p>{@code MaterialDatePicker} selects a day as the epoch milliseconds of that day's <em>midnight
 * in UTC</em>, whatever the device's time zone. Reading it back in the device zone shifts it: in
 * Los Angeles, midnight UTC on 4 October is still 3 October. So both directions go through
 * {@link ZoneOffset#UTC} and never through {@code ZoneId.systemDefault()}.
 */
public final class PickerDates {

    private PickerDates() {
        // Static helpers only; never instantiated
    }

    /**
     * Returns the day a picker selection stands for.
     *
     * @param utcMidnightMillis the picker's selection: midnight UTC of the chosen day, in epoch milliseconds
     * @return the chosen day
     */
    @NonNull
    public static LocalDate toLocalDate(long utcMidnightMillis) {
        return Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Returns the picker selection for {@code date}, so the picker opens on the day already chosen.
     *
     * @param date the day to select
     * @return midnight UTC of {@code date}, in epoch milliseconds
     */
    public static long toSelection(@NonNull LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
