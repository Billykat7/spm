package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks the real {@code assets/recipes.json}, read on the JVM as a test resource (Issue 11), against
 * the rules the app and the matcher rely on. A slip in the asset fails here, naming the recipe,
 * before it can reach a phone.
 *
 * <p><b>The unit convention, kept here because JSON has no comments.</b> Each ingredient is written in
 * the kind a pantry would hold it in, and in the same kind in every recipe (decision 5), or a pantry
 * item could satisfy it in one recipe and never in another:
 * <ul>
 *   <li>counted, {@code pcs}: egg, onion, garlic (cloves), tomato, potato, carrot, lemon, banana,
 *       bread (slices), tortilla, bell pepper, spring onion;</li>
 *   <li>weighed, {@code g}: flour, sugar, salt, butter, rice, pasta, cheese, oats, chicken, beef mince,
 *       lentil, spinach, mushroom, tuna;</li>
 *   <li>poured, {@code ml}, {@code l}, {@code tsp}, {@code tbsp} or {@code cup} (all volume): milk,
 *       olive oil, oil, soy sauce, honey, chicken stock, vegetable stock.</li>
 * </ul>
 * Names are written in the form {@code IngredientNormaliser} (Issue 18) will produce: trimmed, lower
 * case, singular. Until it exists this test checks trimmed and lower case; Issue 18 tightens it to
 * {@code normalise(name).equals(name)}. Water is never listed: a pantry does not stock it.
 */
public class RecipesJsonTest {

    /** The asset's path on the test classpath; {@code app/build.gradle} adds {@code src/main/assets}. */
    private static final String ASSET = "/" + RecipeSeeder.ASSET_NAME;

    /** The twenty recipes the issue pins, in the asset's order. */
    private static final List<String> PINNED_NAMES = Arrays.asList(
            "Cheese omelette", "Tomato pasta", "Pancakes", "Egg fried rice", "Garlic butter mushrooms",
            "Grilled cheese sandwich", "Scrambled eggs on toast", "Banana oat porridge", "Vegetable stir fry",
            "Tomato and onion salad", "Mashed potatoes", "Chicken and rice", "Beef mince tacos",
            "Spinach omelette", "Lentil soup", "French toast", "Garlic bread", "Potato and egg hash",
            "Tuna pasta", "Rice pudding");

    private static String json;
    private static List<RecipeWithIngredients> recipes;

    @BeforeClass
    public static void readTheRealAsset() throws IOException {
        try (InputStream in = RecipesJsonTest.class.getResourceAsStream(ASSET)) {
            assertNotNull(ASSET + " is not on the test classpath; see sourceSets in app/build.gradle", in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; ) {
                bytes.write(buffer, 0, read);
            }
            json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        recipes = RecipeJsonParser.parse(json);
    }

    @Test
    public void holdsExactlyTheTwentyPinnedRecipes() {
        List<String> names = new ArrayList<>();
        for (RecipeWithIngredients recipe : recipes) {
            names.add(recipe.getRecipe().getName());
        }
        assertEquals(PINNED_NAMES, names);
    }

    @Test
    public void everyRecipeHas3To8IngredientsAnd2To6Steps() {
        for (RecipeWithIngredients recipe : recipes) {
            String name = recipe.getRecipe().getName();
            int ingredients = recipe.getIngredients().size();
            int steps = recipe.getRecipe().getSteps().size();
            assertTrue(name + " has " + ingredients + " ingredients", ingredients >= 3 && ingredients <= 8);
            assertTrue(name + " has " + steps + " steps", steps >= 2 && steps <= 6);
        }
    }

    @Test
    public void everyRecipeStatesItsServings() {
        for (RecipeWithIngredients recipe : recipes) {
            Integer servings = recipe.getRecipe().getServings();
            assertNotNull(recipe.getRecipe().getName() + " has no servings", servings);
            assertTrue(recipe.getRecipe().getName() + " serves " + servings, servings >= 1);
        }
    }

    @Test
    public void everyRecipeNeedsDifferentIngredients() {
        for (RecipeWithIngredients recipe : recipes) {
            List<String> seen = new ArrayList<>();
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                assertFalse(recipe.getRecipe().getName() + " lists " + ingredient.getName() + " twice",
                        seen.contains(ingredient.getName()));
                seen.add(ingredient.getName());
            }
        }
    }

    @Test
    public void ingredientNamesAreTrimmedLowerCaseAndSingleSpaced() {
        for (RecipeWithIngredients recipe : recipes) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                String name = ingredient.getName();
                String where = recipe.getRecipe().getName() + ": \"" + name + "\"";
                assertEquals(where + " is not canonical", name.trim().toLowerCase(Locale.ROOT), name);
                assertFalse(where + " has a double space", name.contains("  "));
            }
        }
    }

    @Test
    public void unitsAreWrittenAsTheExactUnitSymbols() throws JSONException {
        // The parser forgives " KG "; the asset should not need forgiving
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject recipe = array.getJSONObject(i);
            JSONArray ingredients = recipe.getJSONArray(RecipeJsonParser.INGREDIENTS_KEY);
            for (int j = 0; j < ingredients.length(); j++) {
                String symbol = ingredients.getJSONObject(j).getString(RecipeJsonParser.UNIT_KEY);
                assertEquals(recipe.getString(RecipeJsonParser.NAME_KEY) + ": unit \"" + symbol + "\"",
                        Unit.fromSymbol(symbol).symbol(), symbol);
            }
        }
    }

    @Test
    public void everyIngredientKeepsOneUnitKindAcrossTheAsset() {
        Map<String, UnitKind> kindOf = new HashMap<>();
        Map<String, String> firstSeenIn = new HashMap<>();
        for (RecipeWithIngredients recipe : recipes) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                UnitKind kind = ingredient.getUnit().kind();
                UnitKind earlier = kindOf.putIfAbsent(ingredient.getName(), kind);
                firstSeenIn.putIfAbsent(ingredient.getName(), recipe.getRecipe().getName());
                assertTrue(ingredient.getName() + " is " + kind + " in " + recipe.getRecipe().getName()
                                + " but " + earlier + " in " + firstSeenIn.get(ingredient.getName()),
                        earlier == null || earlier == kind);
            }
        }
    }

    @Test
    public void noRecipeRequiresWater() {
        for (RecipeWithIngredients recipe : recipes) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                assertFalse(recipe.getRecipe().getName() + " lists water", ingredient.getName().contains("water"));
            }
        }
    }

    @Test
    public void ingredientsOverlapSoASmallPantryMatchesSeveralRecipes() {
        // The demo (Issue 26) needs shared staples: eggs, butter, milk and bread each in four or more
        Map<String, Integer> recipesUsing = new TreeMap<>();
        for (RecipeWithIngredients recipe : recipes) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                recipesUsing.merge(ingredient.getName(), 1, Integer::sum);
            }
        }
        for (String staple : Arrays.asList("egg", "butter", "milk", "bread")) {
            assertTrue(staple + " is in only " + recipesUsing.get(staple) + " recipes: " + recipesUsing,
                    recipesUsing.getOrDefault(staple, 0) >= 4);
        }
    }
}
