package com.btk.spm.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Pins how a stored {@code double} reads on screen: whole numbers without {@code .0}, at most two
 * decimals, the locale's decimal separator, and a non-breaking space before the unit.
 */
public class QuantityFormatterTest {

    @Test
    public void wholeAmounts_haveNoDecimalPart() {
        assertEquals("4", QuantityFormatter.formatAmount(4.0, Locale.UK));
        assertEquals("0", QuantityFormatter.formatAmount(0.0, Locale.UK));
    }

    @Test
    public void fractions_keepOnlyTheDigitsTheyNeed() {
        assertEquals("1.5", QuantityFormatter.formatAmount(1.5, Locale.UK));
        assertEquals("0.25", QuantityFormatter.formatAmount(0.25, Locale.UK));
        assertEquals("2.1", QuantityFormatter.formatAmount(2.10, Locale.UK));
    }

    @Test
    public void longFractions_areRoundedHalfUpToTwoDecimals() {
        assertEquals("0.33", QuantityFormatter.formatAmount(1.0 / 3.0, Locale.UK));
        assertEquals("0.67", QuantityFormatter.formatAmount(2.0 / 3.0, Locale.UK));
        assertEquals("0.13", QuantityFormatter.formatAmount(0.125, Locale.UK));
        // The classic floating-point sum still reads as the number a person meant
        assertEquals("0.3", QuantityFormatter.formatAmount(0.1 + 0.2, Locale.UK));
    }

    @Test
    public void largeAmounts_haveNoGroupingSeparator_soTheFormCanReadThemBack() {
        assertEquals("1500", QuantityFormatter.formatAmount(1500, Locale.UK));
        assertEquals("12500.5", QuantityFormatter.formatAmount(12500.5, Locale.US));
    }

    @Test
    public void theDecimalSeparator_followsTheLocale() {
        assertEquals("1,5", QuantityFormatter.formatAmount(1.5, Locale.GERMANY));
        assertEquals("1.5", QuantityFormatter.formatAmount(1.5, Locale.US));
    }

    @Test
    public void theDisplayPattern_putsANonBreakingSpaceBetweenAmountAndUnit() throws IOException {
        // format(Context, …) needs Android resources; this checks the pattern it fills, as shipped
        String strings = new String(Files.readAllBytes(Paths.get("src", "main", "res", "values", "strings.xml")),
                StandardCharsets.UTF_8);
        assertTrue("quantity_with_unit must be %1$s, a non-breaking space, %2$s",
                strings.contains("<string name=\"quantity_with_unit\">%1$s\\u00A0%2$s</string>"));
    }
}
