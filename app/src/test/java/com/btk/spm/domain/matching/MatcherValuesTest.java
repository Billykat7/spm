package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;

import org.junit.Test;

import java.util.List;

/**
 * The matcher's value types refuse what cannot exist (Issue 21). Each refusal is a line the scenario
 * and property tests never reach, because they only build valid values; JaCoCo's coverage check on
 * {@code domain/matching/} found them.
 */
public class MatcherValuesTest {

    private static final Quantity ONE_GRAM = new Quantity(1, Unit.G);

    @Test
    public void aPantryEntry_needsANameAndAQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new PantryEntry(null, ONE_GRAM, null));
        assertThrows(IllegalArgumentException.class, () -> new PantryEntry("", ONE_GRAM, null));
        assertThrows(IllegalArgumentException.class, () -> new PantryEntry("salt", null, null));
    }

    @Test
    public void aRequiredIngredient_needsANameAndAQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new RequiredIngredient(null, ONE_GRAM));
        assertThrows(IllegalArgumentException.class, () -> new RequiredIngredient("  ", ONE_GRAM));
        assertThrows(IllegalArgumentException.class, () -> new RequiredIngredient("salt", null));
    }

    @Test
    public void aRequiredIngredient_readsAsAmountUnitAndName() {
        assertEquals("250 g flour", new RequiredIngredient("flour", new Quantity(250, Unit.G)).toString());
    }

    @Test
    public void aRecipeSpec_needsAList() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeSpec(1, null));
    }

    @Test
    public void aShortfall_needsTheLineItIsAbout() {
        assertThrows(IllegalArgumentException.class, () -> new Shortfall(null, null));
    }

    @Test
    public void aMatchResult_needsAStatusAndAList() {
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(1, null, List.of(), 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(1, MatchStatus.CAN_MAKE, List.of(), -1, -1));
    }
}
