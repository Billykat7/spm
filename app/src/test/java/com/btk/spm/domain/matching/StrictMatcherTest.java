package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.data.seed.RecipeJsonParser;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link StrictMatcher} on the JVM (Issue 20): the core cases that make the strict rule believable.
 * Issue 21 adds the full table of the marker's cases.
 *
 * <p>The five-ingredient recipe is the seed's Tomato pasta: 200 g pasta, 4 tomatoes, 2 garlic cloves,
 * 2 tbsp olive oil and 2 g salt. The day is fixed, never read from the clock.
 */
public class StrictMatcherTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final MatchOptions OPTIONS = MatchOptions.on(TODAY);

    private static final StrictMatcher MATCHER = new StrictMatcher(
            new IngredientNormaliser(Map.of("cilantro", "coriander")), new UnitConverter());

    private static final RecipeSpec TOMATO_PASTA = recipe(2,
            need("pasta", 200, Unit.G),
            need("tomato", 4, Unit.PCS),
            need("garlic", 2, Unit.PCS),
            need("olive oil", 2, Unit.TBSP),
            need("salt", 2, Unit.G));

    // The brief's own example: five needed, four present

    @Test
    public void fourOfFiveIngredients_isNotSuggested() {
        MatchResult result = MATCHER.match(pantry(
                have("pasta", 500, Unit.G),
                have("tomato", 6, Unit.PCS),
                have("garlic", 5, Unit.PCS),
                have("olive oil", 500, Unit.ML)), TOMATO_PASTA, OPTIONS);

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        assertFalse(result.canMake());
        assertEquals(1, result.shortfalls().size());
        assertEquals("salt", result.shortfalls().get(0).required().name());
        assertTrue(result.shortfalls().get(0).isMissing());
        assertEquals(4, result.haveCount());
        assertEquals(5, result.needCount());
        assertEquals(2, result.recipeId());
    }

    @Test
    public void allFiveInExactQuantities_canMake() {
        MatchResult result = MATCHER.match(pantry(
                have("pasta", 200, Unit.G),
                have("tomato", 4, Unit.PCS),
                have("garlic", 2, Unit.PCS),
                have("olive oil", 2, Unit.TBSP),
                have("salt", 2, Unit.G)), TOMATO_PASTA, OPTIONS);

        assertSame(MatchStatus.CAN_MAKE, result.status());
        assertTrue(result.canMake());
        assertTrue(result.shortfalls().isEmpty());
        assertEquals(5, result.haveCount());
    }

    @Test
    public void anEmptyPantry_isShortOnEveryLine_inTheRecipesOrder() {
        MatchResult result = MATCHER.match(Collections.emptyList(), TOMATO_PASTA, OPTIONS);

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        assertEquals(Arrays.asList("pasta", "tomato", "garlic", "olive oil", "salt"),
                names(result.shortfalls()));
        assertEquals(0, result.haveCount());
    }

    // Quantities: at least, not roughly

    @Test
    public void twoHundredGramsAgainstTwoHundredFifty_isShort_andSaysWhatIsThere() {
        MatchResult result = MATCHER.match(pantry(have("flour", 200, Unit.G)),
                recipe(1, need("flour", 250, Unit.G)), OPTIONS);

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        Shortfall shortfall = result.shortfalls().get(0);
        assertFalse(shortfall.isMissing());
        assertEquals(new CanonicalQuantity(200, UnitKind.MASS), shortfall.available());
    }

    @Test
    public void twoRowsOfTheSameIngredient_areSummed() {
        List<PantryEntry> pantry = pantry(have("flour", 150, Unit.G), have("flour", 100, Unit.G));

        MatchResult result = MATCHER.match(pantry, recipe(1, need("flour", 250, Unit.G)), OPTIONS);

        assertSame(MatchStatus.CAN_MAKE, result.status());
    }

    @Test
    public void rowsSpelledDifferently_areSummedUnderOneName() {
        List<PantryEntry> pantry = pantry(have("Flour", 150, Unit.G), have(" flour.", 0.1, Unit.KG));

        MatchResult result = MATCHER.match(pantry, recipe(1, need("flour", 250, Unit.G)), OPTIONS);

        assertSame(MatchStatus.CAN_MAKE, result.status());
    }

    @Test
    public void aKilogram_coversTwoHundredFiftyGrams() {
        assertTrue(MATCHER.match(pantry(have("flour", 1, Unit.KG)),
                recipe(1, need("flour", 250, Unit.G)), OPTIONS).canMake());
    }

    @Test
    public void aLitre_coversTwoCups() {
        assertTrue(MATCHER.match(pantry(have("milk", 1, Unit.L)),
                recipe(1, need("milk", 2, Unit.CUP)), OPTIONS).canMake());
    }

    // Names: normalised, never compared raw

    @Test
    public void aPluralInThePantry_coversTheSingularInTheRecipe() {
        assertTrue(MATCHER.match(pantry(have("Tomatoes", 4, Unit.PCS)),
                recipe(1, need("tomato", 4, Unit.PCS)), OPTIONS).canMake());
    }

    @Test
    public void anAlias_coversItsTarget() {
        assertTrue(MATCHER.match(pantry(have("Cilantro", 20, Unit.G)),
                recipe(1, need("coriander", 10, Unit.G)), OPTIONS).canMake());
    }

    @Test
    public void aDifferentIngredient_isNotTheSame() {
        assertFalse(MATCHER.match(pantry(have("tomato paste", 400, Unit.G)),
                recipe(1, need("tomato", 1, Unit.PCS)), OPTIONS).canMake());
    }

    // Kinds never cross (decision 5)

    @Test
    public void gramsNeverCoverCups_andTheShortfallSaysNoneOfThatKind() {
        MatchResult result = MATCHER.match(pantry(have("flour", 500, Unit.G)),
                recipe(1, need("flour", 2, Unit.CUP)), OPTIONS);

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        assertNull(result.shortfalls().get(0).available());
    }

    @Test
    public void rowsOfTwoKinds_areNotAddedTogether() {
        List<PantryEntry> pantry = pantry(have("rice", 200, Unit.G), have("rice", 1, Unit.CUP));

        MatchResult result = MATCHER.match(pantry, recipe(1, need("rice", 250, Unit.G)), OPTIONS);

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        assertEquals(new CanonicalQuantity(200, UnitKind.MASS), result.shortfalls().get(0).available());
    }

    // Expired rows (decision 6), with today injected

    @Test
    public void anExpiredRow_isIgnoredByDefault() {
        MatchResult result = MATCHER.match(pantry(have("milk", 1, Unit.L, TODAY.minusDays(1))),
                recipe(1, need("milk", 300, Unit.ML)), MatchOptions.on(TODAY));

        assertSame(MatchStatus.CANNOT_MAKE, result.status());
        assertTrue(result.shortfalls().get(0).isMissing());
    }

    @Test
    public void anExpiredRow_countsWhenExpiredItemsAreIncluded() {
        MatchResult result = MATCHER.match(pantry(have("milk", 1, Unit.L, TODAY.minusDays(1))),
                recipe(1, need("milk", 300, Unit.ML)), MatchOptions.on(TODAY, true));

        assertSame(MatchStatus.CAN_MAKE, result.status());
    }

    @Test
    public void aRowExpiringToday_isNotExpired() {
        assertTrue(MATCHER.match(pantry(have("milk", 1, Unit.L, TODAY)),
                recipe(1, need("milk", 300, Unit.ML)), OPTIONS).canMake());
    }

    @Test
    public void theInjectedDay_decides_notTheClock() {
        List<PantryEntry> pantry = pantry(have("milk", 1, Unit.L, LocalDate.of(2030, 1, 1)));
        RecipeSpec milky = recipe(1, need("milk", 300, Unit.ML));

        assertTrue(MATCHER.match(pantry, milky, MatchOptions.on(LocalDate.of(2030, 1, 1))).canMake());
        assertFalse(MATCHER.match(pantry, milky, MatchOptions.on(LocalDate.of(2030, 1, 2))).canMake());
    }

    @Test
    public void onlyTheFreshRowsAreSummed() {
        MatchResult result = MATCHER.match(pantry(
                        have("flour", 500, Unit.G, TODAY.minusDays(3)),
                        have("flour", 100, Unit.G, null)),
                recipe(1, need("flour", 250, Unit.G)), OPTIONS);

        assertEquals(new CanonicalQuantity(100, UnitKind.MASS), result.shortfalls().get(0).available());
    }

    // The recipe's side

    @Test
    public void anIngredientListedTwice_needsTheSum() {
        RecipeSpec eggs = recipe(1, need("egg", 2, Unit.PCS), need("Eggs", 1, Unit.PCS));

        assertFalse(MATCHER.match(pantry(have("egg", 2, Unit.PCS)), eggs, OPTIONS).canMake());
        assertTrue(MATCHER.match(pantry(have("egg", 3, Unit.PCS)), eggs, OPTIONS).canMake());
    }

    // matchAll

    @Test
    public void matchAll_keepsTheRecipeOrder_andAgreesWithMatch() {
        List<PantryEntry> pantry = pantry(have("flour", 1, Unit.KG), have("milk", 1, Unit.L));
        List<RecipeSpec> recipes = Arrays.asList(
                recipe(7, need("milk", 2, Unit.CUP)),
                recipe(3, need("flour", 250, Unit.G), need("egg", 2, Unit.PCS)),
                recipe(5, need("flour", 100, Unit.G), need("milk", 100, Unit.ML)));

        List<MatchResult> results = MATCHER.matchAll(pantry, recipes, OPTIONS);

        assertEquals(3, results.size());
        for (int i = 0; i < recipes.size(); i++) {
            assertEquals(MATCHER.match(pantry, recipes.get(i), OPTIONS), results.get(i));
        }
        assertEquals(Arrays.asList(true, false, true),
                Arrays.asList(results.get(0).canMake(), results.get(1).canMake(), results.get(2).canMake()));
    }

    @Test
    public void matchAll_overTheTwentySeedRecipes_isFast() throws IOException {
        List<RecipeSpec> seed = seedRecipes();
        List<PantryEntry> pantry = pantry(
                have("eggs", 6, Unit.PCS), have("Cheese", 200, Unit.G), have("butter", 250, Unit.G),
                have("salt", 1, Unit.KG), have("pasta", 500, Unit.G), have("Tomatoes", 6, Unit.PCS),
                have("garlic", 5, Unit.PCS), have("olive oil", 500, Unit.ML), have("flour", 1, Unit.KG),
                have("milk", 1, Unit.L));
        for (int i = 0; i < 200; i++) {
            MATCHER.matchAll(pantry, seed, OPTIONS);
        }
        long fastest = Long.MAX_VALUE;
        List<MatchResult> results = null;
        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            results = MATCHER.matchAll(pantry, seed, OPTIONS);
            fastest = Math.min(fastest, System.nanoTime() - start);
        }

        assertEquals(20, results.size());
        assertTrue("Cheese omelette", results.get(0).canMake());
        assertTrue("Tomato pasta", results.get(1).canMake());
        assertEquals("Pancakes lacks only sugar", Collections.singletonList("sugar"),
                names(results.get(2).shortfalls()));
        // A sanity bound, asserted loosely: the fastest of twenty warm runs
        assertTrue("matchAll took " + fastest / 1_000 + " µs", fastest < 10_000_000L);
    }

    // Immutability and the result's own rules

    @Test
    public void resultsCannotBeChanged() {
        MatchResult result = MATCHER.match(Collections.emptyList(), TOMATO_PASTA, OPTIONS);
        List<MatchResult> all = MATCHER.matchAll(Collections.emptyList(), List.of(TOMATO_PASTA), OPTIONS);

        assertThrows(UnsupportedOperationException.class, () -> result.shortfalls().clear());
        assertThrows(UnsupportedOperationException.class, () -> all.add(result));
        assertThrows(UnsupportedOperationException.class, () -> TOMATO_PASTA.ingredients().clear());
    }

    @Test
    public void aRecipeSpec_keepsItsOwnCopy() {
        List<RequiredIngredient> lines = new ArrayList<>(List.of(need("salt", 1, Unit.G)));
        RecipeSpec spec = new RecipeSpec(1, lines);

        lines.add(need("pepper", 1, Unit.G));

        assertEquals(1, spec.ingredients().size());
    }

    @Test
    public void aRecipeSpec_acceptsAnImmutableList_andRefusesAMissingLine() {
        assertEquals(1, new RecipeSpec(1, List.of(need("salt", 1, Unit.G))).ingredients().size());
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeSpec(1, Arrays.asList(need("salt", 1, Unit.G), null)));
    }

    @Test
    public void aResultWhoseStatusDisagreesWithItsShortfalls_isRefused() {
        Shortfall salt = new Shortfall(need("salt", 1, Unit.G), null);
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(salt), 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CANNOT_MAKE, List.of(), 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CANNOT_MAKE, List.of(salt), 1, 1));
    }

    @Test
    public void incompleteInputs_areRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new PantryEntry(" ", new Quantity(1, Unit.G), null));
        assertThrows(IllegalArgumentException.class, () -> new RequiredIngredient("salt", null));
        assertThrows(IllegalArgumentException.class, () -> new RecipeSpec(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MatchOptions(null, false));
        assertThrows(NullPointerException.class, () -> MATCHER.match(null, TOMATO_PASTA, OPTIONS));
        assertThrows(NullPointerException.class, () -> MATCHER.match(List.of(), null, OPTIONS));
        assertThrows(NullPointerException.class, () -> MATCHER.match(List.of(), TOMATO_PASTA, null));
    }

    // Helpers

    private static PantryEntry have(String name, double amount, Unit unit) {
        return have(name, amount, unit, null);
    }

    private static PantryEntry have(String name, double amount, Unit unit, LocalDate expiry) {
        return new PantryEntry(name, new Quantity(amount, unit), expiry);
    }

    private static RequiredIngredient need(String name, double amount, Unit unit) {
        return new RequiredIngredient(name, new Quantity(amount, unit));
    }

    private static List<PantryEntry> pantry(PantryEntry... entries) {
        return Arrays.asList(entries);
    }

    private static RecipeSpec recipe(long id, RequiredIngredient... lines) {
        return new RecipeSpec(id, Arrays.asList(lines));
    }

    private static List<String> names(List<Shortfall> shortfalls) {
        List<String> names = new ArrayList<>();
        for (Shortfall shortfall : shortfalls) {
            names.add(shortfall.required().name());
        }
        return names;
    }

    /** The real seed, mapped the way the repository will map it (Issue 23), ids by position. */
    private static List<RecipeSpec> seedRecipes() throws IOException {
        String json;
        try (InputStream in = StrictMatcherTest.class.getResourceAsStream("/recipes.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; ) {
                bytes.write(buffer, 0, read);
            }
            json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        List<RecipeSpec> specs = new ArrayList<>();
        List<RecipeWithIngredients> recipes = RecipeJsonParser.parse(json);
        for (int i = 0; i < recipes.size(); i++) {
            List<RequiredIngredient> lines = new ArrayList<>();
            for (RecipeIngredient ingredient : recipes.get(i).getIngredients()) {
                lines.add(need(ingredient.getName(), ingredient.getQuantity(), ingredient.getUnit()));
            }
            specs.add(new RecipeSpec(i + 1, lines));
        }
        return specs;
    }
}
