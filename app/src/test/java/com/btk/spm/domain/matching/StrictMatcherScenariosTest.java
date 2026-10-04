package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The strict-matching rule against every scenario in {@code scenarios.csv}, one test per row, named
 * by the row (Issue 21). The rows were written from brief §2.3 and the milestone's exit criteria,
 * not from {@code StrictMatcherTest}: a second, independent look at the same rule.
 *
 * <p>The file's header explains its columns. The alias table here holds only what the alias rows
 * need, so a row proves the matcher's use of aliases rather than the contents of
 * {@code aliases.json} ({@code SeedRecipesMatchTest} runs the real one).
 */
@RunWith(Parameterized.class)
public class StrictMatcherScenariosTest {

    /** The fixed day every relative expiry in the file is counted from. */
    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private static final StrictMatcher MATCHER = new StrictMatcher(
            new IngredientNormaliser(Map.of("cilantro", "coriander", "scallion", "spring onion")),
            new UnitConverter());

    @Parameter(0) public String name;
    @Parameter(1) public List<PantryEntry> pantry;
    @Parameter(2) public RecipeSpec recipe;
    @Parameter(3) public boolean includeExpired;
    @Parameter(4) public MatchStatus expectedStatus;
    @Parameter(5) public List<String> expectedShortfalls;

    @Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException {
        List<Object[]> rows = new ArrayList<>();
        try (InputStream in = StrictMatcherScenariosTest.class.getResourceAsStream("/scenarios.csv")) {
            assertNotNull("scenarios.csv is not on the test classpath", in);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean header = true;
            int lineNumber = 0;
            for (String line; (line = reader.readLine()) != null; ) {
                lineNumber++;
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                rows.add(row(line, lineNumber));
            }
        }
        return rows;
    }

    @Test
    public void scenario() {
        MatchResult result = MATCHER.match(pantry, recipe, MatchOptions.on(TODAY, includeExpired));

        List<String> shortfalls = new ArrayList<>();
        for (Shortfall shortfall : result.shortfalls()) {
            shortfalls.add(shortfall.required().name());
        }
        assertEquals(name + ": status", expectedStatus, result.status());
        assertEquals(name + ": shortfalls", expectedShortfalls, shortfalls);
        assertEquals(name + ": covered", recipe.ingredients().size() - shortfalls.size(), result.haveCount());
    }

    private static Object[] row(String line, int lineNumber) {
        String[] cells = line.split(",", -1);
        if (cells.length != 6) {
            throw new IllegalArgumentException("scenarios.csv line " + lineNumber + " has " + cells.length
                    + " columns, not 6: " + line);
        }
        List<PantryEntry> pantry = new ArrayList<>();
        for (String item : items(cells[1])) {
            String[] quantityAndExpiry = item.substring(item.indexOf(':') + 1).split("@", -1);
            LocalDate expiry = quantityAndExpiry.length == 1 ? null
                    : TODAY.plusDays(Long.parseLong(quantityAndExpiry[1].trim().replace("+", "")));
            pantry.add(new PantryEntry(nameOf(item), quantity(quantityAndExpiry[0]), expiry));
        }
        List<RequiredIngredient> lines = new ArrayList<>();
        for (String item : items(cells[2])) {
            lines.add(new RequiredIngredient(nameOf(item), quantity(item.substring(item.indexOf(':') + 1))));
        }
        List<String> shortfalls = items(cells[5]);
        return new Object[]{cells[0], Collections.unmodifiableList(pantry), new RecipeSpec(lineNumber, lines),
                Boolean.parseBoolean(cells[3].trim()), MatchStatus.valueOf(cells[4].trim()), shortfalls};
    }

    /** The ";"-separated items of a cell, kept exactly as written; none for an empty cell. */
    private static List<String> items(String cell) {
        return cell.isEmpty() ? Collections.emptyList() : Arrays.asList(cell.split(";"));
    }

    /** Everything before the ":", untrimmed, so the spacing rows reach the matcher as written. */
    private static String nameOf(String item) {
        return item.substring(0, item.indexOf(':'));
    }

    /** {@code "250 g"} to a {@link Quantity}. */
    private static Quantity quantity(String text) {
        String[] amountAndUnit = text.trim().split(" ");
        return new Quantity(Double.parseDouble(amountAndUnit[0]), Unit.fromSymbol(amountAndUnit[1]));
    }
}
