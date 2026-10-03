package com.btk.spm.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks {@link Unit} and {@link UnitKind} against decision 5: three kinds, one canonical unit each,
 * and a fixed factor from every unit to its kind's canonical unit.
 */
@RunWith(Enclosed.class)
public class UnitTest {

    /** One row per unit: its kind, its factor and its machine symbol, exactly as decision 5 lists them. */
    @RunWith(Parameterized.class)
    public static class Decision5Table {

        @Parameters(name = "{0}: {1}, x{2} canonical, \"{3}\"")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][] {
                {Unit.G, UnitKind.MASS, 1.0, "g"},
                {Unit.KG, UnitKind.MASS, 1000.0, "kg"},
                {Unit.ML, UnitKind.VOLUME, 1.0, "ml"},
                {Unit.L, UnitKind.VOLUME, 1000.0, "l"},
                {Unit.TSP, UnitKind.VOLUME, 5.0, "tsp"},
                {Unit.TBSP, UnitKind.VOLUME, 15.0, "tbsp"},
                {Unit.CUP, UnitKind.VOLUME, 250.0, "cup"},
                {Unit.PCS, UnitKind.COUNT, 1.0, "pcs"},
            });
        }

        @Parameter(0) public Unit unit;
        @Parameter(1) public UnitKind kind;
        @Parameter(2) public double factor;
        @Parameter(3) public String symbol;

        @Test
        public void hasTheDecidedKind() {
            assertSame(kind, unit.kind());
        }

        @Test
        public void hasTheDecidedFactor() {
            assertEquals(factor, unit.factorToCanonical(), 0.0);
        }

        @Test
        public void parsesFromItsSymbol() {
            assertEquals(symbol, unit.symbol());
            assertSame(unit, Unit.fromSymbol(symbol));
        }
    }

    /** Rules that hold for every unit and every kind, whatever is added later. */
    public static class Invariants {

        @Test
        public void decision5TableCoversEveryUnit() {
            assertEquals("a Unit was added without a row in Decision5Table",
                    Unit.values().length, Decision5Table.rows().size());
        }

        @Test
        public void everyUnitHasExactlyOneKindAndAPositiveFactor() {
            for (Unit unit : Unit.values()) {
                assertNotNull(unit + " has no kind", unit.kind());
                assertTrue(unit + " has a non-positive factor", unit.factorToCanonical() > 0);
            }
        }

        @Test
        public void eachKindsCanonicalUnitIsOfThatKindWithFactorOne() {
            for (UnitKind kind : UnitKind.values()) {
                Unit canonical = kind.canonicalUnit();
                assertSame(kind + "'s canonical unit is of another kind", kind, canonical.kind());
                assertEquals(kind + "'s canonical unit", 1.0, canonical.factorToCanonical(), 0.0);
            }
        }

        @Test
        public void eachKindHasNoOtherUnitWithFactorOne() {
            for (Unit unit : Unit.values()) {
                if (unit.factorToCanonical() == 1.0) {
                    assertSame(unit + " has factor 1 but is not canonical", unit.kind().canonicalUnit(), unit);
                }
            }
        }

        @Test
        public void symbolsAndDisplayResourcesAreDistinct() {
            Set<String> symbols = new HashSet<>();
            Set<Integer> resources = new HashSet<>();
            for (Unit unit : Unit.values()) {
                assertTrue("duplicate symbol " + unit.symbol(), symbols.add(unit.symbol()));
                assertNotEquals(unit + " has no display string", 0, unit.symbolRes());
                assertTrue(unit + " shares a display string", resources.add(unit.symbolRes()));
            }
        }

        @Test
        public void fromSymbolIgnoresCaseAndSurroundingSpaces() {
            assertSame(Unit.KG, Unit.fromSymbol(" KG "));
            assertSame(Unit.TBSP, Unit.fromSymbol("Tbsp"));
        }

        @Test
        public void fromSymbolRefusesWhatIsNotASymbol() {
            for (String bad : new String[] {"grams", "", "  ", "k g", "G.", "tablespoon"}) {
                IllegalArgumentException e =
                        assertThrows(IllegalArgumentException.class, () -> Unit.fromSymbol(bad));
                assertTrue("message lists the accepted symbols", e.getMessage().contains("g, kg, ml"));
            }
        }

        @Test
        public void fromSymbolRefusesAMissingSymbol() {
            assertThrows(IllegalArgumentException.class, () -> Unit.fromSymbol(null));
        }

        @Test
        public void toStringIsTheSymbol() {
            assertEquals("tbsp", Unit.TBSP.toString());
        }
    }
}
