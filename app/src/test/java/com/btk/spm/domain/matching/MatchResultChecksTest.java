package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import java.util.List;
import java.util.Map;

/**
 * {@link MatchResult#checks()} (Issue 25): one {@link IngredientCheck} per required line, in the
 * recipe's order, with the pantry's amount for covered lines as well as short ones. It adds to the
 * result and changes no verdict: a line is satisfied exactly when it is not among the shortfalls, and
 * the shortfalls, the counts and the status are what they were.
 */
public class MatchResultChecksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final StrictMatcher MATCHER =
            new StrictMatcher(new IngredientNormaliser(Map.of()), new UnitConverter());

    /** Tomato pasta, as the seed writes it (Issue 11). */
    private static final RecipeSpec TOMATO_PASTA = new RecipeSpec(2, List.of(
            need("pasta", 200, Unit.G), need("tomato", 4, Unit.PCS), need("garlic", 2, Unit.PCS),
            need("olive oil", 2, Unit.TBSP), need("salt", 2, Unit.G)));

    @Test
    public void everyLine_hasOneCheck_inTheRecipesOrder() {
        MatchResult result = match(have("pasta", 1, Unit.KG), have("tomatoes", 6, Unit.PCS));

        assertEquals(TOMATO_PASTA.ingredients().size(), result.checks().size());
        for (int i = 0; i < result.checks().size(); i++) {
            assertEquals(TOMATO_PASTA.ingredients().get(i), result.checks().get(i).required());
        }
    }

    @Test
    public void aLineIsSatisfied_exactlyWhenItIsNotAShortfall() {
        MatchResult result = match(have("pasta", 1, Unit.KG), have("tomatoes", 6, Unit.PCS),
                have("garlic", 1, Unit.PCS));

        List<RequiredIngredient> shortLines = new ArrayList<>();
        for (Shortfall shortfall : result.shortfalls()) {
            shortLines.add(shortfall.required());
        }
        for (IngredientCheck check : result.checks()) {
            assertEquals(check.required().toString(), !shortLines.contains(check.required()), check.satisfied());
        }
        assertEquals(MatchStatus.CANNOT_MAKE, result.status());
        assertEquals(2, result.haveCount());
    }

    @Test
    public void aCoveredLine_carriesWhatThePantryHolds() {
        MatchResult result = match(have("pasta", 1, Unit.KG), have("pasta", 250, Unit.G));

        IngredientCheck pasta = result.checks().get(0);
        assertTrue(pasta.satisfied());
        assertEquals(new CanonicalQuantity(1250, UnitKind.MASS), pasta.available());
    }

    @Test
    public void aShortLine_carriesWhatThereIs_andAMissingOneNothing() {
        MatchResult result = match(have("garlic", 1, Unit.PCS));

        IngredientCheck garlic = result.checks().get(2);
        assertFalse(garlic.satisfied());
        assertEquals(new CanonicalQuantity(1, UnitKind.COUNT), garlic.available());
        IngredientCheck salt = result.checks().get(4);
        assertFalse(salt.satisfied());
        assertNull(salt.available());
    }

    @Test
    public void theChecks_matchTheShortfallsThatWereThereBefore() {
        MatchResult result = match(have("pasta", 1, Unit.KG), have("tomatoes", 6, Unit.PCS),
                have("garlic", 1, Unit.PCS), have("olive oil", 2, Unit.TBSP));

        List<Shortfall> fromChecks = new ArrayList<>();
        for (IngredientCheck check : result.checks()) {
            if (!check.satisfied()) {
                fromChecks.add(check.asShortfall());
            }
        }
        assertEquals(result.shortfalls(), fromChecks);
        assertEquals(MatchStatus.CANNOT_MAKE, result.status());
    }

    @Test
    public void everythingCovered_isCanMake_withEveryCheckSatisfied() {
        MatchResult result = match(have("pasta", 200, Unit.G), have("tomato", 4, Unit.PCS),
                have("garlic", 2, Unit.PCS), have("olive oil", 2, Unit.TBSP), have("salt", 2, Unit.G));

        assertEquals(MatchStatus.CAN_MAKE, result.status());
        assertTrue(result.checks().stream().allMatch(IngredientCheck::satisfied));
    }

    @Test
    public void theChecks_cannotBeChanged() {
        MatchResult result = match();

        assertThrows(UnsupportedOperationException.class, () -> result.checks().clear());
    }

    @Test
    public void aResultBuiltWithoutChecks_hasNone() {
        assertTrue(new MatchResult(1, MatchStatus.CAN_MAKE, List.of(), 1, 1).checks().isEmpty());
    }

    @Test
    public void checksThatDisagreeWithTheShortfallsOrTheCounts_areRefused() {
        RequiredIngredient salt = need("salt", 1, Unit.G);
        CanonicalQuantity oneGram = new CanonicalQuantity(1, UnitKind.MASS);
        IngredientCheck covered = new IngredientCheck(salt, oneGram, true);
        IngredientCheck missing = new IngredientCheck(salt, null, false);

        // A covered line where the shortfalls say it is missing
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(1, MatchStatus.ALMOST_THERE,
                List.of(new Shortfall(salt, null)), 0, 1, List.of(covered)));
        // A missing line where the result says it can be made
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(), 1, 1, List.of(missing)));
        // One check for a two-line recipe
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(), 2, 2, List.of(covered)));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(), 1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(), 1, 1, Arrays.asList((IngredientCheck) null)));
    }

    @Test
    public void aCheck_needsItsLine_andCannotBeCoveredByNothing() {
        assertThrows(IllegalArgumentException.class, () -> new IngredientCheck(null, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new IngredientCheck(need("salt", 1, Unit.G), null, true));
    }

    private static MatchResult match(PantryEntry... pantry) {
        return MATCHER.match(List.of(pantry), TOMATO_PASTA, MatchOptions.on(TODAY));
    }

    private static RequiredIngredient need(String name, double amount, Unit unit) {
        return new RequiredIngredient(name, new Quantity(amount, unit));
    }

    private static PantryEntry have(String name, double amount, Unit unit) {
        return new PantryEntry(name, new Quantity(amount, unit), null);
    }
}
