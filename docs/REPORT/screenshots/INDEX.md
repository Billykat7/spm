# Screenshots index

Real screenshots of the running app, taken on the emulator, one per screen and core function (brief
section 6). `NN_<slug>.png`, two-digit order; `_dark` for the dark-theme variants. Issue 33 re-takes
the final set after the UI pass, so earlier rows are working copies.

| NN | File | Caption | Issue |
|----|------|---------|-------|
| 01 | 01_scaffold_main_activity_api35.png | The scaffolded app's single screen, `MainActivity` showing the app name, on the API 35 (Android 15) emulator | #1 |
| 02 | 02_scaffold_main_activity_api26.png | The same screen on the API 26 (Android 8.0) emulator, the minimum SDK | #1 |
| 03 | 03_scaffold_project_tree_android_studio.png | The project in Android Studio's *Android* view: under `com.btk.spm` the six top-level packages (`data`, `domain`, `notifications`, `settings`, `ui`, `util`) and `SpmApplication`, with the `androidTest` and `test` source sets below | #1 |
| 04 | 04_theme_placeholder.png | The placeholder screen in `Theme.Spm`'s light scheme on API 35: the app name in the leaf-green primary role and the tagline in the on-surface-variant role, both sized by `TextAppearance.Spm.*` | #2 |
| 04 | 04_theme_placeholder_dark.png | The same screen after switching the emulator to dark mode with the app open: the same theme, re-coloured from `values-night/colors.xml`, with no restart | #2 |
| 05 | 05_launcher_icon.png | The app's own adaptive launcher icon in the API 35 app drawer, a jar with an amber lid and a leaf on the brand green, beside the system icons | #2 |
| 06 | 06_tab_pantry.png | The navigation shell on API 35: the Pantry tab selected in the bottom navigation (filled icon on the amber indicator), its title in the toolbar and its placeholder Fragment | #3 |
| 06 | 06_tab_pantry_dark.png | The Pantry tab in the dark theme | #3 |
| 07 | 07_tab_recipes.png | The Recipes tab: the toolbar title and the Fragment swapped by the bottom navigation | #3 |
| 07 | 07_tab_recipes_dark.png | The Recipes tab in the dark theme | #3 |
| 08 | 08_tab_settings.png | The Settings tab, the third top-level screen | #3 |
| 08 | 08_tab_settings_dark.png | The Settings tab in the dark theme | #3 |
| 09 | 09_pantry_list.png | The Pantry tab on API 35 with ten items read live from Room through `PantryViewModel` and shown by `PantryAdapter`: sorted by name ignoring case, each amount formatted by `QuantityFormatter` with its unit symbol (`2 kg`, `1.5 kg`, `6 pcs`, `1 l`, never `2.0 KG`), a 48 dp overflow button on every row and the add FAB | #13 |
| 09 | 09_pantry_list_dark.png | The same list in the dark theme | #13 |
| 10 | 10_pantry_empty.png | The Pantry tab on a fresh install: the shared `view_empty_state` (the empty-jar illustration, "Your pantry is empty" and an "Add ingredient" button) in place of the hidden list | #13 |
| 10 | 10_pantry_empty_dark.png | The empty state in the dark theme: the illustration's colours are theme roles, so it re-colours with the screen | #13 |
| 11 | 11_add_ingredient_form.png | The add ingredient form on API 35, opened from the Pantry FAB by an explicit Intent: name with its 0/60 counter, quantity, the unit dropdown and the optional expiry date with its calendar icon, Save in the toolbar and the up arrow to cancel | #14 |
| 11 | 11_add_ingredient_form_dark.png | The same form in the dark theme | #14 |
| 12 | 12_add_ingredient_errors.png | Save tapped on the empty form: "Name is required.", "Enter a quantity." and "Choose a unit." shown at once under their fields, the cursor in the name field, and no row written | #14 |
| 12 | 12_add_ingredient_errors_dark.png | The same three errors in the dark theme | #14 |
| 13 | 13_add_ingredient_expiry_error.png | Plain flour, 1,5 kg, with yesterday chosen in the date picker: "Expiry date cannot be in the past." under the date, whose clear icon stays visible beside the error | #14 |
| 14 | 14_delete_confirm.png | The overflow's Delete on "tomatoes": a Material dialog naming the item, "Delete tomatoes?", with Cancel and Delete; nothing is removed until Delete is tapped | #16 |
| 14 | 14_delete_confirm_dark.png | The same confirmation in the dark theme | #16 |
| 15 | 15_delete_undo.png | Delete confirmed: the row gone from the list with no refresh, and "Deleted tomatoes" with Undo in a Snackbar anchored above the FAB, not over it | #16 |
| 15 | 15_delete_undo_dark.png | The same Snackbar in the dark theme | #16 |
| 16 | 16_delete_last_undo.png | The last item deleted: the empty state in place of the list, with "Deleted Plain flour" and Undo still on offer, which brings the row back with its id | #16 |
| 17 | 17_expiry_badges.png | Five items in the default order, soonest expiry first: "Expired 1 day ago" on the error colours, "Expires today" and "Expires in 2 days" on the tertiary container, "Expires in 10 days" neutral, and no badge for the undated rice, last; each badge from `ExpiryRules` with the 3-day threshold | #17 |
| 17 | 17_expiry_badges_dark.png | The same badges in the dark theme: every colour is a theme role, so each pair stays legible | #17 |
| 18 | 18_sort_menu.png | The sort action in the Pantry toolbar opened: "Expiry (soonest first)" checked, the default, and "Name (A–Z)" | #17 |
| 18 | 18_sort_menu_dark.png | The sort menu in the dark theme | #17 |
| 19 | 19_edit_ingredient_prefilled.png | The overflow's Edit on "Plain flour": the same form, titled "Edit ingredient", opened by an explicit Intent carrying the row's id in `IntentKeys.EXTRA_PANTRY_ITEM_ID` and prefilled from the database (1.5, kg, Oct 24, 2026); Save updates that row with the same id | #15 |
| 19 | 19_edit_ingredient_prefilled_dark.png | The prefilled edit form in the dark theme | #15 |
| 20 | 20_suggested_recipes.png | The Recipes tab over a nine-item pantry: Garlic bread, Grilled cheese sandwich and Tomato pasta, each with its servings and "You have all N ingredients", and "Suggested recipes (3)" in the toolbar. Cheese omelette, two eggs of the three it needs, is not listed: the list is `MatchResults.partition(...).canMake` and nothing else | #23 |
| 20 | 20_suggested_recipes_dark.png | The same three suggestions in the dark theme | #23 |
| 21 | 21_recipes_no_match.png | The brief's zero-match case (2.2): a pantry of pasta 500 g, tomatoes 6, olive oil 0.5 l and salt 1 kg, four of Tomato pasta's five ingredients with the garlic missing. The Recipes tab says "No recipes match your pantry yet, add more ingredients" with an Add ingredients button to the Pantry tab, and "Suggested recipes (0)" | #24 |
| 21 | 21_recipes_no_match_dark.png | The same zero-match state in the dark theme | #24 |
| 22 | 22_recipes_pantry_empty.png | A fresh install, nothing in the pantry: "Your pantry is empty, add some ingredients to see what you can cook" and the same button, which switches the running host to the Pantry tab | #24 |
| 22 | 22_recipes_pantry_empty_dark.png | The empty-pantry state in the dark theme | #24 |
| 23 | 23_recipe_detail.png | Tomato pasta opened by an explicit Intent carrying `IntentKeys.EXTRA_RECIPE_ID`, against a pantry with one garlic clove: "Missing 1 ingredient", a check on pasta ("need 200 g, have 500 g"), tomato, olive oil ("need 2 tbsp, have 500 ml") and salt ("need 2 g, have 1 kg"), a cross on garlic ("need 2 pcs, have 1 pcs"), then the numbered method. Every mark is the matcher's `MatchResult.checks()` | #25 |
| 23 | 23_recipe_detail_dark.png | The same detail screen in the dark theme: the check and the cross take colorPrimary and colorError | #25 |
