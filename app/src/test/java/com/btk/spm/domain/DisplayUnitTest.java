package com.btk.spm.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.btk.spm.R;

import org.junit.Test;

/**
 * {@link DisplayUnit} and {@link DisplayQuantity} (Issue 19): every stored unit is shown exactly as
 * {@link Unit} defines it, and the two ounces carry their exact definitions.
 */
public class DisplayUnitTest {

    @Test
    public void everyStoredUnit_isShownWithItsOwnKindFactorAndSymbol() {
        for (Unit unit : Unit.values()) {
            DisplayUnit shown = DisplayUnit.of(unit);

            assertEquals(unit.name(), shown.name());
            assertSame(unit.name(), unit.kind(), shown.kind());
            assertEquals(unit.name(), unit.factorToCanonical(), shown.factorToCanonical(), 0.0);
            assertEquals(unit.name(), unit.symbol(), shown.symbol());
            assertEquals(unit.name(), unit.symbolRes(), shown.symbolRes());
        }
    }

    @Test
    public void theOunces_areDisplayOnly_withTheirExactDefinitions() {
        assertEquals(DisplayUnit.values().length, Unit.values().length + 2);
        assertSame(UnitKind.MASS, DisplayUnit.OZ.kind());
        assertEquals(28.349523125, DisplayUnit.OZ.factorToCanonical(), 0.0);
        assertEquals(R.string.unit_oz, DisplayUnit.OZ.symbolRes());
        assertSame(UnitKind.VOLUME, DisplayUnit.FL_OZ.kind());
        assertEquals(29.5735295625, DisplayUnit.FL_OZ.factorToCanonical(), 0.0);
        assertEquals(R.string.unit_fl_oz, DisplayUnit.FL_OZ.symbolRes());
        // Not a stored unit: the seed and the form can never name one
        assertThrows(IllegalArgumentException.class, () -> Unit.fromSymbol("oz"));
    }

    @Test
    public void aDisplayQuantity_readsAsTheAmountAndSymbol() {
        assertEquals("52.9 oz", new DisplayQuantity(52.9, DisplayUnit.OZ).toString());
        assertEquals("1.5 kg", new DisplayQuantity(1.5, DisplayUnit.KG).toString());
        assertEquals("16.9 fl oz", new DisplayQuantity(16.9, DisplayUnit.FL_OZ).toString());
    }

    @Test
    public void aDisplayQuantity_refusesANonFiniteAmountOrAMissingUnit() {
        assertThrows(IllegalArgumentException.class, () -> new DisplayQuantity(Double.NaN, DisplayUnit.G));
        assertThrows(IllegalArgumentException.class,
                () -> new DisplayQuantity(Double.POSITIVE_INFINITY, DisplayUnit.G));
        assertThrows(IllegalArgumentException.class, () -> new DisplayQuantity(1, null));
    }
}
