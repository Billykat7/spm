package com.btk.spm.ui.recipes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.domain.DisplayQuantity;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.UnitsSystem;
import com.btk.spm.domain.matching.Shortfall;
import com.btk.spm.domain.matching.UnitConverter;

import java.util.List;
import java.util.Objects;

/**
 * How the Recipes tab lays a {@link UiState.Content} out: the suggestions, then, under their own
 * heading, the recipes one ingredient short (brief §2.3 bonus, non-negotiable 3).
 *
 * <p>The brief allows the "Almost there" list only if it is "clearly separated from the strict
 * suggestions". The engine separates them by type ({@code MatchResults.partition}, Issue 22); this
 * class keeps them separate on the way to the screen. {@link #split} hands each list to its own
 * adapter and says which headers to show, and each adapter checks every item it is given through
 * {@link #requireCanMake} or {@link #requireAlmostThere}, so an almost-there recipe reaching the
 * suggested list throws instead of being drawn. Plain Java with no view in it, so all of it is tested
 * on the JVM; it is the only class under {@code ui/} that names a status.
 */
public final class RecipeSections {

    private static final UnitConverter DISPLAY = new UnitConverter();

    private RecipeSections() {
        // Static helpers only; never instantiated
    }

    /**
     * The parts of the tab, in screen order.
     *
     * @param zeroMatchHeader   whether the brief's zero-match sentence is the first row: nothing can be
     *                          made, but something is almost there
     * @param suggested         the recipes that can be made, for {@code RecipeAdapter}
     * @param almostThereHeader whether the "Almost there" heading and its divider are shown: only when
     *                          there is a recipe under them
     * @param almostThere       the recipes one ingredient short, for {@code AlmostThereAdapter}
     */
    public record Sections(boolean zeroMatchHeader, @NonNull List<MatchedRecipe> suggested,
                           boolean almostThereHeader, @NonNull List<MatchedRecipe> almostThere) {
    }

    /**
     * One almost-there row's line: what is missing or short, and how much there is.
     *
     * @param name what the recipe calls the ingredient
     * @param need how much the recipe needs, in display units
     * @param have how much the pantry holds in the same kind, or {@code null} when it holds none
     */
    public record MissingLine(@NonNull String name, @NonNull DisplayQuantity need, @Nullable DisplayQuantity have) {
    }

    /**
     * Splits a state into the tab's sections, in {@code partition}'s order within each.
     *
     * @param content the state to lay out
     * @return the sections; a header is shown only when there is something under it
     * @throws IllegalArgumentException if a list holds a recipe of the other status
     */
    @NonNull
    public static Sections split(@NonNull UiState.Content content) {
        List<MatchedRecipe> suggested = requireCanMake(content.canMake());
        List<MatchedRecipe> almostThere = requireAlmostThere(content.almostThere());
        return new Sections(suggested.isEmpty() && !almostThere.isEmpty(), suggested,
                !almostThere.isEmpty(), almostThere);
    }

    /**
     * Returns {@code rows} if every one can be made; the suggested list's guard.
     *
     * @param rows the rows about to be shown as suggestions, or {@code null} to clear the list
     * @return {@code rows}, unchanged
     * @throws IllegalArgumentException naming the first recipe that is not {@code CAN_MAKE}
     */
    @Nullable
    public static List<MatchedRecipe> requireCanMake(@Nullable List<MatchedRecipe> rows) {
        return requireStatus(rows, MatchStatus.CAN_MAKE);
    }

    /**
     * Returns {@code rows} if every one is exactly one ingredient short; the "Almost there" list's
     * guard.
     *
     * @param rows the rows about to be shown under "Almost there", or {@code null} to clear the list
     * @return {@code rows}, unchanged
     * @throws IllegalArgumentException naming the first recipe that is not {@code ALMOST_THERE}
     */
    @Nullable
    public static List<MatchedRecipe> requireAlmostThere(@Nullable List<MatchedRecipe> rows) {
        return requireStatus(rows, MatchStatus.ALMOST_THERE);
    }

    /**
     * Returns the one thing an almost-there recipe lacks, from {@code MatchResult.missingOne()}, with
     * both amounts in display units (metric until the units preference, Issue 28).
     *
     * @param row an almost-there row
     * @return the missing or short ingredient; {@link MissingLine#have()} is {@code null} when it is missing
     * @throws IllegalArgumentException if the row is not {@code ALMOST_THERE}
     */
    @NonNull
    public static MissingLine missingLine(@NonNull MatchedRecipe row) {
        Shortfall shortfall = row.result().missingOne().orElseThrow(() -> new IllegalArgumentException(
                "Recipe " + row.recipeId() + " is " + row.result().status() + ", not one ingredient short"));
        return new MissingLine(shortfall.required().name(),
                DISPLAY.toPreferredDisplay(shortfall.required().quantity(), UnitsSystem.METRIC),
                shortfall.isMissing() ? null : DISPLAY.toPreferredDisplay(shortfall.available(), UnitsSystem.METRIC));
    }

    private static List<MatchedRecipe> requireStatus(@Nullable List<MatchedRecipe> rows, @NonNull MatchStatus status) {
        if (rows != null) {
            for (MatchedRecipe row : rows) {
                MatchStatus actual = Objects.requireNonNull(row, "row").result().status();
                if (actual != status) {
                    throw new IllegalArgumentException(row.recipe().getRecipe().getName() + " (recipe "
                            + row.recipeId() + ") is " + actual + " and cannot be shown as " + status);
                }
            }
        }
        return rows;
    }
}
