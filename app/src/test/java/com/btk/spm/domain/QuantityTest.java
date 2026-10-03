package com.btk.spm.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** Checks that a {@link Quantity} cannot hold an amount a pantry or a recipe could not have. */
public class QuantityTest {

    @Test
    public void keepsItsAmountAndUnit() {
        Quantity quantity = new Quantity(250, Unit.G);
        assertEquals(250.0, quantity.amount(), 0.0);
        assertSame(Unit.G, quantity.unit());
    }

    @Test
    public void refusesZero() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(0, Unit.G));
    }

    @Test
    public void refusesANegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(-2, Unit.PCS));
    }

    @Test
    public void refusesNaNAndInfinity() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(Double.NaN, Unit.ML));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(Double.POSITIVE_INFINITY, Unit.ML));
    }

    @Test
    public void refusesAMissingUnit() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(1, null));
    }

    @Test
    public void acceptsTheSmallestPositiveAmount() {
        assertEquals(Double.MIN_VALUE, new Quantity(Double.MIN_VALUE, Unit.G).amount(), 0.0);
    }

    @Test
    public void isEqualByAmountAndUnit() {
        assertEquals(new Quantity(1.5, Unit.KG), new Quantity(1.5, Unit.KG));
        assertEquals(new Quantity(1.5, Unit.KG).hashCode(), new Quantity(1.5, Unit.KG).hashCode());
        assertNotEquals(new Quantity(1.5, Unit.KG), new Quantity(1.5, Unit.L));
        assertNotEquals(new Quantity(1.5, Unit.KG), new Quantity(2, Unit.KG));
    }

    @Test
    public void doesNotConvertWhenComparing() {
        // 1 kg and 1000 g are the same mass, but conversion is Issue 19's job, not equality's
        assertNotEquals(new Quantity(1, Unit.KG), new Quantity(1000, Unit.G));
    }

    @Test
    public void printsTheAmountWithoutATrailingZeroAndTheSymbol() {
        assertEquals("250 g", new Quantity(250, Unit.G).toString());
        assertEquals("1.5 kg", new Quantity(1.5, Unit.KG).toString());
        assertEquals("0.25 cup", new Quantity(0.25, Unit.CUP).toString());
    }
}
