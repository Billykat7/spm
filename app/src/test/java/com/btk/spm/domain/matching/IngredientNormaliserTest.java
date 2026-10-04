package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link IngredientNormaliser#normalise} row by row (Issue 18): one row per rule, per exception and
 * per deliberate limit, named so the report can quote it. Each row checks the result and that
 * normalising the result changes nothing, because the matcher may meet a name twice: once as typed
 * and once as stored.
 *
 * <p>The alias rows use a small table written here, so this test proves the alias step and not the
 * contents of {@code assets/aliases.json}; {@code AliasesJsonTest} checks the shipped table.
 */
@RunWith(Parameterized.class)
public class IngredientNormaliserTest {

    /** The issue's aliases, written loosely on purpose: the normaliser tidies keys and targets too. */
    static final Map<String, String> ALIASES = Map.of(
            "cilantro", "coriander",
            "Scallion", "spring onion",
            "capsicum", "bell pepper",
            "courgette", "zucchini",
            "aubergine", "eggplant",
            "garbanzo beans", "chickpea",
            "rocket", "arugula",
            "caster sugar", "superfine sugar");

    private static final IngredientNormaliser NORMALISER = new IngredientNormaliser(ALIASES);

    @Parameter(0)
    public String rule;

    @Parameter(1)
    public String raw;

    @Parameter(2)
    public String expected;

    @Parameters(name = "{index}: {0}: \"{1}\" -> \"{2}\"")
    public static List<Object[]> rows() {
        return Arrays.asList(new Object[][]{
            // Step 1: case and spaces
            {"upper case and a plural", "Tomatoes", "tomato"},
            {"spaces either side", " tomato ", "tomato"},
            {"a run of spaces inside", "  Spring   Onions ", "spring onion"},
            {"a tab inside", "spring\tonion", "spring onion"},
            {"a no-break space inside", "spring\u00A0onion", "spring onion"},
            // Step 2: punctuation
            {"a trailing full stop", "TOMATO.", "tomato"},
            {"repeated exclamation marks", "Rice!!!", "rice"},
            {"a comma becomes a space", "salt,pepper", "salt pepper"},
            {"quotes around a word", "'egg'", "egg"},
            {"hyphens at the edges", "-tomato-", "tomato"},
            {"an internal hyphen stays", "self-raising flour", "self-raising flour"},
            {"an internal apostrophe stays", "baker's yeast", "baker's yeast"},
            {"a curly apostrophe is a straight one", "Baker\u2019s yeast", "baker's yeast"},
            {"nothing but punctuation", "...", ""},
            {"empty", "", ""},
            {"blank", "   ", ""},
            {"null", null, ""},
            // Step 3: plurals, by rule
            {"-ies to -y", "berries", "berry"},
            {"-ies to -y again", "cherries", "cherry"},
            {"-ies to -y, longer word", "anchovies", "anchovy"},
            {"-oes to -o", "potatoes", "potato"},
            {"-oes to -o again", "mangoes", "mango"},
            {"-ches drops es", "peaches", "peach"},
            {"-shes drops es", "radishes", "radish"},
            {"-sses drops es", "lemongrasses", "lemongrass"},
            {"-xes drops es", "cake mixes", "cake mix"},
            {"-ves to -f", "loaves", "loaf"},
            {"-lves to -lf", "halves", "half"},
            {"-ves to -f in the last word", "bay leaves", "bay leaf"},
            {"-s dropped", "eggs", "egg"},
            {"-s dropped after a vowel", "avocados", "avocado"},
            {"-ces only drops s", "sauces", "sauce"},
            {"-ses only drops s", "cheeses", "cheese"},
            {"peas is the plural of pea", "peas", "pea"},
            {"only the last word changes", "brussels sprouts", "brussels sprout"},
            {"a three-letter word stays", "cos", "cos"},
            {"-ies on a short word only drops s", "pies", "pie"},
            {"olives are not olifs", "olives", "olive"},
            {"cloves are not clofs", "cloves", "clove"},
            {"chives are not chifs", "chives", "chive"},
            {"a word ending in ss is singular", "watercress", "watercress"},
            {"already singular", "tomato", "tomato"},
            // Step 3: singular words that end in s, the exceptions set
            {"exception: asparagus", "Asparagus", "asparagus"},
            {"exception: couscous", "couscous", "couscous"},
            {"exception: hummus", "hummus", "hummus"},
            {"exception: molasses", "molasses", "molasses"},
            {"exception: oats", "oats", "oats"},
            // Step 4: aliases
            {"alias", "Cilantro", "coriander"},
            {"an alias target is itself", "coriander", "coriander"},
            {"alias after the plural", "Scallions", "spring onion"},
            {"alias of two words", "garbanzo beans", "chickpea"},
            {"alias after case and plural", "Courgettes", "zucchini"},
            {"alias to two words", "capsicum", "bell pepper"},
            {"alias in capitals", "AUBERGINE", "eggplant"},
            {"alias rocket", "rocket", "arugula"},
            {"alias caster sugar", "caster sugar", "superfine sugar"},
            {"an alias matches the whole name only", "baby rocket", "baby rocket"},
            // Deliberate limits: no stemming, no fuzzy matching
            {"no fuzzy matching of a typo", "tomatoe", "tomatoe"},
            {"no stemming of a describing word", "grated cheese", "grated cheese"},
        });
    }

    @Test
    public void normalise_givesTheCanonicalName() {
        assertEquals(expected, NORMALISER.normalise(raw));
    }

    @Test
    public void normalise_leavesTheCanonicalNameAsItIs() {
        assertEquals(expected, NORMALISER.normalise(expected));
    }
}
