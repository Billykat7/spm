package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.data.seed.AliasLoader;
import com.btk.spm.data.seed.RecipeJsonParser;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.matching.IngredientNormaliser;
import com.btk.spm.domain.matching.StrictMatcher;
import com.btk.spm.domain.matching.UnitConverter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The live re-evaluation the video films (brief §5.1 part 2), proven on the JVM (Issue 26): one pantry
 * change moves Tomato pasta between "almost there" and the suggestions, and back.
 *
 * <p>{@link SuggestedRecipesViewModel} runs over its three real kinds of source, as {@code
 * MutableLiveData}: the pantry, the recipes and the {@code COUNT_EXPIRED_ITEMS} setting. The executor
 * is direct ({@code Runnable::run}) and {@link InstantTaskExecutorRule} delivers {@code postValue} at
 * once, so every push is matched before the next line runs. Tomato pasta and Garlic bread are read
 * from the shipped seed asset, which is the truth for their ingredients. Every push must give
 * exactly one new state: a duplicate or a stale one fails the count.
 */
public class SuggestedRecipesViewModelLiveTest {

    @Rule
    public final InstantTaskExecutorRule liveDataOnTheTestThread = new InstantTaskExecutorRule();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final long CREATED = 1_790_000_000_000L;

    private static final StrictMatcher MATCHER = new StrictMatcher(
            new IngredientNormaliser(AliasLoader.parse(read("/" + AliasLoader.ASSET_NAME))), new UnitConverter());

    /** The five-ingredient recipe the demo uses (Issue 11), and one other, as Room would read them. */
    private static final RecipeWithIngredients TOMATO_PASTA = seedRecipe("Tomato pasta");
    private static final RecipeWithIngredients GARLIC_BREAD = seedRecipe("Garlic bread");

    private final MutableLiveData<List<PantryItem>> pantry = new MutableLiveData<>();
    private final MutableLiveData<List<RecipeWithIngredients>> recipes = new MutableLiveData<>();
    private final MutableLiveData<Boolean> countExpired = new MutableLiveData<>(false);
    private final List<UiState> states = new ArrayList<>();

    private SuggestedRecipesViewModel viewModel;

    @Before
    public void observeTheViewModel() {
        viewModel = new SuggestedRecipesViewModel(pantry, recipes, countExpired, () -> MATCHER, () -> TODAY,
                Runnable::run);
        viewModel.getState().observeForever(states::add);
        recipes.setValue(List.of(TOMATO_PASTA, GARLIC_BREAD));
    }

    @Test
    public void theFifthIngredient_movesTheRecipeIn_andTakingItAway_movesItBack() {
        List<PantryItem> four = withoutGarlic(allOf(TOMATO_PASTA));
        List<PantryItem> five = allOf(TOMATO_PASTA);

        push(four);
        assertTrue("four of five is not suggested", canMake().isEmpty());
        assertEquals(List.of("Tomato pasta"), almostThere());

        push(five);
        assertEquals("the fifth ingredient puts it in", List.of("Tomato pasta"), canMake());
        assertFalse("and takes it out of almost there", almostThere().contains("Tomato pasta"));

        push(four);
        assertTrue("taking it away again takes it out", canMake().isEmpty());
        assertEquals(List.of("Tomato pasta"), almostThere());
    }

    @Test
    public void eachPantryPush_givesExactlyOneNewState() {
        assertEquals("Loading only, before the pantry arrives", 1, states.size());
        List<PantryItem> four = withoutGarlic(allOf(TOMATO_PASTA));

        push(four);
        assertEquals(2, states.size());
        push(allOf(TOMATO_PASTA));
        assertEquals(3, states.size());
        push(four);
        assertEquals(4, states.size());

        assertTrue(states.get(1) instanceof UiState.Empty);
        assertTrue(states.get(2) instanceof UiState.Content);
        assertTrue(states.get(3) instanceof UiState.Empty);
    }

    @Test
    public void anItemExpiredYesterday_countsOnlyWhileTheSettingIsOn() {
        List<PantryItem> garlicExpired = new ArrayList<>(withoutGarlic(allOf(TOMATO_PASTA)));
        garlicExpired.add(new PantryItem(0, "garlic", 2, Unit.PCS, TODAY.minusDays(1), CREATED));

        push(garlicExpired);
        assertTrue("expired garlic is absent by default", canMake().isEmpty());
        assertEquals(List.of("Tomato pasta"), almostThere());

        int before = states.size();
        countExpired.setValue(true);
        assertEquals("turning the setting on runs the matcher once", before + 1, states.size());
        assertEquals(List.of("Tomato pasta"), canMake());

        countExpired.setValue(false);
        assertEquals(before + 2, states.size());
        assertTrue(canMake().isEmpty());
    }

    /** Pushes a new pantry and checks it produced one state, as the observer counts it. */
    private void push(List<PantryItem> items) {
        int before = states.size();
        pantry.setValue(items);
        assertEquals("one push, one new state", before + 1, states.size());
    }

    private List<String> canMake() {
        UiState state = states.get(states.size() - 1);
        return state instanceof UiState.Content content ? names(content.canMake()) : List.of();
    }

    private List<String> almostThere() {
        UiState state = states.get(states.size() - 1);
        if (state instanceof UiState.Content content) {
            return names(content.almostThere());
        }
        return names(((UiState.Empty) state).almostThere());
    }

    private static List<String> names(List<MatchedRecipe> rows) {
        return rows.stream().map(r -> r.recipe().getRecipe().getName()).collect(Collectors.toList());
    }

    /** One pantry row per line of {@code recipe}, in exactly the amount and unit the seed writes. */
    private static List<PantryItem> allOf(RecipeWithIngredients recipe) {
        List<PantryItem> items = new ArrayList<>();
        for (RecipeIngredient line : recipe.getIngredients()) {
            items.add(new PantryItem(0, line.getName(), line.getQuantity(), line.getUnit(), null, CREATED));
        }
        return items;
    }

    private static List<PantryItem> withoutGarlic(List<PantryItem> items) {
        return items.stream().filter(i -> !i.getName().equals("garlic")).collect(Collectors.toList());
    }

    /** The named recipe from {@code recipes.json}, with its position in the asset (from 1) as its id. */
    private static RecipeWithIngredients seedRecipe(String name) {
        List<RecipeWithIngredients> parsed = RecipeJsonParser.parse(read("/" + RecipeSeeder.ASSET_NAME));
        for (int i = 0; i < parsed.size(); i++) {
            Recipe r = parsed.get(i).getRecipe();
            if (r.getName().equals(name)) {
                long id = i + 1;
                List<RecipeIngredient> lines = new ArrayList<>();
                for (RecipeIngredient line : parsed.get(i).getIngredients()) {
                    lines.add(line.withRecipeId(id));
                }
                return new RecipeWithIngredients(new Recipe(id, r.getName(), r.getServings(), r.getSteps()), lines);
            }
        }
        throw new AssertionError(name + " is not in the seed");
    }

    private static String read(String resource) {
        try (InputStream in = SuggestedRecipesViewModelLiveTest.class.getResourceAsStream(resource)) {
            assertNotNull(resource + " is not on the test classpath; see sourceSets in app/build.gradle", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
