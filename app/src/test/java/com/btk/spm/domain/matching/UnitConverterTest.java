package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link UnitConverter} on the JVM (Issue 19): every unit to its canonical amount by decision 5's
 * factors, same-kind sums, and the explicit refusal to add a mass to a volume.
 */
@RunWith(Enclosed.class)
public class UnitConverterTest {

    private static final UnitConverter CONVERTER = new UnitConverter();

    static Quantity q(double amount, Unit unit) {
        return new Quantity(amount, unit);
    }

    /** One row per unit, plus the issue's examples. */
    @RunWith(Parameterized.class)
    public static class ToCanonical {

        @Parameters(name = "{index}: {0} {1} is {2} {3}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                {250.0, Unit.G, 250.0, UnitKind.MASS},
                {1.0, Unit.KG, 1000.0, UnitKind.MASS},
                {0.25, Unit.KG, 250.0, UnitKind.MASS},
                {750.0, Unit.ML, 750.0, UnitKind.VOLUME},
                {1.5, Unit.L, 1500.0, UnitKind.VOLUME},
                {2.0, Unit.TSP, 10.0, UnitKind.VOLUME},
                {3.0, Unit.TBSP, 45.0, UnitKind.VOLUME},
                {2.0, Unit.CUP, 500.0, UnitKind.VOLUME},
                {0.5, Unit.CUP, 125.0, UnitKind.VOLUME},
                {6.0, Unit.PCS, 6.0, UnitKind.COUNT},
            });
        }

        @Parameter(0) public double amount;
        @Parameter(1) public Unit unit;
        @Parameter(2) public double canonicalAmount;
        @Parameter(3) public UnitKind kind;

        @Test
        public void toCanonical() {
            CanonicalQuantity canonical = CONVERTER.toCanonical(q(amount, unit));

            assertEquals(canonicalAmount, canonical.amount(), 1e-9);
            assertSame(kind, canonical.kind());
        }
    }

    /** Kinds, sums and the refusal to mix kinds. */
    public static class KindsAndSums {

        @Test
        public void sameKind_isTrueWithinAKind_andFalseAcrossKinds() {
            assertTrue(CONVERTER.sameKind(Unit.KG, Unit.G));
            assertTrue(CONVERTER.sameKind(Unit.CUP, Unit.ML));
            assertFalse(CONVERTER.sameKind(Unit.G, Unit.CUP));
            assertFalse(CONVERTER.sameKind(Unit.PCS, Unit.G));
        }

        @Test
        public void sum_addsRowsOfOneKind_inTheCanonicalUnit() {
            assertEquals(new CanonicalQuantity(300, UnitKind.MASS),
                    CONVERTER.sum(Arrays.asList(q(200, Unit.G), q(0.1, Unit.KG))));
        }

        @Test
        public void sum_ofSpoonsAndCups_isMillilitres() {
            assertEquals(new CanonicalQuantity(270, UnitKind.VOLUME),
                    CONVERTER.sum(Arrays.asList(q(1, Unit.CUP), q(1, Unit.TBSP), q(1, Unit.TSP))));
        }

        @Test
        public void sum_ofOneRow_isThatRow() {
            assertEquals(new CanonicalQuantity(6, UnitKind.COUNT), CONVERTER.sum(List.of(q(6, Unit.PCS))));
        }

        @Test
        public void sum_refusesMassAndVolume_namingBothKinds() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> CONVERTER.sum(Arrays.asList(q(200, Unit.G), q(1, Unit.CUP))));

            assertTrue(e.getMessage(), e.getMessage().contains("MASS"));
            assertTrue(e.getMessage(), e.getMessage().contains("VOLUME"));
            assertTrue(e.getMessage(), e.getMessage().contains("200 g") && e.getMessage().contains("1 cup"));
        }

        @Test
        public void sum_refusesAnEmptyList() {
            assertThrows(IllegalArgumentException.class, () -> CONVERTER.sum(Collections.emptyList()));
        }

        @Test
        public void sumByKind_keepsEachKindApart() {
            Map<UnitKind, CanonicalQuantity> totals = CONVERTER.sumByKind(
                    Arrays.asList(q(200, Unit.G), q(1, Unit.CUP), q(0.3, Unit.KG), q(2, Unit.TBSP)));

            assertEquals(2, totals.size());
            assertEquals(new CanonicalQuantity(500, UnitKind.MASS), totals.get(UnitKind.MASS));
            assertEquals(new CanonicalQuantity(280, UnitKind.VOLUME), totals.get(UnitKind.VOLUME));
        }

        @Test
        public void sumByKind_ofNothing_isEmpty() {
            assertTrue(CONVERTER.sumByKind(Collections.emptyList()).isEmpty());
        }

        @Test
        public void sumByKind_cannotBeChanged() {
            Map<UnitKind, CanonicalQuantity> totals = CONVERTER.sumByKind(List.of(q(1, Unit.G)));

            assertThrows(UnsupportedOperationException.class, () -> totals.remove(UnitKind.MASS));
        }

        @Test
        public void summedRows_compareWithRoundingErrorForgiven() {
            CanonicalQuantity have = CONVERTER.sum(Arrays.asList(q(0.1, Unit.G), q(0.2, Unit.G)));

            assertTrue(have.isAtLeast(CONVERTER.toCanonical(q(0.3, Unit.G))));
            assertTrue(CONVERTER.toCanonical(q(0.3, Unit.G)).isAtLeast(have));
        }

        @Test
        public void aMissingQuantity_isRefused() {
            assertThrows(NullPointerException.class, () -> CONVERTER.toCanonical(null));
            assertThrows(NullPointerException.class, () -> CONVERTER.sum(null));
            assertThrows(NullPointerException.class,
                    () -> CONVERTER.sumByKind(Arrays.asList(q(1, Unit.G), null)));
        }
    }
}
