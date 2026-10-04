package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.DisplayQuantity;
import com.btk.spm.domain.DisplayUnit;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;
import com.btk.spm.domain.UnitsSystem;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link UnitConverter} on the JVM (Issue 19): every unit to its canonical amount by decision 5's
 * factors, same-kind sums, the explicit refusal to add a mass to a volume, and the display helper
 * for the units preference, which never changes a match.
 */
@RunWith(Enclosed.class)
public class UnitConverterTest {

    private static final UnitConverter CONVERTER = new UnitConverter();

    private static final StrictMatcher MATCHER =
            new StrictMatcher(new IngredientNormaliser(Collections.emptyMap()), CONVERTER);

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

    /** One row per display rule: {@code toPreferredDisplay(quantity, system)} as text. */
    @RunWith(Parameterized.class)
    public static class PreferredDisplay {

        private static final UnitsSystem METRIC = UnitsSystem.METRIC;
        private static final UnitsSystem IMPERIAL = UnitsSystem.IMPERIAL;

        @Parameters(name = "{index}: {0} {1} in {2} shows {3}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                {1500.0, Unit.G, METRIC, "1.5 kg"},
                {999.0, Unit.G, METRIC, "999 g"},
                {1000.0, Unit.G, METRIC, "1 kg"},
                {0.25, Unit.KG, METRIC, "250 g"},
                {750.0, Unit.ML, METRIC, "750 ml"},
                {1.5, Unit.L, METRIC, "1.5 l"},
                {3.0, Unit.CUP, METRIC, "3 cup"},
                {2.0, Unit.TBSP, METRIC, "2 tbsp"},
                {1.0, Unit.TSP, IMPERIAL, "1 tsp"},
                {3.0, Unit.CUP, IMPERIAL, "3 cup"},
                {1500.0, Unit.G, IMPERIAL, "52.9 oz"},
                {1.0, Unit.KG, IMPERIAL, "35.3 oz"},
                {28.349523125, Unit.G, IMPERIAL, "1 oz"},
                {500.0, Unit.ML, IMPERIAL, "16.9 fl oz"},
                {1.0, Unit.L, IMPERIAL, "33.8 fl oz"},
                {6.0, Unit.PCS, METRIC, "6 pcs"},
                {6.0, Unit.PCS, IMPERIAL, "6 pcs"},
            });
        }

        @Parameter(0) public double amount;
        @Parameter(1) public Unit unit;
        @Parameter(2) public UnitsSystem system;
        @Parameter(3) public String shown;

        @Test
        public void toPreferredDisplay() {
            assertEquals(shown, CONVERTER.toPreferredDisplay(q(amount, unit), system).toString());
        }

        @Test
        public void theCanonicalOverload_agreesExceptForSpoonsAndCups() {
            DisplayQuantity fromCanonical =
                    CONVERTER.toPreferredDisplay(CONVERTER.toCanonical(q(amount, unit)), system);
            boolean keptAsWritten = unit == Unit.TSP || unit == Unit.TBSP || unit == Unit.CUP;

            if (!keptAsWritten) {
                assertEquals(shown, fromCanonical.toString());
            } else {
                // Without the recipe's own unit, a volume can only be shown in ml, l or fl oz
                assertTrue(fromCanonical.toString(), fromCanonical.unit().kind() == UnitKind.VOLUME
                        && fromCanonical.unit() != DisplayUnit.of(unit));
            }
        }
    }

    /**
     * The units preference never changes a match. Each case goes through {@link StrictMatcher} itself,
     * once per preference, after every quantity has been displayed in it, and the
     * decision must be the same and right every time.
     */
    public static class DisplayNeverChangesMatching {

        /** pantry rows, required, can make. */
        private static final Object[][] CASES = {
            {List.of(q(1500, Unit.G)), q(1.5, Unit.KG), true},
            {List.of(q(1, Unit.KG)), q(250, Unit.G), true},
            {List.of(q(200, Unit.G)), q(250, Unit.G), false},
            {List.of(q(200, Unit.G), q(0.05, Unit.KG)), q(250, Unit.G), true},
            {List.of(q(1, Unit.L)), q(2, Unit.CUP), true},
            {List.of(q(400, Unit.ML)), q(2, Unit.CUP), false},
            {List.of(q(500, Unit.G)), q(2, Unit.CUP), false},
            {List.of(q(0.1, Unit.G), q(0.2, Unit.G)), q(0.3, Unit.G), true},
            {List.of(q(6, Unit.PCS)), q(6, Unit.PCS), true},
        };

        @Test
        public void eachCase_isDecidedTheSameUnderBothPreferences() {
            for (Object[] row : CASES) {
                @SuppressWarnings("unchecked")
                List<Quantity> pantry = (List<Quantity>) row[0];
                Quantity required = (Quantity) row[1];
                boolean canMake = (Boolean) row[2];
                for (UnitsSystem system : UnitsSystem.values()) {
                    assertEquals(pantry + " for " + required + " in " + system,
                            canMake, decide(pantry, required, system));
                }
            }
        }

        @Test
        public void matchingOnTheRoundedImperialFigure_wouldTurnEnoughIntoShort() {
            // Why display never feeds matching: 1500 g shows as 52.9 oz, and 52.9 oz is 1499.7 g
            DisplayQuantity shown = CONVERTER.toPreferredDisplay(q(1500, Unit.G), UnitsSystem.IMPERIAL);
            double grams = shown.amount() * shown.unit().factorToCanonical();

            assertTrue(grams < 1500);
            assertTrue(CONVERTER.toCanonical(q(1500, Unit.G))
                    .isAtLeast(CONVERTER.toCanonical(q(1.5, Unit.KG))));
        }

        @Test
        public void nothingThatMatches_acceptsADisplayValueOrAPreference() {
            List<String> offenders = new ArrayList<>();
            for (Class<?> type : List.of(CanonicalQuantity.class, UnitConverter.class, StrictMatcher.class,
                    MatchOptions.class, PantryEntry.class, RecipeSpec.class, MatchResult.class)) {
                for (Method method : type.getMethods()) {
                    if (method.getName().equals("toPreferredDisplay")) {
                        continue;
                    }
                    for (Class<?> parameter : method.getParameterTypes()) {
                        if (parameter == DisplayQuantity.class || parameter == DisplayUnit.class
                                || parameter == UnitsSystem.class) {
                            offenders.add(type.getSimpleName() + "." + method.getName());
                        }
                    }
                }
            }
            assertTrue("Only toPreferredDisplay may take display types: " + offenders, offenders.isEmpty());
        }

        /** Shows every quantity in {@code system}, as a screen would, then decides from canonical amounts. */
        private static boolean decide(List<Quantity> pantry, Quantity required, UnitsSystem system) {
            for (Quantity row : pantry) {
                CONVERTER.toPreferredDisplay(row, system);
            }
            CONVERTER.toPreferredDisplay(required, system);
            List<PantryEntry> entries = new ArrayList<>();
            for (Quantity row : pantry) {
                entries.add(new PantryEntry("flour", row, null));
            }
            RecipeSpec recipe = new RecipeSpec(1, List.of(new RequiredIngredient("flour", required)));
            return MATCHER.match(entries, recipe, MatchOptions.on(LocalDate.of(2026, 10, 4))).canMake();
        }
    }
}
