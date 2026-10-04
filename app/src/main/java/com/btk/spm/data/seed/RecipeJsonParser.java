package com.btk.spm.data.seed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Unit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the text of {@code assets/recipes.json} into recipes ready to insert, each with its
 * ingredients and ids of {@code 0}.
 *
 * <p>The document is a JSON array of objects:
 * <pre>{@code
 * {"name": "Cheese omelette", "servings": 1,
 *  "ingredients": [{"name": "egg", "quantity": 3, "unit": "pcs"}, ...],
 *  "steps": ["Whisk the eggs with the salt.", ...]}
 * }</pre>
 *
 * <p>The parser checks structure and values, not editorial choices: every key present with the
 * right JSON type, quantities and servings above zero, every unit one of {@link Unit}'s symbols,
 * no two recipes with the same name (ignoring case), no empty list and no blank text. Anything else
 * throws an {@link IllegalArgumentException} naming the recipe and the field, so a bad asset fails a
 * JVM test with a message that says where to look, rather than putting a half-read recipe in the
 * app. The editorial rules (twenty recipes, 3 to 8 ingredients, one unit kind per ingredient) are
 * {@code RecipesJsonTest}'s.
 *
 * <p>{@link #parse} is the strict reading the JVM tests use on the shipped asset. The app seeds with
 * {@link #parseSkippingBroken} instead (Issue 31): the same rules, but a recipe that breaks one is
 * left out and named, so a damaged file still seeds what it can and never stops the app.
 *
 * <p>Plain Java plus {@code org.json}, which ships with Android, so the app takes no new dependency
 * and the parser runs on the JVM with the real {@code org.json} on the test classpath.
 */
public final class RecipeJsonParser {

    /** JSON key of a recipe's or an ingredient's name. */
    static final String NAME_KEY = "name";
    /** JSON key of how many people a recipe feeds; optional. */
    static final String SERVINGS_KEY = "servings";
    /** JSON key of a recipe's ingredient list. */
    static final String INGREDIENTS_KEY = "ingredients";
    /** JSON key of a recipe's method, one string per step. */
    static final String STEPS_KEY = "steps";
    /** JSON key of an ingredient's amount. */
    static final String QUANTITY_KEY = "quantity";
    /** JSON key of an ingredient's unit symbol, such as {@code "g"}. */
    static final String UNIT_KEY = "unit";

    private RecipeJsonParser() {
        // Static parser; never instantiated
    }

    /**
     * Parses the whole document.
     *
     * @param json the text of {@code recipes.json}
     * @return every recipe in document order, each with its ingredients in document order; ids are
     *     {@code 0} until inserted
     * @throws IllegalArgumentException if the document breaks any rule above; the message names the
     *     recipe (by name, or by position when the name itself is the problem) and the field
     */
    @NonNull
    public static List<RecipeWithIngredients> parse(@NonNull String json) {
        JSONArray array;
        try {
            array = new JSONArray(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("recipes.json is not a JSON array: " + e.getMessage(), e);
        }
        List<RecipeWithIngredients> recipes = new ArrayList<>(array.length());
        Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            String position = "recipe " + (i + 1);
            if (!(item instanceof JSONObject)) {
                throw invalid(position, "is not a JSON object");
            }
            RecipeWithIngredients recipe = parseRecipe((JSONObject) item, position);
            if (!seenNames.add(recipe.getRecipe().getName().toLowerCase(Locale.ROOT))) {
                throw invalid(where(recipe.getRecipe().getName()), "is a duplicate name");
            }
            recipes.add(recipe);
        }
        return Collections.unmodifiableList(recipes);
    }

    /**
     * What {@link #parseSkippingBroken} read: the recipes that passed every rule, and one line for each
     * one left out.
     *
     * @param recipes the good recipes, in document order, ids {@code 0}
     * @param skipped why each broken one was left out, such as {@code recipe 3: "unit": ...}
     */
    public record Report(@NonNull List<RecipeWithIngredients> recipes, @NonNull List<String> skipped) {

        /**
         * Creates a report, keeping unmodifiable copies.
         */
        public Report {
            recipes = List.copyOf(recipes);
            skipped = List.copyOf(skipped);
        }
    }

    /**
     * Parses the document as {@link #parse} does, but leaves out each recipe that breaks a rule instead
     * of throwing: the rest still seeds. A document that is not a JSON array at all gives no recipes and
     * one line saying so. A recipe whose name repeats an earlier one is left out too.
     *
     * @param json the text of the recipes file
     * @return the good recipes and one line per recipe left out, by position or name
     */
    @NonNull
    public static Report parseSkippingBroken(@NonNull String json) {
        JSONArray array;
        try {
            array = new JSONArray(json);
        } catch (JSONException e) {
            return new Report(List.of(), List.of("the file is not a JSON array: " + e.getMessage()));
        }
        List<RecipeWithIngredients> recipes = new ArrayList<>(array.length());
        List<String> skipped = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            String position = "recipe " + (i + 1);
            try {
                if (!(item instanceof JSONObject)) {
                    throw invalid(position, "is not a JSON object");
                }
                RecipeWithIngredients recipe = parseRecipe((JSONObject) item, position);
                if (!seenNames.add(recipe.getRecipe().getName().toLowerCase(Locale.ROOT))) {
                    throw invalid(position + " (" + recipe.getRecipe().getName() + ")", "is a duplicate name");
                }
                recipes.add(recipe);
            } catch (IllegalArgumentException broken) {
                skipped.add(broken.getMessage());
            }
        }
        return new Report(recipes, skipped);
    }

    private static RecipeWithIngredients parseRecipe(JSONObject object, String position) {
        String name = requiredText(object, NAME_KEY, position);
        String recipe = where(name);
        Integer servings = optionalPositiveInt(object, SERVINGS_KEY, recipe);

        JSONArray ingredientArray = requiredNonEmptyArray(object, INGREDIENTS_KEY, recipe);
        List<RecipeIngredient> ingredients = new ArrayList<>(ingredientArray.length());
        for (int i = 0; i < ingredientArray.length(); i++) {
            String ingredient = recipe + ", ingredient " + (i + 1);
            Object item = ingredientArray.opt(i);
            if (!(item instanceof JSONObject)) {
                throw invalid(ingredient, "is not a JSON object");
            }
            ingredients.add(parseIngredient((JSONObject) item, ingredient));
        }

        JSONArray stepArray = requiredNonEmptyArray(object, STEPS_KEY, recipe);
        List<String> steps = new ArrayList<>(stepArray.length());
        for (int i = 0; i < stepArray.length(); i++) {
            Object step = stepArray.opt(i);
            if (!(step instanceof String) || ((String) step).trim().isEmpty()) {
                throw invalid(recipe + ", step " + (i + 1), "is not a non-blank string");
            }
            steps.add((String) step);
        }
        return new RecipeWithIngredients(new Recipe(name, servings, steps), ingredients);
    }

    private static RecipeIngredient parseIngredient(JSONObject object, String ingredient) {
        String name = requiredText(object, NAME_KEY, ingredient);
        String where = ingredient + " (" + name + ")";
        double quantity = requiredPositiveNumber(object, QUANTITY_KEY, where);
        String symbol = requiredText(object, UNIT_KEY, where);
        Unit unit;
        try {
            unit = Unit.fromSymbol(symbol);
        } catch (IllegalArgumentException e) {
            throw invalid(where, UNIT_KEY + ": " + e.getMessage());
        }
        return new RecipeIngredient(name, quantity, unit);
    }

    private static String requiredText(JSONObject object, String key, String where) {
        Object value = object.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw invalid(where, "\"" + key + "\" is missing or not a non-blank string");
        }
        return (String) value;
    }

    private static double requiredPositiveNumber(JSONObject object, String key, String where) {
        Object value = object.opt(key);
        if (!(value instanceof Number)) {
            throw invalid(where, "\"" + key + "\" is missing or not a number");
        }
        double number = ((Number) value).doubleValue();
        if (!(number > 0) || Double.isInfinite(number)) {
            throw invalid(where, "\"" + key + "\" must be more than 0, was " + value);
        }
        return number;
    }

    @Nullable
    private static Integer optionalPositiveInt(JSONObject object, String key, String where) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        Object value = object.opt(key);
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 1
                || ((Number) value).longValue() > Integer.MAX_VALUE) {
            throw invalid(where, "\"" + key + "\" must be a whole number of at least 1, was " + value);
        }
        return ((Number) value).intValue();
    }

    private static JSONArray requiredNonEmptyArray(JSONObject object, String key, String where) {
        Object value = object.opt(key);
        if (!(value instanceof JSONArray) || ((JSONArray) value).length() == 0) {
            throw invalid(where, "\"" + key + "\" is missing or not a non-empty array");
        }
        return (JSONArray) value;
    }

    private static String where(String recipeName) {
        return "recipe \"" + recipeName + "\"";
    }

    private static IllegalArgumentException invalid(String where, String problem) {
        return new IllegalArgumentException("recipes.json, " + where + ": " + problem);
    }
}
