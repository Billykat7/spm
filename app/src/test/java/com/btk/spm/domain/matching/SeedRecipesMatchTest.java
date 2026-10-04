package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The matcher against the twenty real seed recipes and the real alias table (Issue 21), the cases a
 * marker meets first: an empty pantry, the pantry for one recipe, and that pantry one item short.
 */
public class SeedRecipesMatchTest {

    private static final MatchOptions TODAY = MatchOptions.on(LocalDate.of(2026, 10, 4));
    private static final List<RecipeSpec> SEED = SeedFixture.specs();
    private static final RecipeSpec RECIPE_ONE = SEED.get(0);

    @Test
    public void theSeedHasTwentyRecipes() {
        assertEquals(20, SEED.size());
    }

    @Test
    public void anEmptyPantry_makesNothing() {
        for (MatchResult result : SeedFixture.MATCHER.matchAll(Collections.emptyList(), SEED, TODAY)) {
            assertSame(SeedFixture.name((int) result.recipeId() - 1), MatchStatus.CANNOT_MAKE, result.status());
            assertEquals(0, result.haveCount());
        }
    }

    @Test
    public void recipeOnesExactPantry_makesRecipeOne() {
        MatchResult result = SeedFixture.MATCHER.match(SeedFixture.exactPantryFor(RECIPE_ONE), RECIPE_ONE, TODAY);

        assertSame(SeedFixture.name(0), MatchStatus.CAN_MAKE, result.status());
    }

    @Test
    public void recipeOnesExactPantry_makesNoRecipeThatNeedsMore() {
        List<PantryEntry> pantry = SeedFixture.exactPantryFor(RECIPE_ONE);
        // The oracle, worked out apart from the engine: seed names are already canonical, so a line
        // is covered only by the same name in the same kind, in at least the same canonical amount
        Map<String, Quantity> held = new HashMap<>();
        for (PantryEntry entry : pantry) {
            held.put(entry.name(), entry.quantity());
        }
        List<MatchResult> results = SeedFixture.MATCHER.matchAll(pantry, SEED, TODAY);
        int makeable = 0;
        for (int i = 0; i < SEED.size(); i++) {
            boolean coveredByHand = true;
            for (RequiredIngredient line : SEED.get(i).ingredients()) {
                Quantity have = held.get(line.name());
                coveredByHand &= have != null && have.unit().kind() == line.quantity().unit().kind()
                        && canonical(have) >= canonical(line.quantity());
            }
            assertEquals(SeedFixture.name(i), coveredByHand, results.get(i).canMake());
            makeable += coveredByHand ? 1 : 0;
        }
        assertTrue("recipe 1 itself is makeable", results.get(0).canMake());
        assertTrue("most recipes need something recipe 1 does not have", makeable < SEED.size() / 2);
    }

    @Test
    public void recipeOnesPantryMinusAnyOneItem_doesNotMakeRecipeOne() {
        List<PantryEntry> exact = SeedFixture.exactPantryFor(RECIPE_ONE);
        for (int removed = 0; removed < exact.size(); removed++) {
            List<PantryEntry> pantry = new ArrayList<>(exact);
            PantryEntry gone = pantry.remove(removed);

            MatchResult result = SeedFixture.MATCHER.match(pantry, RECIPE_ONE, TODAY);

            assertFalse("without " + gone.name(), result.canMake());
            assertSame("without " + gone.name(), MatchStatus.ALMOST_THERE, result.status());
            assertEquals("without " + gone.name(), 1, result.shortfalls().size());
            assertEquals(gone.name(), result.shortfalls().get(0).required().name());
        }
    }

    @Test
    public void recipeOne_typedTheWayAPersonTypes_stillMatches() {
        // "Eggs", "CHEESE ", "Butter." and so on: what the add form may hold
        List<PantryEntry> typed = new ArrayList<>();
        for (PantryEntry entry : SeedFixture.exactPantryFor(RECIPE_ONE)) {
            String name = entry.name().toUpperCase(Locale.ROOT) + ". ";
            typed.add(new PantryEntry(name, entry.quantity(), null));
        }

        assertTrue(SeedFixture.MATCHER.match(typed, RECIPE_ONE, TODAY).canMake());
    }

    private static double canonical(Quantity quantity) {
        return quantity.amount() * quantity.unit().factorToCanonical();
    }
}
