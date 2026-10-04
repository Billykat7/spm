package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two properties of the strict rule, checked for every one of the twenty seed recipes (Issue 21).
 * From a pantry that holds exactly what a recipe needs:
 * <ul>
 *   <li>taking away any one ingredient, or a little of it, or letting it expire, breaks the match;</li>
 *   <li>adding anything at all keeps it: more of an ingredient, other ingredients, the same
 *       ingredient in another kind, expired rows.</li>
 * </ul>
 * Together they say the match depends on the required ingredients and nothing else.
 */
@RunWith(Parameterized.class)
public class MatcherPropertiesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    private static final MatchOptions OPTIONS = MatchOptions.on(DAY);

    @Parameter(0) public String recipeName;
    @Parameter(1) public RecipeSpec recipe;

    @Parameters(name = "{0}")
    public static List<Object[]> recipes() {
        List<Object[]> rows = new ArrayList<>();
        List<RecipeSpec> specs = SeedFixture.specs();
        for (int i = 0; i < specs.size(); i++) {
            rows.add(new Object[]{SeedFixture.name(i), specs.get(i)});
        }
        return rows;
    }

    @Test
    public void theExactPantry_makesTheRecipe() {
        assertTrue(match(SeedFixture.exactPantryFor(recipe)).canMake());
    }

    @Test
    public void removingAnyOneIngredient_breaksTheMatch() {
        List<PantryEntry> exact = SeedFixture.exactPantryFor(recipe);
        for (int removed = 0; removed < exact.size(); removed++) {
            List<PantryEntry> pantry = new ArrayList<>(exact);
            PantryEntry gone = pantry.remove(removed);

            MatchResult result = match(pantry);

            assertFalse("without " + gone.name(), result.canMake());
            assertEquals("without " + gone.name(), gone.name(), onlyShortfall(result));
        }
    }

    @Test
    public void holdingOnePercentLessOfAnyOneIngredient_breaksTheMatch() {
        List<PantryEntry> exact = SeedFixture.exactPantryFor(recipe);
        for (int i = 0; i < exact.size(); i++) {
            List<PantryEntry> pantry = new ArrayList<>(exact);
            PantryEntry line = pantry.get(i);
            Quantity less = new Quantity(line.quantity().amount() * 0.99, line.quantity().unit());
            pantry.set(i, new PantryEntry(line.name(), less, null));

            assertEquals("short of " + line.name(), line.name(), onlyShortfall(match(pantry)));
        }
    }

    @Test
    public void anyOneIngredientOnlyInAnExpiredRow_breaksTheMatch_unlessExpiredItemsCount() {
        List<PantryEntry> exact = SeedFixture.exactPantryFor(recipe);
        for (int i = 0; i < exact.size(); i++) {
            List<PantryEntry> pantry = new ArrayList<>(exact);
            PantryEntry line = pantry.get(i);
            pantry.set(i, new PantryEntry(line.name(), line.quantity(), DAY.minusDays(1)));

            assertEquals("expired " + line.name(), line.name(), onlyShortfall(match(pantry)));
            assertTrue("expired " + line.name() + ", counted",
                    SeedFixture.MATCHER.match(pantry, recipe, MatchOptions.on(DAY, true)).canMake());
        }
    }

    @Test
    public void addingAnything_keepsTheMatch() {
        List<List<PantryEntry>> additions = new ArrayList<>();
        List<PantryEntry> everything = new ArrayList<>();
        for (RecipeSpec other : SeedFixture.specs()) {
            for (RequiredIngredient line : other.ingredients()) {
                Unit unit = line.quantity().unit();
                // Another ingredient, or more of this one, in its own unit
                additions.add(List.of(new PantryEntry(line.name(), new Quantity(1000, unit), null)));
                // The same ingredient in another kind
                additions.add(List.of(new PantryEntry(line.name(), new Quantity(5, otherKind(unit)), null)));
                // An expired row
                additions.add(List.of(new PantryEntry(line.name(), new Quantity(1, unit), DAY.minusDays(1))));
            }
        }
        additions.add(List.of(new PantryEntry("dragon fruit", new Quantity(2, Unit.PCS), null)));
        for (List<PantryEntry> addition : additions) {
            everything.addAll(addition);
            List<PantryEntry> pantry = new ArrayList<>(SeedFixture.exactPantryFor(recipe));
            pantry.addAll(addition);

            assertTrue("with " + addition, match(pantry).canMake());
        }
        List<PantryEntry> all = new ArrayList<>(SeedFixture.exactPantryFor(recipe));
        all.addAll(everything);
        assertTrue("with all of them at once", match(all).canMake());
    }

    @Test
    public void theOrderOfThePantry_doesNotMatter() {
        List<PantryEntry> reversed = new ArrayList<>(SeedFixture.exactPantryFor(recipe));
        Collections.reverse(reversed);

        assertTrue(match(reversed).canMake());
    }

    private MatchResult match(List<PantryEntry> pantry) {
        return SeedFixture.MATCHER.match(pantry, recipe, OPTIONS);
    }

    private static String onlyShortfall(MatchResult result) {
        assertEquals(1, result.shortfalls().size());
        return result.shortfalls().get(0).required().name();
    }

    private static Unit otherKind(Unit unit) {
        return unit.kind() == UnitKind.MASS ? Unit.ML : Unit.G;
    }
}
