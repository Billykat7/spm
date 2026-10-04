# Screenshots index

Real screenshots of the running app, taken on the emulator, one per screen and core function (brief
section 6). `NN_<slug>.png`, two-digit order; `_dark` for the dark-theme variants. Issue 33 re-takes
the final set after the UI pass, so earlier rows are working copies.

## Report set

These 16 are the report's screenshots, in the order the written report (#35) embeds them, chosen from
the working copies below. Each was taken on the API 35 emulator from the debug build of the issue
named in its row (builds from Issue 17 to Issue 31), in the light theme with the demo-mode status bar.
The full re-take on one build was cut from scope.

| NN | File | Caption | Issue |
|----|------|---------|-------|
| 01 | 33_ux_pantry_list_light.png | The Pantry tab with ten items, soonest expiry first, each with its amount and the expiry badge under it, "Expired 1 day ago" on the error colours | #30 |
| 02 | 33_ux_add_form_light.png | The empty add form: name with its 0/60 counter, quantity, the unit dropdown and the optional expiry date, Save in the toolbar | #30 |
| 03 | 34_validation_all_errors.png | Save on an empty name, a quantity of 0 and yesterday's date: all three errors from `Validators` at once, and nothing written | #31 |
| 04 | 33_ux_edit_form_light.png | The overflow's Edit on "Plain flour": the same form, titled "Edit ingredient", prefilled from the database (1.5, kg, Nov 3, 2026) | #30 |
| 05 | 33_ux_delete_dialog_light.png | The overflow's Delete on "milk": a DialogFragment, "Delete milk?", with Cancel and Delete; nothing is removed until Delete is tapped | #30 |
| 06 | 33_ux_undo_snackbar_light.png | Delete confirmed: the row gone from the list and "Deleted milk" with Undo in a Snackbar above the FAB | #30 |
| 07 | 17_expiry_badges.png | Expiry badges from `ExpiryRules` with the 3-day threshold: expired on the error colours, expiring soon on the tertiary container, later neutral, none for an undated item | #17 |
| 08 | 24_match_four_ingredients.png | Four of Tomato pasta's five ingredients in the pantry, exactly as the seed needs them: "No recipes match your pantry yet, add more ingredients", (0) | #26 |
| 09 | 24_match_five_ingredients.png | The fifth, Garlic, 2 pcs, added: Tomato pasta is listed, "You have all 5 ingredients", "Suggested recipes (1)", with no refresh | #26 |
| 10 | 33_ux_recipes_no_match_light.png | The zero-match sentence, "Suggested recipes (0)", over the almost-there cards for Grilled cheese sandwich and Tomato pasta | #30 |
| 11 | 25_almost_there.png | The bonus "Almost there (missing one ingredient)" section under the one suggestion, each card naming what is short or missing, none of them counted | #27 |
| 12 | 33_ux_recipe_detail_light.png | Tomato pasta in full: "You can make this", a check and "need, have" on each of the five ingredients from the matcher, then the numbered method | #30 |
| 13 | 33_ux_settings_light.png | The Settings tab: Alerts (the expiring-soon switch, the 3-day threshold, the test alert), Display (Units: Metric), Matching (Count expired items: off) and About | #30 |
| 14 | 28_settings_count_expired.png | Count expired items turned on: the garlic expired yesterday counts again, so Tomato pasta becomes a suggestion, "Suggested recipes (1)" | #28 |
| 15 | 29_permission_prompt.png | Android 13+ only: turning on expiring-soon alerts without `POST_NOTIFICATIONS` shows the system prompt | #29 |
| 16 | 32_expiry_notification.png | "Send a test alert now": one notification, "2 items expiring soon", "tomato (tomorrow), milk (in 3 days)"; tapping it opens the Pantry tab | #29 |

