package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.R;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.matching.MatchResult;
import com.btk.spm.domain.matching.RequiredIngredient;
import com.btk.spm.domain.matching.Shortfall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RecipesRender} over every state the Recipes tab can be in: exactly one of the progress
 * indicator, the list and the empty view is visible, and the empty view has the right message and
 * button for its reason.
 */
public class RecipesRenderTest {

    private static final MatchedRecipe GARLIC_BREAD = new MatchedRecipe(
            new RecipeWithIngredients(new Recipe(17, "Garlic bread", 2, List.of("Bake it.")),
                    List.of(new RecipeIngredient(1, 17, "bread", 4, Unit.PCS))),
            new MatchResult(17, MatchStatus.CAN_MAKE, List.of(), 1, 1));

    @Test
    public void everyState_showsExactlyOneView() {
        List<UiState> states = new ArrayList<>();
        states.add(UiState.Loading.INSTANCE);
        for (EmptyReason reason : EmptyReason.values()) {
            states.add(new UiState.Empty(reason));
        }
        states.add(new UiState.Content(List.of(GARLIC_BREAD), List.of()));
        states.add(new UiState.Content(List.of(), List.of(almostThere())));

        for (UiState state : states) {
            RecipesRender render = RecipesRender.of(state);
            int visible = (render.progress() ? 1 : 0) + (render.list() ? 1 : 0) + (render.empty() ? 1 : 0);
            assertEquals(state + " shows " + render, 1, visible);
        }
    }

    @Test
    public void loading_showsOnlyTheProgressIndicator() {
        RecipesRender render = RecipesRender.of(UiState.Loading.INSTANCE);

        assertTrue(render.progress());
        assertTrue(render.rows().isEmpty());
    }

    @Test
    public void content_showsTheCanMakeRows_andCountsThem() {
        RecipesRender render = RecipesRender.of(new UiState.Content(List.of(GARLIC_BREAD), List.of()));

        assertTrue(render.list());
        assertEquals(List.of(GARLIC_BREAD), render.rows());
        assertEquals(1, render.count());
    }

    @Test
    public void nothingSuggested_butOneAlmostThere_showsTheList_andCountsNothing() {
        RecipesRender render = RecipesRender.of(new UiState.Content(List.of(), List.of(almostThere())));

        assertTrue("the section is a list, not the full-screen empty state", render.list());
        assertEquals(0, render.count());
    }

    @Test
    public void noMatch_saysTheBriefsSentence_withTheButton() {
        RecipesRender render = RecipesRender.of(new UiState.Empty(EmptyReason.NO_MATCH));

        assertTrue(render.empty());
        assertEquals(R.string.recipes_empty_no_match, render.emptyMessage());
        assertTrue(render.addIngredients());
        assertEquals(0, render.count());
    }

    @Test
    public void pantryEmpty_saysSo_withTheButton() {
        RecipesRender render = RecipesRender.of(new UiState.Empty(EmptyReason.PANTRY_EMPTY));

        assertEquals(R.string.recipes_empty_pantry_empty, render.emptyMessage());
        assertTrue(render.addIngredients());
    }

    @Test
    public void noRecipes_saysTheSeedDidNotLoad_withNoButton() {
        RecipesRender render = RecipesRender.of(new UiState.Empty(EmptyReason.NO_RECIPES));

        assertEquals(R.string.recipes_error_title, render.emptyMessage());
        assertEquals(R.string.recipes_error_body, render.emptyBody());
        assertFalse(render.addIngredients());
    }

    @Test
    public void theReason_isPickedInTheOrderThatTellsTheTruth() {
        assertEquals(EmptyReason.NO_RECIPES, EmptyReason.of(true, true));
        assertEquals(EmptyReason.NO_RECIPES, EmptyReason.of(true, false));
        assertEquals(EmptyReason.PANTRY_EMPTY, EmptyReason.of(false, true));
        assertEquals(EmptyReason.NO_MATCH, EmptyReason.of(false, false));
    }

    @Test
    public void noState_isRefused() {
        assertThrows(NullPointerException.class, () -> RecipesRender.of(null));
    }

    /** Garlic bread one loaf short: an almost-there row. */
    private static MatchedRecipe almostThere() {
        return new MatchedRecipe(GARLIC_BREAD.recipe(), new MatchResult(17, MatchStatus.ALMOST_THERE,
                List.of(new Shortfall(new RequiredIngredient("bread", new Quantity(4, Unit.PCS)), null)), 0, 1));
    }
}
