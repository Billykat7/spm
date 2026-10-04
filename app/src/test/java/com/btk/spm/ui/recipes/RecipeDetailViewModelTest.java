package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.DisplayQuantity;
import com.btk.spm.domain.DisplayUnit;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.matching.IngredientNormaliser;
import com.btk.spm.domain.matching.StrictMatcher;
import com.btk.spm.domain.matching.UnitConverter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * {@link RecipeDetailViewModel} on the JVM with {@code MutableLiveData} in place of Room's two
 * queries, and an executor that holds each match until the test runs it, in any order.
 *
 * <p>The recipe is Garlic bread as the seed writes it: 4 pcs of bread, 50 g of butter and 3 pcs of
 * garlic. Every row's mark and amounts come from the matcher's checks.
 */
public class RecipeDetailViewModelTest {

    @Rule
    public final InstantTaskExecutorRule liveDataOnTheTestThread = new InstantTaskExecutorRule();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final long CREATED = 1_790_000_000_000L;

    private static final RecipeWithIngredients GARLIC_BREAD = new RecipeWithIngredients(
            new Recipe(17, "Garlic bread", 2, List.of("Mix the butter and garlic.", "Spread and bake.")),
            List.of(new RecipeIngredient(51, 17, "bread", 4, Unit.PCS),
                    new RecipeIngredient(52, 17, "butter", 50, Unit.G),
                    new RecipeIngredient(53, 17, "garlic", 3, Unit.PCS)));

    private static final StrictMatcher MATCHER =
            new StrictMatcher(new IngredientNormaliser(Map.of()), new UnitConverter());

    private final MutableLiveData<RecipeWithIngredients> recipe = new MutableLiveData<>();
    private final MutableLiveData<List<PantryItem>> pantry = new MutableLiveData<>();
    private final QueuedExecutor jobs = new QueuedExecutor();
    private final List<DetailUiState> states = new ArrayList<>();

    private boolean countExpired;
    private RecipeDetailViewModel viewModel;

    @Before
    public void createTheViewModel() {
        viewModel = new RecipeDetailViewModel(recipe, pantry, () -> MATCHER, () -> countExpired, () -> TODAY, jobs);
        viewModel.getState().observeForever(states::add);
    }

    @Test
    public void theFirstState_isLoading_untilBothAreRead() {
        recipe.setValue(GARLIC_BREAD);

        assertEquals(List.of(DetailUiState.Loading.INSTANCE), states);
        assertEquals(0, jobs.size());
    }

    @Test
    public void anUnknownId_isNotFound_andNothingIsMatched() {
        recipe.setValue(null);
        pantry.setValue(List.of(item("bread", 4, Unit.PCS)));

        assertSame(DetailUiState.NotFound.INSTANCE, viewModel.getState().getValue());
        assertEquals(0, jobs.size());
    }

    @Test
    public void everyIngredient_getsOneRow_inTheRecipesOrder_keyedOnItsRowId() {
        deliver(List.of());

        List<IngredientRow> rows = loaded().rows();
        assertEquals(List.of(51L, 52L, 53L), rows.stream().map(IngredientRow::ingredientId).toList());
        assertEquals(List.of("bread", "butter", "garlic"), rows.stream().map(IngredientRow::name).toList());
    }

    @Test
    public void aCoveredLine_isHave_withThePantrysAmountInDisplayUnits() {
        deliver(List.of(item("Butter", 1, Unit.KG)));

        IngredientRow butter = loaded().rows().get(1);
        assertTrue(butter.have());
        assertEquals(new DisplayQuantity(50, DisplayUnit.G), butter.required());
        assertEquals(new DisplayQuantity(1, DisplayUnit.KG), butter.available());
    }

    @Test
    public void aShortLine_isNeed_withWhatThereIs() {
        deliver(List.of(item("garlic", 1, Unit.PCS)));

        IngredientRow garlic = loaded().rows().get(2);
        assertFalse(garlic.have());
        assertEquals(new DisplayQuantity(1, DisplayUnit.PCS), garlic.available());
    }

