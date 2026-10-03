package com.btk.spm.ui;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.btk.spm.R;
import com.btk.spm.ui.pantry.PantryFragment;
import com.btk.spm.ui.recipes.SuggestedRecipesFragment;
import com.btk.spm.ui.settings.SettingsFragment;

/**
 * The three top-level screens of the bottom navigation, in the order it shows them (decision 3).
 *
 * <p>Each tab knows its menu item, its toolbar title and how to create its Fragment, so
 * {@link MainActivity} handles every tab the same way and a fourth tab would be one more constant
 * here plus a menu item. A tab travels between screens and across rotation by its {@link #name()}.
 */
public enum Tab {

    /** The pantry list (Issue 13); the first tab, where the app opens and where Back returns to. */
    PANTRY(R.id.nav_pantry, R.string.tab_pantry),

    /** The recipes the pantry can make right now (Issue 23). */
    RECIPES(R.id.nav_recipes, R.string.tab_recipes),

    /** Alerts, units and matching options (Issue 28). */
    SETTINGS(R.id.nav_settings, R.string.tab_settings);

    @IdRes
    private final int menuItemId;
    @StringRes
    private final int titleRes;

    Tab(@IdRes int menuItemId, @StringRes int titleRes) {
        this.menuItemId = menuItemId;
        this.titleRes = titleRes;
    }

    /**
     * Returns the id of this tab's item in {@code res/menu/bottom_nav.xml}.
     *
     * @return a {@code R.id.nav_*} id
     */
    @IdRes
    public int menuItemId() {
        return menuItemId;
    }

    /**
     * Returns the string shown in the toolbar while this tab is selected, the same as its label in
     * the bottom navigation.
     *
     * @return a {@code R.string.tab_*} id
     */
    @StringRes
    public int titleRes() {
        return titleRes;
    }

    /**
     * Creates a new instance of this tab's Fragment. Called only when the tab is shown and its
     * Fragment is not already in the {@code FragmentManager}.
     *
     * @return a new, unattached Fragment
     */
    @NonNull
    public Fragment newFragment() {
        return switch (this) {
            case PANTRY -> new PantryFragment();
            case RECIPES -> new SuggestedRecipesFragment();
            case SETTINGS -> new SettingsFragment();
        };
    }

    /**
     * Finds the tab a bottom navigation item stands for.
     *
     * @param menuItemId the id of the selected menu item
     * @return its tab
     * @throws IllegalArgumentException if no tab has that menu item, which means the menu and this
     *     enum have gone out of step
     */
    @NonNull
    public static Tab fromMenuItemId(@IdRes int menuItemId) {
        for (Tab tab : values()) {
            if (tab.menuItemId == menuItemId) {
                return tab;
            }
        }
        throw new IllegalArgumentException("No Tab for menu item 0x" + Integer.toHexString(menuItemId));
    }

    /**
     * Reads a tab back from its {@link #name()}, as stored in an Intent extra or in saved state.
     * Anything missing or unknown gives {@code fallback} rather than an error, because an Intent can
     * come from outside the app and a stale value must not crash it.
     *
     * @param name     a tab name such as {@code "RECIPES"}, or {@code null}
     * @param fallback the tab to use when {@code name} names no tab
     * @return the named tab, or {@code fallback}
     */
    @NonNull
    public static Tab fromName(@Nullable String name, @NonNull Tab fallback) {
        if (name != null) {
            for (Tab tab : values()) {
                if (tab.name().equals(name)) {
                    return tab;
                }
            }
        }
        return fallback;
    }
}