## Working copies

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
| 24 | 24_match_four_ingredients.png | The video's match proof, before (docs/DEMO/MATCH_PROOF_STEPS.md): pasta 200 g, tomatoes 4, olive oil 2 tbsp and salt 2 g, four of Tomato pasta's five ingredients exactly as the seed needs them, and the Recipes tab says "No recipes match your pantry yet, add more ingredients", (0) | #26 |
| 24 | 24_match_five_ingredients.png | After: the fifth ingredient, Garlic, 2 pcs, in the pantry, and Tomato pasta is in the list, "You have all 5 ingredients", "Suggested recipes (1)"; no refresh action exists or was needed | #26 |
| 25 | 25_almost_there.png | The bonus "Almost there" section, apart from the suggestions (brief 2.3). Pantry: pasta 500 g, tomatoes 6, garlic 6, olive oil 0.5 l, salt 1 kg, bread 6 and butter 40 g. Tomato pasta is the one suggestion, "Suggested recipes (1)"; under a divider and the heading "Almost there (missing one ingredient)", outlined cards on the surface-variant colour: Garlic bread "Short: need 50 g butter, have 40 g", Garlic butter mushrooms "Missing: 250 g mushroom", Grilled cheese sandwich "Missing: 50 g cheese". None of them is counted | #27 |
| 25 | 25_almost_there_dark.png | Both sections in the dark theme | #27 |
| 26 | 26_settings.png | The Settings tab, the fifth screen (brief 2.2, 3.1): a `PreferenceFragmentCompat` over `preferences.xml`, every key a `PrefKey`. Alerts (the expiring-soon switch, the 1–14 day threshold whose summary says "Items expiring within 3 days are badged and reported", the test alert still disabled), Display (Units: Metric), Matching (Count expired items: off, with what that means) and About (the version name from `BuildConfig`) | #28 |
| 26 | 26_settings_dark.png | The Settings tab in the dark theme, with the Material 3 switches | #28 |
| 27 | 27_settings_pantry_imperial.png | The Pantry tab after Units: Imperial and a threshold of 7 on the Settings tab: Plain flour, stored as 1500 g, reads "52.9 oz" and its "Expires in 5 days" badge has turned to the expiring-soon colour; olive oil 0.5 l reads "16.9 fl oz"; nothing stored has changed | #28 |
| 28 | 28_settings_count_expired.png | Count expired items turned on: the garlic expired yesterday counts again, so Tomato pasta, almost there a moment before, is a suggestion, "Suggested recipes (1)"; turned off, it is back to (0) | #28 |
| 29 | 29_permission_prompt.png | Android 13+ only: turning *Expiring-soon alerts* on with `POST_NOTIFICATIONS` not granted shows the system prompt, launched through `ActivityResultContracts.RequestPermission`. Behind it, the switch's summary already says the app may not post and the *Notification settings* shortcut is shown | #29 |
| 30 | 30_alerts_denied.png | After "Don't allow": the switch is back off, its summary says "Off: notifications are not allowed for this app.", the daily check is cancelled, and *Notification settings* opens the app's page in system settings | #29 |
| 31 | 31_permission_rationale.png | Turning the alert on again after a refusal: the app's own explanation first, "Allow notifications?", with Continue (to the system prompt) and Not now | #29 |
| 32 | 32_expiry_notification.png | "Send a test alert now" with tomato expiring tomorrow and milk in three days: one notification on the `expiry_alerts` channel, "2 items expiring soon", "tomato (tomorrow), milk (in 3 days)", worded by `ExpiryMessageBuilder`; tapping it opens the Pantry tab | #29 |
| 33 | 33_ux_add_form_dark.png | The empty add form, dark theme | #30 |
| 33 | 33_ux_add_form_errors_dark.png | Save on the empty form: three errors at once, dark theme | #30 |
| 33 | 33_ux_add_form_errors_fontscale13.png | Save on the empty form: three errors at once, at font scale 1.3 | #30 |
| 33 | 33_ux_add_form_errors_fontscale2.png | Save on the empty form: three errors at once, at font scale 2.0 | #30 |
| 33 | 33_ux_add_form_errors_light.png | Save on the empty form: three errors at once | #30 |
| 33 | 33_ux_add_form_fontscale2.png | The empty add form, at font scale 2.0 | #30 |
| 33 | 33_ux_add_form_light.png | The empty add form | #30 |
| 33 | 33_ux_add_form_rotated_land.png | The add form half filled (Basil, 2, pcs, Oct 10) after a rotation to landscape: every value kept, the form scrolls | #30 |
| 33 | 33_ux_delete_dialog_dark.png | The delete confirmation, now a DialogFragment, dark theme | #30 |
| 33 | 33_ux_delete_dialog_fontscale2.png | The delete confirmation, now a DialogFragment, at font scale 2.0 | #30 |
| 33 | 33_ux_delete_dialog_light.png | The delete confirmation, now a DialogFragment | #30 |
| 33 | 33_ux_delete_dialog_rotated_land.png | The delete confirmation still open after a rotation | #30 |
| 33 | 33_ux_edit_form_dark.png | The edit form, prefilled, dark theme | #30 |
| 33 | 33_ux_edit_form_fontscale2.png | The edit form, prefilled, at font scale 2.0 | #30 |
| 33 | 33_ux_edit_form_light.png | The edit form, prefilled | #30 |
| 33 | 33_ux_pantry_empty_dark.png | The empty pantry in the shared state layout: illustration, headline and the Add ingredient button, dark theme | #30 |
| 33 | 33_ux_pantry_empty_fontscale2.png | The empty pantry in the shared state layout: illustration, headline and the Add ingredient button, at font scale 2.0 | #30 |
| 33 | 33_ux_pantry_empty_land.png | The empty pantry in landscape | #30 |
| 33 | 33_ux_pantry_empty_light.png | The empty pantry in the shared state layout: illustration, headline and the Add ingredient button | #30 |
| 33 | 33_ux_pantry_list_dark.png | The Pantry tab with ten items: the badge under each amount since this pass, "Expired 1 day ago" on the error colours, dark theme | #30 |
| 33 | 33_ux_pantry_list_fontscale13.png | The Pantry tab with ten items: the badge under each amount since this pass, "Expired 1 day ago" on the error colours, at font scale 1.3 | #30 |
| 33 | 33_ux_pantry_list_fontscale2.png | The Pantry tab with ten items: the badge under each amount since this pass, "Expired 1 day ago" on the error colours, at font scale 2.0 | #30 |
| 33 | 33_ux_pantry_list_land.png | The Pantry tab in landscape | #30 |
| 33 | 33_ux_pantry_list_light.png | The Pantry tab with ten items: the badge under each amount since this pass, "Expired 1 day ago" on the error colours | #30 |
| 33 | 33_ux_recipe_detail_dark.png | Tomato pasta in full, every mark from the matcher, dark theme | #30 |
| 33 | 33_ux_recipe_detail_fontscale2.png | Tomato pasta in full, every mark from the matcher, at font scale 2.0 | #30 |
| 33 | 33_ux_recipe_detail_land.png | The detail screen in landscape | #30 |
| 33 | 33_ux_recipe_detail_light.png | Tomato pasta in full, every mark from the matcher | #30 |
| 33 | 33_ux_recipes_dark.png | One suggestion and three almost-there cards, dark theme | #30 |
| 33 | 33_ux_recipes_fontscale2.png | One suggestion and three almost-there cards, at font scale 2.0 | #30 |
| 33 | 33_ux_recipes_light.png | One suggestion and three almost-there cards | #30 |
| 33 | 33_ux_recipes_no_match_dark.png | The zero-match sentence over the almost-there section, dark theme | #30 |
| 33 | 33_ux_recipes_no_match_fontscale2.png | The zero-match sentence over the almost-there section, at font scale 2.0 | #30 |
| 33 | 33_ux_recipes_no_match_light.png | The zero-match sentence over the almost-there section | #30 |
| 33 | 33_ux_recipes_scrolled_land.png | The Recipes tab in landscape, scrolled to the almost-there cards | #30 |
| 33 | 33_ux_recipes_scrolled_land_recreated.png | The same tab after the Activity was re-created (theme change): still scrolled to Garlic bread | #30 |
| 33 | 33_ux_settings_dark.png | The Settings tab, dark theme | #30 |
| 33 | 33_ux_settings_fontscale2.png | The Settings tab, at font scale 2.0 | #30 |
| 33 | 33_ux_settings_land.png | The Settings tab in landscape | #30 |
| 33 | 33_ux_settings_light.png | The Settings tab | #30 |
| 33 | 33_ux_settings_scroll_recreated.png | Settings scrolled, before (light) and after (dark) the Activity was re-created: the same position | #30 |
| 33 | 33_ux_talkback_form.png | TalkBack on the edit form: "Navigate up. Button" | #30 |
| 33 | 33_ux_talkback_pantry.png | TalkBack on, with its speech output captioned: "Sort the pantry. Button. Sort by" | #30 |
| 33 | 33_ux_undo_snackbar_dark.png | "Deleted milk" with Undo, above the FAB, dark theme | #30 |
| 33 | 33_ux_undo_snackbar_fontscale2.png | "Deleted milk" with Undo, above the FAB, at font scale 2.0 | #30 |
| 33 | 33_ux_undo_snackbar_light.png | "Deleted milk" with Undo, above the FAB | #30 |
| 34 | 34_validation_all_errors.png | The validation error the brief asks for (5.1): Save on an empty name, a quantity of 0 and yesterday's date shows all three errors at once, "Name is required.", "Quantity must be more than 0." and "Expiry date cannot be in the past.", each from `Validators`, with Save still enabled and nothing written | #31 |
| 34 | 34_validation_all_errors_dark.png | The same three errors in the dark theme | #31 |
| 34 | 34_validation_name_fixed.png | One letter typed in the name: its error is gone, the other two stay | #31 |
| 34 | 34_validation_comma_in_english.png | "1,5" under English: "Enter a number, such as 4 or 1.5."; under German the same text saves 1.5 kg | #31 |
| 34 | 34_recipe_not_found.png | The detail screen started with an id no recipe has (`--el com.btk.spm.extra.RECIPE_ID 999999`): the shared error state, "Recipe not found", with a working up arrow, instead of a crash | #31 |
