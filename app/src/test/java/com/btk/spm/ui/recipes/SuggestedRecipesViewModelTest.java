package com.btk.spm.ui.recipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.btk.spm.data.mapping.PantryEntryMapper;
import com.btk.spm.data.mapping.RecipeSpecMapper;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.data.seed.AliasLoader;
import com.btk.spm.data.seed.RecipeJsonParser;
import com.btk.spm.data.seed.RecipeSeeder;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;
import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.domain.matching.CanonicalQuantity;
import com.btk.spm.domain.matching.IngredientNormaliser;
import com.btk.spm.domain.matching.MatchOptions;
import com.btk.spm.domain.matching.MatchResult;
import com.btk.spm.domain.matching.MatchResults;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * {@link SuggestedRecipesViewModel} on the JVM, over the twenty real seed recipes and the real alias
 * table, with {@code MutableLiveData} in place of Room's queries.
 *
 * <p>Most tests run with {@link InstantTaskExecutorRule} and a {@link QueuedExecutor}, which holds
 * each match job until the test runs it, in any order. {@link #matchAll_runsOnTheExecutorsThread_andTheScreenOnlyGetsWhatIsPosted()}
 * replaces both with real threads: a named matching thread, and the test's own thread playing the
 * main thread. It fails if {@code matchAll} ever runs anywhere but the executor's thread.
 */
public class SuggestedRecipesViewModelTest {

    @Rule
    public final InstantTaskExecutorRule liveDataOnTheTestThread = new InstantTaskExecutorRule();

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final long CREATED = 1_790_000_000_000L;

    /** The seed as Room would read it after the first run: ids 1 to 20 in the asset's order. */
    private static final List<RecipeWithIngredients> SEED = seed();

    private static final StrictMatcher MATCHER = new StrictMatcher(
            new IngredientNormaliser(AliasLoader.parse(read("/" + AliasLoader.ASSET_NAME))), new UnitConverter());

    private final MutableLiveData<List<PantryItem>> pantry = new MutableLiveData<>();
    private final MutableLiveData<List<RecipeWithIngredients>> recipes = new MutableLiveData<>();
    private final QueuedExecutor jobs = new QueuedExecutor();
    private final List<UiState> states = new ArrayList<>();

    private final MutableLiveData<Boolean> countExpired = new MutableLiveData<>(false);
    private LocalDate today = TODAY;
    private SuggestedRecipesViewModel viewModel;

    @Before
    public void createTheViewModel() {
        viewModel = new SuggestedRecipesViewModel(pantry, recipes, countExpired, new MutableLiveData<>(UnitsSystem.METRIC), () -> MATCHER,
                () -> today, jobs);
        viewModel.getState().observeForever(states::add);
    }

    @Test
    public void theFirstState_isLoading() {
        assertEquals(List.of(UiState.Loading.INSTANCE), states);
    }

    @Test
    public void recipesFirst_staysLoading_untilThePantryArrives() {
        recipes.setValue(SEED);

        assertEquals(0, jobs.size());
        assertSame(UiState.Loading.INSTANCE, viewModel.getState().getValue());

        pantry.setValue(pantryFor("Tomato pasta"));
        assertEquals(1, jobs.size());
        jobs.runAll();

        assertTrue(viewModel.getState().getValue() instanceof UiState.Content);
        assertFalse("Content was shown before both sources delivered",
                states.subList(0, states.size() - 1).stream().anyMatch(s -> s instanceof UiState.Content));
    }

    @Test
    public void pantryFirst_staysLoading_untilTheRecipesArrive() {
        pantry.setValue(pantryFor("Tomato pasta"));

        assertEquals(0, jobs.size());
        assertSame(UiState.Loading.INSTANCE, viewModel.getState().getValue());
    }

    @Test
    public void everyIngredientOfExactlyOneRecipe_listsThatRecipeAndNothingElse() {
        deliver(pantryFor("Tomato pasta"), SEED);

        assertEquals(List.of("Tomato pasta"), names(content().canMake()));
    }

    @Test
    public void fourOfFiveIngredients_isNotSuggested() {
        List<PantryItem> noGarlic = without(pantryFor("Tomato pasta"), "garlic");

        deliver(noGarlic, SEED);

        assertNothingSuggested_tomatoPastaAlmostThere();
    }

    @Test
    public void fourOfFive_isKeptAsAlmostThere_apartFromTheSuggestions() {
        List<PantryItem> items = new ArrayList<>(without(pantryFor("Tomato pasta"), "garlic"));
        items.addAll(pantryFor("Garlic bread"));
        items.removeIf(item -> item.getName().equals("garlic")); // Garlic bread added some back
        items.addAll(pantryFor("Grilled cheese sandwich"));

        deliver(items, SEED);

        assertEquals(List.of("Grilled cheese sandwich"), names(content().canMake()));
        assertTrue(names(content().almostThere()).contains("Tomato pasta"));
        assertTrue(names(content().almostThere()).contains("Garlic bread"));
    }

    @Test
    public void theSuggestions_arePartitionsCanMakeList_verbatim() {
        List<PantryItem> items = new ArrayList<>(pantryFor("Pancakes"));
        items.addAll(pantryFor("Rice pudding"));
        items.addAll(pantryFor("Garlic bread"));

        deliver(items, SEED);

        List<MatchResult> expected = MatchResults.partition(MATCHER.matchAll(PantryEntryMapper.toEntries(items),
                RecipeSpecMapper.toSpecs(SEED), MatchOptions.on(TODAY))).canMake();
        List<MatchResult> shown = content().canMake().stream().map(MatchedRecipe::result).collect(Collectors.toList());
        assertEquals(expected, shown);
        assertTrue(shown.size() >= 2);
        assertTrue(shown.stream().allMatch(r -> r.status() == MatchStatus.CAN_MAKE));
    }

    @Test
    public void eachRow_pairsTheResultWithItsOwnRecipe() {
        deliver(pantryFor("Cheese omelette"), SEED);

        MatchedRecipe row = content().canMake().get(0);
        assertEquals(row.result().recipeId(), row.recipe().getRecipe().getId());
        assertEquals(4, row.result().needCount());
        assertEquals(4, row.result().haveCount());
    }

    @Test
    public void anExpiredIngredient_isLeftOut_byDefault() {
        deliver(expired(pantryFor("Tomato pasta"), "tomato"), SEED);

        assertNothingSuggested_tomatoPastaAlmostThere();
    }

    @Test
    public void anExpiredIngredient_counts_whenTheSettingSaysSo() {
        countExpired.setValue(true);

        deliver(expired(pantryFor("Tomato pasta"), "tomato"), SEED);

        assertEquals(List.of("Tomato pasta"), names(content().canMake()));
    }

    @Test
    public void today_comesFromTheSupplier_notTheClock() {
        // Good until TODAY, so it counts today and is expired the day after
        List<PantryItem> items = withExpiry(pantryFor("Tomato pasta"), "tomato", TODAY);

        today = TODAY;
        deliver(items, SEED);
        assertTrue(viewModel.getState().getValue() instanceof UiState.Content);

        today = TODAY.plusDays(1);
        pantry.setValue(items);
        jobs.runAll();
        assertNothingSuggested_tomatoPastaAlmostThere();
    }

    @Test
    public void aPantryChange_runsTheMatchAgain() {
        deliver(pantryFor("Tomato pasta"), SEED);
        assertEquals(1, content().canMake().size());

        List<PantryItem> more = new ArrayList<>(pantryFor("Tomato pasta"));
        more.addAll(pantryFor("Grilled cheese sandwich"));
        pantry.setValue(more);
        jobs.runAll();

        assertEquals(List.of("Tomato pasta", "Grilled cheese sandwich"), names(content().canMake()));
    }

    @Test
    public void aRecipeChange_runsTheMatchAgain() {
        deliver(pantryFor("Tomato pasta"), List.of());
        assertEquals(EmptyReason.NO_RECIPES, emptyReason());

        recipes.setValue(SEED);
        jobs.runAll();

        assertEquals(List.of("Tomato pasta"), names(content().canMake()));
    }

    @Test
    public void twoPantriesInQuickSuccession_endOnTheSecond_evenWhenTheFirstFinishesLast() {
        recipes.setValue(SEED);
        pantry.setValue(without(pantryFor("Tomato pasta"), "garlic")); // job 1: nothing can be made
        pantry.setValue(pantryFor("Tomato pasta"));                    // job 2: Tomato pasta
        assertEquals(2, jobs.size());

        jobs.runNewestFirst(); // job 2 finishes, then the stale job 1

        assertEquals(List.of("Tomato pasta"), names(content().canMake()));
        assertFalse("The stale result was posted", states.stream()
                .anyMatch(s -> s instanceof UiState.Content c && c.canMake().isEmpty()));
    }

    @Test
    public void aJobStillRunning_whenTheScreenIsGone_postsNothing() {
        recipes.setValue(SEED);
        pantry.setValue(pantryFor("Tomato pasta"));

        viewModel.onCleared();
        jobs.runAll();

        assertEquals(List.of(UiState.Loading.INSTANCE), states);
    }

    @Test
    public void aPantryCloseToNothing_isTheFullScreenNoMatch() {
        // Rice alone: every seed recipe that needs it is two or more short, so nothing is almost there
        deliver(List.of(new PantryItem(0, "rice", 1, Unit.KG, null, CREATED)), SEED);

        assertEquals(EmptyReason.NO_MATCH, emptyReason());
    }

    @Test
    public void anEmptyPantry_isPantryEmpty() {
        deliver(List.of(), SEED);

        assertEquals(EmptyReason.PANTRY_EMPTY, emptyReason());
    }

    @Test
    public void noRecipes_isNoRecipes_evenWhenThePantryIsEmptyToo() {
        deliver(List.of(), List.of());

        assertEquals(EmptyReason.NO_RECIPES, emptyReason());
    }

    @Test
    public void noRecipes_isNoRecipes_withAFullPantry() {
        deliver(pantryFor("Tomato pasta"), List.of());

        assertEquals(EmptyReason.NO_RECIPES, emptyReason());
    }

    @Test
    public void aFifthUnrelatedIngredient_keepsNoMatch() {
        List<PantryItem> fourOfFive = without(pantryFor("Tomato pasta"), "garlic");
        deliver(fourOfFive, SEED);

        List<PantryItem> plusRice = new ArrayList<>(fourOfFive);
        plusRice.add(new PantryItem(0, "rice", 1, Unit.KG, null, CREATED));
        pantry.setValue(plusRice);
        jobs.runAll();

        assertNothingSuggested_tomatoPastaAlmostThere();
    }

    @Test
    public void loading_isEmittedOnce_acrossThreePantryPushes() {
        recipes.setValue(SEED);
        pantry.setValue(without(pantryFor("Tomato pasta"), "garlic"));
        jobs.runAll();
        pantry.setValue(pantryFor("Tomato pasta"));
        jobs.runAll();
        pantry.setValue(List.of());
        jobs.runAll();

        assertEquals(1, states.stream().filter(s -> s instanceof UiState.Loading).count());
        assertSame("Loading is first", UiState.Loading.INSTANCE, states.get(0));
        assertEquals(4, states.size());
        assertTrue(((UiState.Content) states.get(1)).canMake().isEmpty());
        assertTrue(states.get(2) instanceof UiState.Content);
        assertEquals(EmptyReason.PANTRY_EMPTY, ((UiState.Empty) states.get(3)).reason());
    }

    @Test
    public void whileAMatchRuns_thePreviousStateStaysOnScreen() {
        deliver(pantryFor("Tomato pasta"), SEED);
        UiState shown = viewModel.getState().getValue();

        pantry.setValue(List.of()); // the job is queued, not run

        assertSame(shown, viewModel.getState().getValue());
    }

    @Test
    public void pendingMatches_countsJobsSubmittedAndNotFinished() {
        recipes.setValue(SEED);
        pantry.setValue(pantryFor("Tomato pasta"));
        pantry.setValue(List.of());
        assertEquals(2, viewModel.pendingMatches());

        jobs.runNewestFirst(); // the stale one finishes too, and is counted off without posting

        assertEquals(0, viewModel.pendingMatches());
    }

    /**
     * The threading proof. The test's thread plays the main thread: LiveData's posts are queued for it
     * instead of running at once. Jobs go to a real thread named {@code spm-match-test}, and the
     * converter inside the real matcher records every thread it is called on, which is the thread
     * {@code matchAll} ran on. Run with {@code matchAll} moved onto the main thread, this test fails.
     */
    @Test
    public void matchAll_runsOnTheExecutorsThread_andTheScreenOnlyGetsWhatIsPosted() throws Exception {
        MainThreadIsThisThread main = new MainThreadIsThisThread();
        ArchTaskExecutor.getInstance().setDelegate(main); // the rule puts its own back afterwards
        ThreadRecordingConverter converter = new ThreadRecordingConverter();
        StrictMatcher recordingMatcher = new StrictMatcher(
                new IngredientNormaliser(AliasLoader.parse(read("/" + AliasLoader.ASSET_NAME))), converter);
        ThreadRecordingExecutor matchThread = new ThreadRecordingExecutor("spm-match-test");
        try {
            SuggestedRecipesViewModel threaded = new SuggestedRecipesViewModel(pantry, recipes,
                    new MutableLiveData<>(false), new MutableLiveData<>(UnitsSystem.METRIC), () -> recordingMatcher, () -> TODAY, matchThread);
            List<Thread> deliveredOn = new CopyOnWriteArrayList<>();
            List<UiState> seen = new CopyOnWriteArrayList<>();
            Observer<UiState> screen = state -> {
                deliveredOn.add(Thread.currentThread());
                seen.add(state);
            };
            threaded.getState().observeForever(screen);

            recipes.setValue(SEED);
            pantry.setValue(pantryFor("Tomato pasta"));

            Runnable posted = main.posted.poll(5, TimeUnit.SECONDS);
            assertNotNull("The match never posted its state", posted);
            assertEquals("The screen saw a state before the match posted one", List.of(UiState.Loading.INSTANCE), seen);
            posted.run(); // the main thread delivers the posted value

            assertTrue(seen.get(seen.size() - 1) instanceof UiState.Content);
            assertEquals(Set.of(Thread.currentThread()), Set.copyOf(deliveredOn));
            assertEquals("One job for one change", 1, matchThread.ranOn.size());
            assertFalse("The matcher was never called", converter.calledOn.isEmpty());
            assertEquals("matchAll ran on " + names(converter.calledOn) + ", not only on the executor's thread",
                    Set.of(matchThread.ranOn.get(0)), converter.calledOn);
            assertFalse("matchAll ran on the main thread", converter.calledOn.contains(Thread.currentThread()));
            threaded.getState().removeObserver(screen);
        } finally {
            matchThread.shutDown();
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private void deliver(List<PantryItem> items, List<RecipeWithIngredients> recipeList) {
        recipes.setValue(recipeList);
        pantry.setValue(items);
        jobs.runAll();
    }

    /**
     * Four of Tomato pasta's five: since Issue 27 that is {@code Content} with no suggestion and the
     * recipe almost there, not {@code Empty}; the full-screen empty state is for nothing at all.
     */
    private void assertNothingSuggested_tomatoPastaAlmostThere() {
        assertTrue("Nothing may be suggested", content().canMake().isEmpty());
        assertEquals(List.of("Tomato pasta"), names(content().almostThere()));
    }

    private EmptyReason emptyReason() {
        UiState state = viewModel.getState().getValue();
        assertTrue("Expected Empty, was " + state, state instanceof UiState.Empty);
        return ((UiState.Empty) state).reason();
    }

    private UiState.Content content() {
        UiState state = viewModel.getState().getValue();
        assertTrue("Expected Content, was " + state, state instanceof UiState.Content);
        return (UiState.Content) state;
    }

    private static List<String> names(List<MatchedRecipe> rows) {
        return rows.stream().map(r -> r.recipe().getRecipe().getName()).collect(Collectors.toList());
    }

    private static String names(Set<Thread> threads) {
        return threads.stream().map(Thread::getName).sorted().collect(Collectors.joining(", ", "[", "]"));
    }

    /** One pantry row per line of the named seed recipe, in exactly the amount it needs, no expiry. */
    private static List<PantryItem> pantryFor(String recipeName) {
        RecipeWithIngredients recipe = SEED.stream()
                .filter(r -> r.getRecipe().getName().equals(recipeName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No seed recipe " + recipeName));
        List<PantryItem> items = new ArrayList<>();
        for (RecipeIngredient line : recipe.getIngredients()) {
            items.add(new PantryItem(0, line.getName(), line.getQuantity(), line.getUnit(), null, CREATED));
        }
        return items;
    }

    private static List<PantryItem> without(List<PantryItem> items, String name) {
        return items.stream().filter(i -> !i.getName().equals(name)).collect(Collectors.toList());
    }

    private static List<PantryItem> expired(List<PantryItem> items, String name) {
        return withExpiry(items, name, TODAY.minusDays(1));
    }

    private static List<PantryItem> withExpiry(List<PantryItem> items, String name, LocalDate expiry) {
        return items.stream()
                .map(i -> i.getName().equals(name)
                        ? new PantryItem(i.getId(), i.getName(), i.getQuantity(), i.getUnit(), expiry, CREATED)
                        : i)
                .collect(Collectors.toList());
    }

    private static List<RecipeWithIngredients> seed() {
        List<RecipeWithIngredients> parsed = RecipeJsonParser.parse(read("/" + RecipeSeeder.ASSET_NAME));
        List<RecipeWithIngredients> stored = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            long id = i + 1;
            Recipe r = parsed.get(i).getRecipe();
            List<RecipeIngredient> lines = new ArrayList<>();
            for (RecipeIngredient line : parsed.get(i).getIngredients()) {
                lines.add(line.withRecipeId(id));
            }
            stored.add(new RecipeWithIngredients(new Recipe(id, r.getName(), r.getServings(), r.getSteps()), lines));
        }
        return stored;
    }

    private static String read(String resource) {
        try (InputStream in = SuggestedRecipesViewModelTest.class.getResourceAsStream(resource)) {
            assertNotNull(resource + " is not on the test classpath; see sourceSets in app/build.gradle", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    /** Runs jobs on one real, named thread and records the thread each job ran on. */
    private static final class ThreadRecordingExecutor implements Executor {

        final List<Thread> ranOn = new CopyOnWriteArrayList<>();
        private final ExecutorService thread;

        ThreadRecordingExecutor(String name) {
            thread = Executors.newSingleThreadExecutor(job -> new Thread(job, name));
        }

        @Override
        public void execute(Runnable job) {
            thread.execute(() -> {
                ranOn.add(Thread.currentThread());
                job.run();
            });
        }

        void shutDown() throws InterruptedException {
            thread.shutdown();
            assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * The real converter, recording every thread it is called on. The matcher calls it for every
     * pantry and recipe it sums, so these are the threads {@code matchAll} ran on.
     */
    private static final class ThreadRecordingConverter extends UnitConverter {

        final Set<Thread> calledOn = ConcurrentHashMap.newKeySet();

        @Override
        public Map<UnitKind, CanonicalQuantity> sumByKind(List<Quantity> quantities) {
            calledOn.add(Thread.currentThread());
            return super.sumByKind(quantities);
        }
    }

    /**
     * Makes the test's thread LiveData's main thread, and queues what is posted to it until the test
     * runs it, as Android's main looper would.
     */
    private static final class MainThreadIsThisThread extends TaskExecutor {

        final BlockingQueue<Runnable> posted = new LinkedBlockingQueue<>();
        private final Thread main = Thread.currentThread();

        @Override
        public void executeOnDiskIO(Runnable runnable) {
            runnable.run();
        }

        @Override
        public void postToMainThread(Runnable runnable) {
            posted.add(runnable);
        }

        @Override
        public boolean isMainThread() {
            return Thread.currentThread() == main;
        }
    }
}
