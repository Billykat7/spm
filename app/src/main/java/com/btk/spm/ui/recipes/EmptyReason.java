package com.btk.spm.ui.recipes;

import androidx.annotation.StringRes;

import com.btk.spm.R;

/**
 * Why the Recipes tab has nothing to suggest. The brief (§2.2) asks for feedback when zero recipes
 * match "rather than a blank or broken screen", and there are three different kinds of nothing, each
 * with its own message and its own way out.
 *
 * <p>The ViewModel picks the reason only after the matcher has said no recipe can be made; the reason
 * changes the words on screen, never the answer.
 */
public enum EmptyReason {

    /**
     * The recipe table is empty, so the first-run seed did not load (Issue 11). Checked first: adding
     * ingredients would not help, so there is no button, only what to do instead.
     */
    NO_RECIPES(R.string.recipes_empty_no_recipes, false),

    /** Nothing is in the pantry yet, as on a first launch. The button goes to the Pantry tab. */
    PANTRY_EMPTY(R.string.recipes_empty_pantry_empty, true),

    /**
     * The pantry has items but no recipe has every ingredient in enough quantity: the case the brief
     * tests with four of five ingredients. The button goes to the Pantry tab.
     */
    NO_MATCH(R.string.recipes_empty_no_match, true);

    @StringRes
    private final int messageRes;
    private final boolean offersAddIngredients;

    EmptyReason(@StringRes int messageRes, boolean offersAddIngredients) {
        this.messageRes = messageRes;
        this.offersAddIngredients = offersAddIngredients;
    }

    /**
     * Returns what the empty state says for this reason.
     *
     * @return a {@code R.string.recipes_empty_*} id
     */
    @StringRes
    public int messageRes() {
        return messageRes;
    }

    /**
     * Says whether the empty state shows the "Add ingredients" button, which only helps when more
     * ingredients could make a recipe possible.
     *
     * @return {@code true} for {@link #PANTRY_EMPTY} and {@link #NO_MATCH}
     */
    public boolean offersAddIngredients() {
        return offersAddIngredients;
    }

    /**
     * Picks the reason for a match that suggested nothing, in the order that tells the truth: no
     * recipes at all comes before an empty pantry, so a missing seed is never reported as "add
     * ingredients".
     *
     * @param noRecipes   whether the recipe list was empty
     * @param pantryEmpty whether the pantry was empty
     * @return the reason to show
     */
    public static EmptyReason of(boolean noRecipes, boolean pantryEmpty) {
        if (noRecipes) {
            return NO_RECIPES;
        }
        return pantryEmpty ? PANTRY_EMPTY : NO_MATCH;
    }
}
