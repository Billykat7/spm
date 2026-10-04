# UX checklist (Issue 30)

One pass over every screen and state of the app with one checklist, before the report's screenshots
are taken (Issue 33 chooses and captions its set from these). Each cell names the screenshot under
[`screenshots/`](screenshots/) that shows the check, or the test that makes it. Taken on the API 35
emulator (`spm_api35`, 1080 x 2400, demo mode) from the debug build, on 2026-10-04.

## The checks

| Check | How it was made |
|---|---|
| **Consistency** | `grep -rn 'android:textSize\|android:padding="[0-9]\|android:layout_margin="[0-9]\|#[0-9A-Fa-f]\{6\}' app/src/main/res/layout` prints nothing; `LayoutStyleConventionsTest` fails on a raw hex colour or a `textSize` in any layout. Every spacing is a `@dimen`, every text a `TextAppearance.Spm.*`, every colour a theme role. Toolbar titles come from resources (`Tab.titleRes()`, the two Activities' manifest labels and `title_edit_ingredient`); both Activities have the up arrow through `parentActivityName` and `onSupportNavigateUp`. Loading, empty and error share `view_state_message.xml`, filled by `ui/StateMessage`. |
| **Dark** | Every screen and state screenshotted in the dark theme. No fixed `@color/white` or `@android:color/black` in a layout; badges, chips and cards take colour roles, so each pair stays legible. |
| **Labels** | Lint's `ContentDescription` is an error (`app/build.gradle`), shown failing on a scratch `ImageView` and reverted. Every `ImageView`, `ImageButton` and FAB has a description from `strings.xml` or `importantForAccessibility="no"`; each pantry row has one spoken description ("tomato, 4 pieces, Expires in 3 days"). `AccessibilityChecksTest` runs the Accessibility Test Framework on every screen below. |
| **Targets** | `@dimen/touch_target_min` (48 dp) on every `ImageButton` (`LayoutStyleConventionsTest`) and on the state layout's button; the framework's `TouchTargetSizeCheck` runs in `AccessibilityChecksTest`. |
| **Font 2.0** | `adb shell settings put system font_scale 2.0`, every screen opened; 1.3 checked on the pantry and the form too. Reset to 1.0 afterwards. |
| **Rotation** | Rotated with state on screen; the Activity is re-created (`adb logcat -s Lifecycle`: `onDestroy (configuration change)`, `onCreate (restored)`). |
| **TalkBack** | The pantry list and the form, see *TalkBack* below. |
| **No string in Java** | `grep -rn 'setText("\|setTitle("\|setError("\|makeText(.*"' app/src/main/java` prints nothing; `HardcodedText` and `SetTextI18n` are Lint errors. |

## Every screen and state

| Screen or state | Consistency | Dark | Labels | Targets | Font 2.0 | Rotation | TalkBack | No string in Java |
|---|---|---|---|---|---|---|---|---|
| Pantry list | ✅ `33_ux_pantry_list_light.png` | ✅ `33_ux_pantry_list_dark.png` | ✅ `AccessibilityChecksTest.thePantryList_theSortMenu_andTheDeleteDialog` | ✅ same test; overflow 48 dp | ✅ `33_ux_pantry_list_fontscale2.png` | ✅ `33_ux_pantry_list_land.png` | ✅ `33_ux_talkback_pantry.png` | ✅ |
| Pantry empty | ✅ `33_ux_pantry_empty_light.png` | ✅ `33_ux_pantry_empty_dark.png` | ✅ `AccessibilityChecksTest.theEmptyPantry`; illustration decorative | ✅ button 48 dp | ✅ `33_ux_pantry_empty_fontscale2.png` | ✅ `33_ux_pantry_empty_land.png` | — | ✅ |
| Delete dialog | ✅ `33_ux_delete_dialog_light.png` | ✅ `33_ux_delete_dialog_dark.png` | ✅ `AccessibilityChecksTest.thePantryList_theSortMenu_andTheDeleteDialog` | ✅ Material dialog buttons | ✅ `33_ux_delete_dialog_fontscale2.png` | ✅ `33_ux_delete_dialog_rotated_land.png` (a `DialogFragment` now: still open) | — | ✅ |
| Undo Snackbar | ✅ `33_ux_undo_snackbar_light.png` | ✅ `33_ux_undo_snackbar_dark.png` | ✅ action labelled "Undo" | ✅ Material action | ✅ `33_ux_undo_snackbar_fontscale2.png` | ⚠️ finding 2 below | — | ✅ |
| Add form | ✅ `33_ux_add_form_light.png` | ✅ `33_ux_add_form_dark.png` | ✅ `AccessibilityChecksTest.theAddForm_withEveryErrorShowing`; every field labelled by its hint | ✅ end icons 48 dp | ✅ `33_ux_add_form_fontscale2.png` | ✅ `33_ux_add_form_rotated_land.png` (name, quantity, unit and date kept) | ✅ `33_ux_talkback_form.png` (the same form, in edit mode) | ✅ |
| Add form, errors | ✅ `33_ux_add_form_errors_light.png` | ✅ `33_ux_add_form_errors_dark.png` (errors kept through the theme's re-creation) | ✅ same test; error icons labelled "Error" | ✅ | ✅ `33_ux_add_form_errors_fontscale2.png`, `33_ux_add_form_errors_fontscale13.png` | ✅ errors kept, as the dark shot shows | ✅ errors in the tree, see *TalkBack* | ✅ |
| Edit form | ✅ `33_ux_edit_form_light.png` | ✅ `33_ux_edit_form_dark.png` | ✅ "Clear expiry date", "Choose expiry date" | ✅ | ✅ `33_ux_edit_form_fontscale2.png` | ✅ same layout and state as the add form | ✅ `33_ux_talkback_form.png` | ✅ |
| Recipes, suggestions and Almost there | ✅ `33_ux_recipes_light.png` | ✅ `33_ux_recipes_dark.png` | ✅ `AccessibilityChecksTest.theRecipesTab_withAlmostThere_thenTheSettingsTab` | ✅ whole rows and cards | ✅ `33_ux_recipes_fontscale2.png` | ✅ `33_ux_recipes_scrolled_land.png`, `33_ux_recipes_scrolled_land_recreated.png` (scrolled to Garlic bread, still there after re-creation) | — | ✅ |
| Recipes, zero match | ✅ `33_ux_recipes_no_match_light.png` | ✅ `33_ux_recipes_no_match_dark.png` | ✅ live region on the message | ✅ | ✅ `33_ux_recipes_no_match_fontscale2.png` | ✅ state in the ViewModel | — | ✅ |
| Recipe detail | ✅ `33_ux_recipe_detail_light.png` | ✅ `33_ux_recipe_detail_dark.png` | ✅ `AccessibilityChecksTest.theRecipeDetailScreen`; marks "You have this", "You need this" | ✅ | ✅ `33_ux_recipe_detail_fontscale2.png` | ✅ `33_ux_recipe_detail_land.png` | — | ✅ |
| Settings | ✅ `33_ux_settings_light.png` | ✅ `33_ux_settings_dark.png` | ✅ `AccessibilityChecksTest.theRecipesTab_withAlmostThere_thenTheSettingsTab` | ✅ preference rows | ✅ `33_ux_settings_fontscale2.png` | ✅ `33_ux_settings_land.png`, `33_ux_settings_scroll_recreated.png` | — | ✅ |

TalkBack is required for the pantry list and the form only; "—" means not part of the walk. The
loading state (the shared layout's progress) appears only when a match or the recipe takes longer
than 300 ms, which it never did on the emulator, so it has no screenshot; `StateMessage.showLoading`
gives the indicator its description, "Matching recipes to your pantry" or "Loading the recipe".

## TalkBack

TalkBack 15 on the API 35 emulator, enabled with
`adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`,
with its developer option *Display speech output* on, which captions each announcement.

- **Spoken, captioned:** on the Pantry tab, "Sort the pantry. Button. Sort by"
  (`33_ux_talkback_pantry.png`); on the edit form, "Navigate up. Button" (`33_ux_talkback_form.png`).
- **What TalkBack reads, from the accessibility tree** (`uiautomator dump`, text or description of
  each focusable node). Pantry rows: "milk, 1 litre, Expired 1 day ago", "tomatoes, 6 pieces,
  Expires in 2 days", then "More options for milk". The form: Name "Basil", "Characters entered 5 of
  60", Quantity "2", Unit "pcs", "Show dropdown menu", Expiry date "Oct 10, 2026", "Choose expiry
  date", "Clear expiry date", the helper "Leave it empty if the ingredient does not expire.", and on
  Save with the fields empty: "Name is required.", "Enter a quantity.", "Choose a unit.", each
  beside an icon labelled "Error".
- **Not done:** a full swipe-by-swipe walk with TalkBack speaking every row and field. Injected
  `adb shell input swipe` gestures only sometimes register as TalkBack gestures on this emulator,
  and `uiautomator events`, the other way to record announcements, suppresses TalkBack while it
  listens. The walk is done by hand before the video (Issue 36).

## Findings

1. **"tomatoes" broke mid-word at font scale 2.0.** The badge beside the name left it too narrow.
   Fixed: the badge is under the amount (`item_pantry.xml`), shown in `33_ux_pantry_list_fontscale2.png`.
2. **The undo Snackbar does not survive a rotation.** A Snackbar belongs to the window it was shown
   in, and the delete has already happened, so after a rotation the Undo is gone and the item has to
   be added again. Left as it is: an undo that outlives a rotation would need the deleted item held
   in the ViewModel and the Snackbar shown again, for a few seconds of a rare case.
3. **The delete confirmation closed on rotation.** Fixed: it is a `DialogFragment`
   (`DeleteIngredientDialog`), still open after a rotation and two theme changes.
4. **Accessibility Scanner was not run.** The emulator's Play Store needs a signed-in Google account
   to install it. `AccessibilityChecksTest` runs the Accessibility Test Framework, the engine the
   Scanner app is built on, through Espresso on every screen above, and reports **no finding**. Made
   to fail on purpose, it does: a FAB with its description removed fails two tests with
   `SpeakableTextPresentCheck`. It did **not** fail on a 24 dp overflow button inside a clickable
   row, which the framework accepts because the row is a larger target; the 48 dp rule for every
   `ImageButton` is therefore held by `LayoutStyleConventionsTest` instead.
5. **The add form scrolls in landscape rather than fitting.** All four fields and Save stay
   reachable in the `NestedScrollView` (`33_ux_add_form_rotated_land.png`), so there is no
   `layout-land` copy of the form to keep in step.

## What the pass changed

- `view_state_message.xml` and `ui/StateMessage`: one layout for loading, empty and error, on the
  Pantry tab, the Recipes tab and the detail screen (which shows its progress there now).
- `ContentDescription` is a Lint error; every pantry row has one spoken description, with units in
  words through `util/SpokenQuantity`.
- The delete confirmation is a `DialogFragment`.
- The pantry row puts the badge under the amount.
- `AccessibilityChecksTest` (device) and the `ImageButton` rule in `LayoutStyleConventionsTest` (JVM).
- No colour, size, spacing or type style changed: the palette and type scale from Issue 2 stay.
