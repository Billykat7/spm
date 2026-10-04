package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * {@code ALMOST_THERE} (Issue 22): a recipe with exactly one shortfall, kept apart from the
 * suggestions by its own status and its own list (non-negotiable 3).
 *
 * <p>The deliverable is
 * {@link #canMakeAndAlmostThere_neverOverlap_overTheSeedAndTwoHundredRandomPantries}: the twenty
 * seed recipes against 200 random pantries, partitioned, and the strict list checked every time.
 */
public class AlmostThereTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final MatchOptions OPTIONS = MatchOptions.on(TODAY);
    private static final StrictMatcher MATCHER =
            new StrictMatcher(new IngredientNormaliser(Collections.emptyMap()), new UnitConverter());

    /** Three lines: 2 eggs, 250 g flour, 300 ml milk. */
    private static final RecipeSpec PANCAKES = new RecipeSpec(1, List.of(
            line("egg", 2, Unit.PCS), line("flour", 250, Unit.G), line("milk", 300, Unit.ML)));

    // The thresholds: 0, 1, 2 or more shortfalls

    @Test
    public void noShortfall_isCanMake() {
        MatchResult result =
                match(have("egg", 2, Unit.PCS), have("flour", 250, Unit.G), have("milk", 300, Unit.ML));

        assertSame(MatchStatus.CAN_MAKE, result.status());
        assertTrue(result.missingOne().isEmpty());
    }

    @Test
    public void oneIngredientMissing_isAlmostThere_andNamesIt() {
        MatchResult result = match(have("egg", 2, Unit.PCS), have("flour", 250, Unit.G));

        assertSame(MatchStatus.ALMOST_THERE, result.status());
        assertFalse(result.canMake());
        assertEquals("milk", result.missingOne().orElseThrow().required().name());
        assertTrue(result.missingOne().orElseThrow().isMissing());
    }

    @Test
    public void oneIngredientShort_isAlmostThere_andSaysWhatThereIs() {
        MatchResult result =
                match(have("egg", 2, Unit.PCS), have("flour", 200, Unit.G), have("milk", 300, Unit.ML));

        assertSame(MatchStatus.ALMOST_THERE, result.status());
        Shortfall flour = result.missingOne().orElseThrow();
        assertEquals("flour", flour.required().name());
        assertEquals(new CanonicalQuantity(200, UnitKind.MASS), flour.available());
    }

    @Test
    public void oneMissingAndOneShort_isCannotMake() {
        MatchResult result = match(have("egg", 1, Unit.PCS), have("flour", 250, Unit.G));

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        assertEquals(2, result.shortfalls().size());
        assertTrue(result.missingOne().isEmpty());
    }

    @Test
    public void everythingMissing_isCannotMake() {
        assertSame(MatchStatus.CANNOT_MAKE, match().status());
    }

    @Test
    public void theThresholds_areWrittenOnce() {
        assertSame(MatchStatus.CAN_MAKE, MatchStatus.forShortfalls(0));
        assertSame(MatchStatus.ALMOST_THERE, MatchStatus.forShortfalls(1));
        assertSame(MatchStatus.CANNOT_MAKE, MatchStatus.forShortfalls(2));
        assertSame(MatchStatus.CANNOT_MAKE, MatchStatus.forShortfalls(8));
        assertThrows(IllegalArgumentException.class, () -> MatchStatus.forShortfalls(-1));
    }

    @Test
    public void aResult_cannotClaimAStatusItsShortfallsDoNotGive() {
        Shortfall milk = new Shortfall(line("milk", 300, Unit.ML), null);
        Shortfall flour = new Shortfall(line("flour", 250, Unit.G), null);

        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(milk), 2, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.ALMOST_THERE, List.of(), 3, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.ALMOST_THERE, List.of(milk, flour), 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CANNOT_MAKE, List.of(milk), 2, 3));
    }

    // partition

    @Test
    public void partition_keepsInputOrder_inEachList_andLosesNothing() {
        List<MatchResult> results = Arrays.asList(
                result(1, 0), result(2, 1), result(3, 2), result(4, 0), result(5, 1), result(6, 3));

        MatchResults.Partition groups = MatchResults.partition(results);

        assertEquals(List.of(1L, 4L), ids(groups.canMake()));
        assertEquals(List.of(2L, 5L), ids(groups.almostThere()));
        assertEquals(List.of(3L, 6L), ids(groups.cannotMake()));
    }

    @Test
    public void partition_ofNothing_isThreeEmptyLists() {
        MatchResults.Partition groups = MatchResults.partition(List.of());

        assertTrue(groups.canMake().isEmpty());
        assertTrue(groups.almostThere().isEmpty());
        assertTrue(groups.cannotMake().isEmpty());
    }

    @Test
    public void partition_cannotBeChanged() {
        MatchResults.Partition groups = MatchResults.partition(List.of(result(1, 0), result(2, 1)));

        assertThrows(UnsupportedOperationException.class, () -> groups.canMake().add(result(3, 1)));
        assertThrows(UnsupportedOperationException.class, () -> groups.almostThere().clear());
        assertThrows(UnsupportedOperationException.class, () -> groups.cannotMake().add(result(4, 2)));
    }

    @Test
    public void aPartitionBuiltByHand_cannotPutAnAlmostThereRecipeAmongTheSuggestions() {
        MatchResult almost = result(1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new MatchResults.Partition(List.of(almost), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResults.Partition(List.of(), List.of(), List.of(almost)));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResults.Partition(List.of(), List.of(result(2, 0)), List.of()));
    }

    @Test
    public void partition_refusesAMissingList_orAMissingResult() {
        assertThrows(NullPointerException.class, () -> MatchResults.partition(null));
        assertThrows(NullPointerException.class,
                () -> MatchResults.partition(Arrays.asList(result(1, 0), null)));
        assertThrows(NullPointerException.class, () -> new MatchResults.Partition(null, List.of(), List.of()));
    }

    // The property: over the seed and 200 random pantries, the strict list never meets almost there

    @Test
    public void canMakeAndAlmostThere_neverOverlap_overTheSeedAndTwoHundredRandomPantries() {
        List<RecipeSpec> seed = SeedFixture.specs();
        List<RequiredIngredient> stock = everyIngredientOf(seed);
        // A fixed seed: the same 200 pantries on every run, so a failure can be replayed
        Random random = new Random(22);
        Map<MatchStatus, Integer> seen = new EnumMap<>(MatchStatus.class);

        for (int run = 0; run < 200; run++) {
            List<PantryEntry> pantry = randomPantry(stock, random);
            MatchOptions options = MatchOptions.on(TODAY, random.nextInt(4) == 0);
            List<MatchResult> results = SeedFixture.MATCHER.matchAll(pantry, seed, options);

            MatchResults.Partition groups = MatchResults.partition(results);

            String where = "pantry " + run + ": " + pantry;
            Set<MatchResult> suggested = Collections.newSetFromMap(new IdentityHashMap<>());
            suggested.addAll(groups.canMake());
            for (MatchResult almost : groups.almostThere()) {
                assertFalse(where + ": recipe " + almost.recipeId() + " is in both",
                        suggested.contains(almost));
                assertEquals(where, 1, almost.shortfalls().size());
            }
            for (MatchResult result : groups.canMake()) {
                assertSame(where, MatchStatus.CAN_MAKE, result.status());
                assertTrue(where + ": a suggestion with a shortfall", result.shortfalls().isEmpty());
            }
            for (MatchResult result : groups.cannotMake()) {
                assertTrue(where, result.shortfalls().size() >= 2);
            }
            assertEquals(where + ": nothing lost or added", seed.size(),
                    groups.canMake().size() + groups.almostThere().size() + groups.cannotMake().size());
            assertEquals(where + ": input order kept", ids(results), mergedInInputOrder(results, groups));
            for (MatchResult result : results) {
                seen.merge(result.status(), 1, Integer::sum);
            }
        }
        // The pantries must reach all three outcomes, or the property above proves nothing
        for (MatchStatus status : MatchStatus.values()) {
            assertTrue(status + " never came up in 4000 matches: " + seen,
                    seen.getOrDefault(status, 0) > 0);
        }
    }

    // Helpers

    private static MatchResult match(PantryEntry... pantry) {
        return MATCHER.match(Arrays.asList(pantry), PANCAKES, OPTIONS);
    }

    private static PantryEntry have(String name, double amount, Unit unit) {
        return new PantryEntry(name, new Quantity(amount, unit), null);
    }

    private static RequiredIngredient line(String name, double amount, Unit unit) {
        return new RequiredIngredient(name, new Quantity(amount, unit));
    }

    /** A valid result for recipe {@code id} with {@code shortfalls} shortfalls out of three lines. */
    private static MatchResult result(long id, int shortfalls) {
        List<Shortfall> missing = new ArrayList<>();
        for (int i = 0; i < shortfalls; i++) {
            missing.add(new Shortfall(line("item " + i, 1, Unit.PCS), null));
        }
        int need = Math.max(3, shortfalls);
        return new MatchResult(id, MatchStatus.forShortfalls(shortfalls), missing, need - shortfalls, need);
    }

    private static List<Long> ids(List<MatchResult> results) {
        List<Long> ids = new ArrayList<>();
        for (MatchResult result : results) {
            ids.add(result.recipeId());
        }
        return ids;
    }

    /** The groups' ids put back in the input's order, using each group's own order. */
    private static List<Long> mergedInInputOrder(List<MatchResult> input, MatchResults.Partition groups) {
        Map<MatchStatus, List<MatchResult>> byStatus = new EnumMap<>(MatchStatus.class);
        byStatus.put(MatchStatus.CAN_MAKE, new ArrayList<>(groups.canMake()));
        byStatus.put(MatchStatus.ALMOST_THERE, new ArrayList<>(groups.almostThere()));
        byStatus.put(MatchStatus.CANNOT_MAKE, new ArrayList<>(groups.cannotMake()));
        List<Long> merged = new ArrayList<>();
        for (MatchResult result : input) {
            merged.add(byStatus.get(result.status()).remove(0).recipeId());
        }
        return merged;
    }

    /** Each seed ingredient once, with the largest amount any recipe needs of it. */
    private static List<RequiredIngredient> everyIngredientOf(List<RecipeSpec> seed) {
        Map<String, RequiredIngredient> largest = new LinkedHashMap<>();
        for (RecipeSpec recipe : seed) {
            for (RequiredIngredient line : recipe.ingredients()) {
                RequiredIngredient held = largest.get(line.name());
                if (held == null || canonical(line) > canonical(held)) {
                    largest.put(line.name(), line);
                }
            }
        }
        return new ArrayList<>(largest.values());
    }

    /**
     * A pantry a person might hold: each seed ingredient with a chance that changes from pantry to
     * pantry, in an amount from half to twice the most any recipe needs, sometimes typed in capitals,
     * sometimes in another kind of unit, sometimes expired.
     */
    private static List<PantryEntry> randomPantry(List<RequiredIngredient> stock, Random random) {
        double chance = 0.3 + 0.7 * random.nextDouble();
        List<PantryEntry> pantry = new ArrayList<>();
        for (RequiredIngredient ingredient : stock) {
            if (random.nextDouble() >= chance) {
                continue;
            }
            Unit unit = ingredient.quantity().unit();
            if (random.nextInt(10) == 0) {
                unit = unit.kind() == UnitKind.MASS ? Unit.ML : Unit.G;
            }
            double amount = ingredient.quantity().amount() * (0.5 + 1.5 * random.nextDouble());
            String name = random.nextBoolean()
                    ? ingredient.name() : ingredient.name().toUpperCase(Locale.ROOT);
            LocalDate expiry = random.nextInt(8) == 0 ? TODAY.minusDays(1 + random.nextInt(5)) : null;
            pantry.add(new PantryEntry(name, new Quantity(amount, unit), expiry));
        }
        return pantry;
    }

    private static double canonical(RequiredIngredient line) {
        return line.quantity().amount() * line.quantity().unit().factorToCanonical();
    }
}
