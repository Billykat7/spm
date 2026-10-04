# Release v0.3.0: Pantry Management

**Date:** 2026-10-04 · **Milestone:** M3 · **Issues closed:** 13–17

The first release a person can use. The Pantry tab is a live list of what is in the database, and
every operation of the CRUD cycle works on it: add an ingredient through a validated form, see it
appear with no refresh, edit it, delete it with an undo. Each dated item carries a badge saying how
long it has left, and the list puts the soonest-expiring first. Recipes and Settings are still
placeholders: the matcher that fills the Recipes tab is Milestone 4.

## What shipped

- **The pantry list** (Issue 13, pull request #52): a `RecyclerView` whose `PantryAdapter`, a
  `ListAdapter` with a `DiffUtil` callback, shows the `LiveData` list `PantryViewModel` derives from
  the repository. A row written anywhere appears with no refresh, and an edit redraws only its own
  row (`adb logcat -s ListChange` in a debug build shows which). Amounts read `4 pcs` and `1.5 kg`.
  An empty pantry shows an empty state with an "Add ingredient" button.
- **Add, with validation** (Issue 14, #53): `AddEditIngredientActivity`, started by an explicit
  `Intent` for a result. It asks for a name, a quantity, a unit from a dropdown and an optional expiry
  date from a date picker. One pure class, `Validators`, decides what may be saved: a name of 1 to 60
  characters, a quantity above 0 and at most 100000 (a comma works as the decimal point), a unit, and
  no date in the past. The form shows every error at once under its field and writes nothing until
  they are fixed.
- **Delete, with undo** (Issue 16, #54): the row's overflow Delete asks first, in a dialog naming the
  item. A Snackbar above the add button then offers Undo, which brings the row back with its
  original id.
- **Expiry badges and sorting** (Issue 17, #55): decision 6 in one pure function,
  `ExpiryRules.statusOf`, with "today" passed in. A row reads "Expired 1 day ago", "Expires today",
  "Expires in 2 days" (expiring soon, within 3 days by default) or "Expires in 10 days", in colours
  from the theme, and an undated row shows no badge. The list sorts soonest-expiring first, undated
  last; a toolbar menu switches to name order, and the choice is remembered across restarts.
- **Edit** (Issue 15, #56): a tap on a row, or its overflow Edit, opens the same form with the row's
  id in `IntentKeys.EXTRA_PANTRY_ITEM_ID`, prefilled from the database. Save validates as when adding
  and updates that row with the same id; the row count does not change. A rotation keeps what has
  been typed, and an id with no row behind it says "Ingredient not found" and closes.

At this tag: 247 JVM unit tests and 101 instrumented tests, the instrumented ones run on an API 26 and
an API 35 emulator, all passing; Lint reports no error. Screenshots of every new screen, light and
dark, are in `docs/REPORT/screenshots/` (rows 09–19 of its index).

## Database

- Room schema version 1 (`app/schemas/com.btk.spm.data.db.AppDatabase/1.json`), unchanged from
  `v0.2.0`: no migration.
- New on the device: the app's default preferences file (`com.btk.spm_preferences.xml`), which
  holds the pantry's sort order (`pantry_sort`, by enum name). The expiring-soon threshold is read
  from the same file and defaults to 3 days until the Settings screen (Issue 28) can change it.

## Upgrade notes

- Install `spm-v0.3.0.apk` from the release page.
- It will not install over `v0.2.0`. Each release is signed by a fresh GitHub runner's debug key, so
  Android refuses it as an update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstall `v0.2.0` first.
  Nothing a user entered is lost: `v0.2.0` had no screen to enter anything, and the recipes are
  seeded again on the first start.

## Known issues

- **A badge is worked out when its row is drawn.** A list left open across midnight shows yesterday's
  badges until something redraws the rows (any change, a scroll, or returning to the tab). Following
  the date live would need a timer or a date-change receiver for a case that ends at the next touch.
- **A rotation dismisses the delete confirmation and the Undo Snackbar.** A rotation while the
  dialog is open cancels it, and nothing is deleted; the user taps Delete again. A rotation while
  Undo is on offer drops the Snackbar, and the delete stands, as it does when Undo times out. Issue
  30 decides whether a `DialogFragment`, and an undo that survives a rotation, are worth having.
- **Leaving the form does not ask about unsaved changes.** Back and the up arrow cancel at once.
  This is also Issue 30's.
- **Swipe to delete is not there.** The overflow Delete is the one way in, as the issue allows.
- **Recipes and Settings are placeholders.** The matcher and the suggested recipes are Milestones 4
  and 5, and the Settings screen is Milestone 6.
- **Instrumented tests run only on a laptop, and the gate can miss a device.** CI has no emulator.
  When the test APK fails to install on a device (an APK installed by hand blocks it on API 26), the
  Android Gradle plugin still reports the run as passed, with no tests run there. Until the gate checks
  every device's results, uninstall `com.btk.spm` and `com.btk.spm.test` before a device run, and
  check each device's count in the run pasted in the pull request.
- **One Lint warning remains**, as in `v0.2.0`: `androidx.appcompat` 1.8.0 is available.
