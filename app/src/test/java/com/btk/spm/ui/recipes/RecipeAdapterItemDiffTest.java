package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.matching.MatchResult;

import org.junit.Test;

import java.util.List;

/**
 * Pins the rule {@code ListAdapter} uses to redraw the suggestions: the same recipe id is the same
 * row, and the row is rebound only when what it shows or the verdict behind it changed.
 */
public class RecipeAdapterItemDiffTest {

    private final RecipeAdapter.ItemDiff diff = new RecipeAdapter.ItemDiff();
    private final MatchedRecipe garlicBread = row(17, "Garlic bread", 2);

    @Test
    public void theSameRecipeMatchedAgain_isTheSameItemWithTheSameContents() {
        MatchedRecipe matchedAgain = row(17, "Garlic bread", 2);

        assertTrue(diff.areItemsTheSame(garlicBread, matchedAgain));
        assertTrue(diff.areContentsTheSame(garlicBread, matchedAgain));
    }

    @Test
    public void anotherRecipe_isADifferentItem() {
        assertFalse(diff.areItemsTheSame(garlicBread, row(6, "Grilled cheese sandwich", 1)));
    }

    @Test
    public void whatTheRowShows_changesTheContents() {
        assertFalse(diff.areContentsTheSame(garlicBread, row(17, "Cheesy garlic bread", 2)));
        assertFalse(diff.areContentsTheSame(garlicBread, row(17, "Garlic bread", 4)));
        assertFalse(diff.areContentsTheSame(garlicBread, row(17, "Garlic bread", null)));
    }

    @Test
    public void aResultForAnotherRecipe_cannotBePairedWithThisOne() {
        RecipeWithIngredients recipe = garlicBread.recipe();
        MatchResult other = new MatchResult(6, MatchStatus.CAN_MAKE, List.of(), 3, 3);

        assertThrows(IllegalArgumentException.class, () -> new MatchedRecipe(recipe, other));
    }

    private static MatchedRecipe row(long id, String name, Integer servings) {
        RecipeWithIngredients recipe = new RecipeWithIngredients(new Recipe(id, name, servings, List.of("Bake it.")),
                List.of(new RecipeIngredient(1, id, "bread", 4, Unit.PCS),
                        new RecipeIngredient(2, id, "butter", 50, Unit.G),
                        new RecipeIngredient(3, id, "garlic", 3, Unit.PCS)));
        return new MatchedRecipe(recipe, new MatchResult(id, MatchStatus.CAN_MAKE, List.of(), 3, 3));
    }
}
