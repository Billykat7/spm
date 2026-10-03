package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Unit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * {@link RecipeJsonParser} on small documents, on the JVM (Issue 11): a valid recipe reads back in
 * full, and every kind of bad input is refused with a message that names the recipe and the field.
 */
public class RecipeJsonParserTest {

    private static final String OMELETTE = "{\"name\": \"Cheese omelette\", \"servings\": 1,"
            + " \"ingredients\": [{\"name\": \"egg\", \"quantity\": 3, \"unit\": \"pcs\"},"
            + "                   {\"name\": \"salt\", \"quantity\": 0.5, \"unit\": \"g\"}],"
            + " \"steps\": [\"Whisk the eggs.\", \"Cook, fold and serve.\"]}";

    @Test
    public void aMinimalValidDocument_parsesInFull() {
        List<RecipeWithIngredients> recipes = RecipeJsonParser.parse("[" + OMELETTE + "]");

        assertEquals(1, recipes.size());
        RecipeWithIngredients omelette = recipes.get(0);
        assertEquals("Cheese omelette", omelette.getRecipe().getName());
        assertEquals(Integer.valueOf(1), omelette.getRecipe().getServings());
        assertEquals(Arrays.asList("Whisk the eggs.", "Cook, fold and serve."), omelette.getRecipe().getSteps());
        assertEquals(0, omelette.getRecipe().getId());
        List<RecipeIngredient> ingredients = omelette.getIngredients();
        assertEquals(2, ingredients.size());
        assertEquals("egg", ingredients.get(0).getName());
        assertEquals(3.0, ingredients.get(0).getQuantity(), 0.0);
        assertEquals(Unit.PCS, ingredients.get(0).getUnit());
        assertEquals(0.5, ingredients.get(1).getQuantity(), 0.0);
        assertEquals(Unit.G, ingredients.get(1).getUnit());
    }

    @Test
    public void servings_areOptional() {
        String noServings = OMELETTE.replace("\"servings\": 1,", "");

        assertNull(RecipeJsonParser.parse("[" + noServings + "]").get(0).getRecipe().getServings());
    }

    @Test
    public void recipes_keepTheirDocumentOrder() {
        String pancakes = OMELETTE.replace("Cheese omelette", "Pancakes");

        List<RecipeWithIngredients> recipes = RecipeJsonParser.parse("[" + pancakes + "," + OMELETTE + "]");

        assertEquals("Pancakes", recipes.get(0).getRecipe().getName());
        assertEquals("Cheese omelette", recipes.get(1).getRecipe().getName());
    }

    @Test
    public void anEmptyArray_parsesToNoRecipes() {
        assertTrue(RecipeJsonParser.parse("[]").isEmpty());
    }

    // Refusals: each names the recipe and the field

    @Test
    public void aMissingUnit_isRefused() {
        assertRefused(OMELETTE.replace(", \"unit\": \"pcs\"", ""), "Cheese omelette", "ingredient 1 (egg)", "\"unit\"");
    }

    @Test
    public void anUnknownUnitSymbol_isRefused() {
        assertRefused(OMELETTE.replace("\"unit\": \"g\"", "\"unit\": \"grams\""),
                "Cheese omelette", "ingredient 2 (salt)", "grams");
    }

    @Test
    public void aZeroQuantity_isRefused() {
        assertRefused(OMELETTE.replace("\"quantity\": 3", "\"quantity\": 0"),
                "Cheese omelette", "ingredient 1 (egg)", "\"quantity\" must be more than 0");
    }

    @Test
    public void aNegativeOrTextQuantity_isRefused() {
        assertRefused(OMELETTE.replace("\"quantity\": 3", "\"quantity\": -1"), "Cheese omelette", "\"quantity\"");
        assertRefused(OMELETTE.replace("\"quantity\": 3", "\"quantity\": \"3\""), "Cheese omelette", "\"quantity\"");
    }

    @Test
    public void twoRecipesWithTheSameName_areRefused() {
        String sameNameOtherCase = OMELETTE.replace("Cheese omelette", "cheese OMELETTE");

        assertRefused(OMELETTE + "," + sameNameOtherCase, "cheese OMELETTE", "duplicate name");
    }

    @Test
    public void aMissingRecipeName_isRefusedByPosition() {
        assertRefused(OMELETTE + "," + OMELETTE.replace("\"name\": \"Cheese omelette\", ", ""),
                "recipe 2", "\"name\"");
    }

    @Test
    public void missingOrEmptyIngredientsOrSteps_areRefused() {
        assertRefused(OMELETTE.replaceAll("\"ingredients\": \\[.*?\\}\\],", "\"ingredients\": [],"),
                "Cheese omelette", "\"ingredients\"");
        assertRefused(OMELETTE.replace("\"steps\": [\"Whisk the eggs.\", \"Cook, fold and serve.\"]", "\"steps\": []"),
                "Cheese omelette", "\"steps\"");
        assertRefused(OMELETTE.replace("\"Whisk the eggs.\"", "\"  \""), "Cheese omelette", "step 1");
    }

    @Test
    public void servingsBelowOneOrFractional_areRefused() {
        assertRefused(OMELETTE.replace("\"servings\": 1", "\"servings\": 0"), "Cheese omelette", "\"servings\"");
        assertRefused(OMELETTE.replace("\"servings\": 1", "\"servings\": 1.5"), "Cheese omelette", "\"servings\"");
    }

    @Test
    public void textThatIsNotAJsonArray_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RecipeJsonParser.parse(OMELETTE));
        assertTrue(e.getMessage(), e.getMessage().contains("not a JSON array"));
    }

    /** Parses {@code [recipes]} and checks it throws with every one of {@code expected} in the message. */
    private static void assertRefused(String recipes, String... expected) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RecipeJsonParser.parse("[" + recipes + "]"));
        for (String part : expected) {
            assertTrue("\"" + part + "\" not in: " + e.getMessage(), e.getMessage().contains(part));
        }
    }
}
