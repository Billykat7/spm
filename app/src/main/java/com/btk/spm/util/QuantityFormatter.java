package com.btk.spm.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.btk.spm.R;
import com.btk.spm.domain.Unit;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Turns a stored quantity into the text a person reads: {@code 4 pcs}, {@code 1.5 kg},
 * {@code 0.25 l}.
 *
 * <p>A quantity is a {@code double} (Issue 8), so printing it as it is gives {@code 4.0} for four
 * eggs and {@code 0.30000000000000004} after a sum. This class is the one place that decides how many
 * decimals are shown, so the pantry row (Issue 13) and the form that prefills an edit (Issue 15)
 * always agree. The unit is shown through its symbol resource ({@link Unit#symbolRes()}), never the
 * enum's name, after a non-breaking space so {@code 250 g} never wraps between the number and the
 * unit.
 */
public final class QuantityFormatter {

    /** The most decimals an amount shows: enough for {@code 0.25 l}, few enough to stay readable. */
    public static final int MAX_FRACTION_DIGITS = 2;

    private QuantityFormatter() {
        // Static helpers only; never instantiated
    }

    /**
     * Formats an amount with no trailing zeros and at most {@link #MAX_FRACTION_DIGITS} decimals,
     * rounding half up, in {@code locale}'s digits and decimal separator: {@code 4.0} is {@code "4"},
     * {@code 1.5} is {@code "1.5"} (or {@code "1,5"} in German), {@code 0.125} is {@code "0.13"}.
     *
     * <p>No grouping separator is used, so {@code 1500} stays {@code "1500"}: the text can be put
     * back into the form's quantity field (Issue 15) and read again as the same number.
     *
     * @param amount the amount to show
     * @param locale the locale whose decimal separator is used
     * @return the amount as text
     */
    @NonNull
    public static String formatAmount(double amount, @NonNull Locale locale) {
        // A new NumberFormat per call: instances are not thread-safe, and a list binds a few rows at most
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setGroupingUsed(false);
        return format.format(amount);
    }

    /**
     * Formats an amount and its unit for display, such as {@code "4 pcs"} or {@code "1.5 kg"}, in the
     * locale the app is showing.
     *
     * @param context any context, for the resources and the current locale
     * @param amount  the amount to show
     * @param unit    the unit the amount is measured in
     * @return the amount, a non-breaking space and the unit's display symbol
     */
    @NonNull
    public static String format(@NonNull Context context, double amount, @NonNull Unit unit) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return context.getString(R.string.quantity_with_unit,
                formatAmount(amount, locale), context.getString(unit.symbolRes()));
    }
}
