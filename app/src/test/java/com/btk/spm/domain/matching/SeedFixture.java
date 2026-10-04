package com.btk.spm.domain.matching;

import static org.junit.Assert.assertNotNull;

import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.data.seed.AliasLoader;
import com.btk.spm.data.seed.RecipeJsonParser;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.domain.Quantity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The real seed for the engine's JVM tests (Issue 21): the twenty recipes of {@code recipes.json}
 * and the alias table of {@code aliases.json}, both read from the test classpath, where
 * {@code app/build.gradle} puts {@code src/main/assets}. The recipes are mapped to {@link RecipeSpec}
 * the way the repository will map them (Issue 23); a recipe's id is its position, from 1.
 */
final class SeedFixture {

    /** The seed's recipes, in the asset's order. */
    static final List<RecipeWithIngredients> RECIPES =
            RecipeJsonParser.parse(read("/" + RecipeSeeder.ASSET_NAME));

    /** The normaliser the app builds, over the shipped alias table. */
    static final IngredientNormaliser NORMALISER =
            new IngredientNormaliser(AliasLoader.parse(read("/" + AliasLoader.ASSET_NAME)));

    /** A matcher over {@link #NORMALISER}. */
    static final StrictMatcher MATCHER = new StrictMatcher(NORMALISER, new UnitConverter());

    private SeedFixture() {
        // Static fixture; never instantiated
    }

    /** Every recipe as the matcher takes it. */
    static List<RecipeSpec> specs() {
        List<RecipeSpec> specs = new ArrayList<>();
        for (int i = 0; i < RECIPES.size(); i++) {
            specs.add(spec(i));
        }
        return Collections.unmodifiableList(specs);
    }

    /** The recipe at {@code index} (from 0) as the matcher takes it, with id {@code index + 1}. */
    static RecipeSpec spec(int index) {
        List<RequiredIngredient> lines = new ArrayList<>();
        for (RecipeIngredient ingredient : RECIPES.get(index).getIngredients()) {
            lines.add(new RequiredIngredient(ingredient.getName(),
                    new Quantity(ingredient.getQuantity(), ingredient.getUnit())));
        }
        return new RecipeSpec(index + 1, lines);
    }

    /** The recipe's name, for messages. */
    static String name(int index) {
        return RECIPES.get(index).getRecipe().getName();
    }

    /** A pantry holding exactly what the recipe requires: one row per line, same name and amount, no expiry. */
    static List<PantryEntry> exactPantryFor(RecipeSpec recipe) {
        List<PantryEntry> pantry = new ArrayList<>();
        for (RequiredIngredient line : recipe.ingredients()) {
            pantry.add(new PantryEntry(line.name(), line.quantity(), null));
        }
        return pantry;
    }

    private static String read(String resource) {
        try (InputStream in = SeedFixture.class.getResourceAsStream(resource)) {
            assertNotNull(resource + " is not on the test classpath; see sourceSets in app/build.gradle", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
