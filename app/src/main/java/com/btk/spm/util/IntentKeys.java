package com.btk.spm.util;

/**
 * The name of every extra an {@code Intent} in this app carries, defined once.
 *
 * <p>Screens that receive data are opened by explicit Intents (decision 3), and both sides of each
 * Intent use these constants: the {@code intentFor(...)} factory that puts the extra and the screen
 * that reads it. A typed key such as {@code putExtra("recipe_id", id)} would compile and then fail
 * silently at runtime when the two sides spell it differently, so {@code ConventionsTest} fails the
 * build on one.
 *
 * <p>Each value is prefixed with the package name, as Android recommends for extras, so it can never
 * clash with an extra another app or library adds to the same Intent.
 */
public final class IntentKeys {

    /** The prefix every extra name starts with. */
    public static final String PREFIX = "com.btk.spm.extra.";

    /** The {@code long} id of the pantry item to edit (Issue 15); absent when adding a new item. */
    public static final String EXTRA_PANTRY_ITEM_ID = PREFIX + "PANTRY_ITEM_ID";

    /** The {@code long} id of the recipe a detail screen shows (Issue 25). */
    public static final String EXTRA_RECIPE_ID = PREFIX + "RECIPE_ID";

    /** The name of the tab {@code MainActivity} opens on (Issue 3), from its {@code Tab} enum. */
    public static final String EXTRA_TAB = PREFIX + "TAB";

    private IntentKeys() {
        // Constants only; never instantiated
    }
}