    @Test
    public void aMissingLine_isNeed_withNothingAvailable() {
        deliver(List.of());

        IngredientRow bread = loaded().rows().get(0);
        assertFalse(bread.have());
        assertNull(bread.available());
        assertEquals(MatchStatus.CANNOT_MAKE, loaded().result().status());
    }

    @Test
    public void theRowsMarks_areTheMatchersChecks() {
        deliver(List.of(item("bread", 4, Unit.PCS), item("butter", 50, Unit.G)));

        DetailUiState.Loaded loaded = loaded();
        for (int i = 0; i < loaded.rows().size(); i++) {
            assertEquals(loaded.result().checks().get(i).satisfied(), loaded.rows().get(i).have());
        }
        assertEquals(MatchStatus.ALMOST_THERE, loaded.result().status());
    }

    @Test
    public void addingTheMissingItem_turnsItsCrossIntoACheck() {
        List<PantryItem> twoOfThree = List.of(item("bread", 4, Unit.PCS), item("butter", 50, Unit.G));
        deliver(twoOfThree);
        assertFalse(loaded().rows().get(2).have());

        List<PantryItem> all = new ArrayList<>(twoOfThree);
        all.add(item("garlic", 3, Unit.PCS));
        pantry.setValue(all);
        jobs.runAll();

        assertTrue(loaded().rows().get(2).have());
        assertEquals(MatchStatus.CAN_MAKE, loaded().result().status());
    }

    @Test
    public void anExpiredItem_isNeed_unlessTheSettingCountsIt() {
        PantryItem oldGarlic = new PantryItem(0, "garlic", 3, Unit.PCS, TODAY.minusDays(1), CREATED);
        deliver(List.of(oldGarlic));
        assertFalse(loaded().rows().get(2).have());

        countExpired = true;
        pantry.setValue(List.of(oldGarlic));
        jobs.runAll();
        assertTrue(loaded().rows().get(2).have());
    }

    @Test
    public void twoPantriesInQuickSuccession_endOnTheSecond() {
        recipe.setValue(GARLIC_BREAD);
        pantry.setValue(List.of());                              // job 1: three crosses
        pantry.setValue(List.of(item("garlic", 3, Unit.PCS)));   // job 2: garlic covered

        jobs.runNewestFirst();

        assertTrue(loaded().rows().get(2).have());
    }

    @Test
    public void aJobStillRunning_whenTheScreenIsGone_postsNothing() {
        recipe.setValue(GARLIC_BREAD);
        pantry.setValue(List.of());

        viewModel.onCleared();
        jobs.runAll();

        assertEquals(List.of(DetailUiState.Loading.INSTANCE), states);
    }

    private void deliver(List<PantryItem> items) {
        recipe.setValue(GARLIC_BREAD);
        pantry.setValue(items);
        jobs.runAll();
    }

    private DetailUiState.Loaded loaded() {
        DetailUiState state = viewModel.getState().getValue();
        assertTrue("Expected Loaded, was " + state, state instanceof DetailUiState.Loaded);
        return (DetailUiState.Loaded) state;
    }

    private static PantryItem item(String name, double quantity, Unit unit) {
        return new PantryItem(0, name, quantity, unit, null, CREATED);
    }

    /** Holds each job until the test runs it, so a test decides when and in which order jobs finish. */
    private static final class QueuedExecutor implements Executor {

        private final Deque<Runnable> queued = new ArrayDeque<>();

        @Override
        public void execute(Runnable job) {
            queued.addLast(job);
        }

        int size() {
            return queued.size();
        }

        void runAll() {
            while (!queued.isEmpty()) {
                queued.removeFirst().run();
            }
        }

        void runNewestFirst() {
            while (!queued.isEmpty()) {
                queued.removeLast().run();
            }
        }
    }
}
