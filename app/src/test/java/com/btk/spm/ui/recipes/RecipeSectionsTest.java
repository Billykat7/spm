package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.DisplayQuantity;
import com.btk.spm.domain.DisplayUnit;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;
import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.domain.matching.CanonicalQuantity;
import com.btk.spm.domain.matching.MatchResult;
import com.btk.spm.domain.matching.RequiredIngredient;
import com.btk.spm.domain.matching.Shortfall;

import org.junit.Test;

import java.util.List;

/**
 * {@link RecipeSections}: the suggestions and the almost-there recipes reach their own adapters, never
 * each other's, the headers show only over something, and each almost-there row names its one missing
 * or short ingredient.
 */
public class RecipeSectionsTest {

    private static final MatchedRecipe PASTA = canMake(2, "Tomato pasta");
    private static final MatchedRecipe GARLIC_BREAD = canMake(17, "Garlic bread");
    private static final MatchedRecipe PANCAKES_SHORT_OF_FLOUR = shortOf(3, "Pancakes",
            new RequiredIngredient("flour", new Quantity(250, Unit.G)), new CanonicalQuantity(200, UnitKind.MASS));
    private static final MatchedRecipe OMELETTE_MISSING_EGGS = shortOf(1, "Cheese omelette",
            new RequiredIngredient("egg", new Quantity(2, Unit.PCS)), null);

    @Test
    public void aMixedContent_sendsEachListToItsOwnSide_inOrder() {
        RecipeSections.Sections sections = RecipeSections.split(new UiState.Content(List.of(PASTA, GARLIC_BREAD),
                List.of(PANCAKES_SHORT_OF_FLOUR, OMELETTE_MISSING_EGGS)));

        assertEquals(List.of(PASTA, GARLIC_BREAD), sections.suggested());
        assertEquals(List.of(PANCAKES_SHORT_OF_FLOUR, OMELETTE_MISSING_EGGS), sections.almostThere());
        assertTrue(sections.suggested().stream().noneMatch(r -> r.result().status() == MatchStatus.ALMOST_THERE));
        assertFalse("there are suggestions, so no zero-match row", sections.zeroMatchHeader());
        assertTrue(sections.almostThereHeader());
    }

    @Test
    public void nothingAlmostThere_hasNoHeading() {
        RecipeSections.Sections sections = RecipeSections.split(new UiState.Content(List.of(PASTA), List.of()));

        assertFalse(sections.almostThereHeader());
        assertFalse(sections.zeroMatchHeader());
        assertTrue(sections.almostThere().isEmpty());
    }

    @Test
    public void nothingSuggested_butSomethingAlmostThere_putsTheZeroMatchSentenceFirst() {
        RecipeSections.Sections sections =
                RecipeSections.split(new UiState.Content(List.of(), List.of(OMELETTE_MISSING_EGGS)));

        assertTrue(sections.zeroMatchHeader());
        assertTrue(sections.suggested().isEmpty());
        assertTrue(sections.almostThereHeader());
    }

    @Test
    public void theSuggestedList_refusesAnAlmostThereRecipe_byName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RecipeSections.requireCanMake(List.of(PASTA, PANCAKES_SHORT_OF_FLOUR)));

        assertTrue(refused.getMessage(), refused.getMessage().startsWith("Pancakes (recipe 3) is ALMOST_THERE"));
    }

    @Test
    public void theAlmostThereList_refusesARecipeThatCanBeMade() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RecipeSections.requireAlmostThere(List.of(OMELETTE_MISSING_EGGS, GARLIC_BREAD)));

        assertTrue(refused.getMessage(), refused.getMessage().startsWith("Garlic bread (recipe 17) is CAN_MAKE"));
    }

    @Test
    public void aContentBuiltWithTheListsSwapped_isRefusedBySplit() {
        UiState.Content swapped = new UiState.Content(List.of(OMELETTE_MISSING_EGGS), List.of(PASTA));

        assertThrows(IllegalArgumentException.class, () -> RecipeSections.split(swapped));
    }

    @Test
    public void theGuards_passTheirListThrough_andLetNullClearIt() {
        List<MatchedRecipe> suggested = List.of(PASTA);

        assertSame(suggested, RecipeSections.requireCanMake(suggested));
        assertNull(RecipeSections.requireCanMake(null));
        assertNull(RecipeSections.requireAlmostThere(null));
    }

    @Test
    public void aMissingIngredient_hasNothingAvailable() {
        RecipeSections.MissingLine line = RecipeSections.missingLine(OMELETTE_MISSING_EGGS, UnitsSystem.METRIC);

        assertEquals("egg", line.name());
        assertEquals(new DisplayQuantity(2, DisplayUnit.PCS), line.need());
        assertNull(line.have());
    }

    @Test
    public void aShortIngredient_saysWhatThereIs_inDisplayUnits() {
        RecipeSections.MissingLine line = RecipeSections.missingLine(PANCAKES_SHORT_OF_FLOUR, UnitsSystem.METRIC);

        assertEquals("flour", line.name());
        assertEquals(new DisplayQuantity(250, DisplayUnit.G), line.need());
        assertEquals(new DisplayQuantity(200, DisplayUnit.G), line.have());
    }

    @Test
    public void underImperial_theSameShortfall_readsInOunces() {
        RecipeSections.MissingLine line = RecipeSections.missingLine(PANCAKES_SHORT_OF_FLOUR, UnitsSystem.IMPERIAL);

        assertEquals("flour", line.name());
        assertEquals(new DisplayQuantity(8.82, DisplayUnit.OZ), line.need());
        assertEquals(new DisplayQuantity(7.05, DisplayUnit.OZ), line.have());
    }

    @Test
    public void aRecipeThatCanBeMade_hasNoMissingLine() {
        assertThrows(IllegalArgumentException.class, () -> RecipeSections.missingLine(PASTA, UnitsSystem.METRIC));
    }

    private static MatchedRecipe canMake(long id, String name) {
        return new MatchedRecipe(recipe(id, name), new MatchResult(id, MatchStatus.CAN_MAKE, List.of(), 1, 1));
    }

    private static MatchedRecipe shortOf(long id, String name, RequiredIngredient line, CanonicalQuantity have) {
        return new MatchedRecipe(recipe(id, name),
                new MatchResult(id, MatchStatus.ALMOST_THERE, List.of(new Shortfall(line, have)), 2, 3));
    }

    private static RecipeWithIngredients recipe(long id, String name) {
        return new RecipeWithIngredients(new Recipe(id, name, 2, List.of("Cook it.")),
                List.of(new RecipeIngredient(id * 10, id, "salt", 1, Unit.G)));
    }
}
