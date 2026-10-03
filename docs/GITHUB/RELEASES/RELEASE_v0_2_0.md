# Release v0.2.0: Local Database (Room)

**Date:** 2026-10-03 · **Milestone:** M2 · **Issues closed:** 8–12

The data layer, finished and proven before any screen uses it. The app now keeps its pantry and its
recipes in a Room database on the device, seeds twenty recipes the first time it starts, and a test
shows the data surviving the database being closed and reopened. Nothing new is visible on screen
yet: the tabs are still placeholders, and the pantry list and the recipe screens that read this
database are Milestones 3 and 5.

## What shipped

- **The data model** (Issue 8, pull request #47): Room over SQLite on the device (decision 1), with
  three tables: `pantry_items`, `recipes` and `recipe_ingredients`, whose `recipe_id` is a foreign key
  that cascades on delete, with an index. Converters store a unit by its enum name, a date as its
  epoch day and a recipe's steps as one JSON array. `SpmApplication` builds the one database and owns
  the single write thread. The README's *Database* section says why this database and not another.
- **Pantry create, read, update and delete** (Issue 9, #48): `PantryItemDao`, whose pantry list is a
  `LiveData` that updates itself, behind `PantryRepository`, which runs every write on the write
  thread. `DaoBoundaryTest` fails the build when code outside `data/` reaches a DAO directly.
- **Recipes with their ingredients** (Issue 10, #49): `RecipeDao` reads each recipe with its
  ingredients through one `@Relation` query and stores a recipe and its ingredients in one
  transaction. `RecipeRepository` is read-only, because recipes are seed data (decision 7).
- **Twenty seeded recipes** (Issue 11, #50): `assets/recipes.json`, from Cheese omelette to Rice
  pudding, each ingredient in its canonical name and in one unit kind across the whole collection.
  They are inserted on the first start in one transaction and never again: the app checks for an
  empty table on every start instead of relying on a once-only database callback.
- **Persistence proven** (Issue 12, #51): `PersistenceAcrossRestartTest` writes to a real database
  file, closes it, opens a new instance on the same file and reads everything back. `SchemaFileTest`
  checks the committed schema, and a new `schema` stage in `scripts/ci-local.sh` fails when a build
  changes it without a version bump. `CONTRIBUTING.md` says how to run the device tests and when a
  pull request must show them.

At this tag: 125 JVM unit tests and 68 instrumented tests, the instrumented ones run on an API 26 and
an API 35 emulator, all passing; Lint reports no error.

## Database

- Room schema version 1 (`app/schemas/com.btk.spm.data.db.AppDatabase/1.json`): first schema, no
  migration.

## Upgrade notes

- Install `spm-v0.2.0.apk` from the release page. The database file, `spm.db`, is created the first
  time the app starts, with the twenty recipes in it.
- This release will not install over `v0.1.0`. Each release is signed by a fresh GitHub runner's debug
  key, so Android refuses it as an update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstall `v0.1.0`
  first; it stored nothing, so nothing is lost.

## Known issues

- **Nothing on screen uses the database yet.** The pantry list arrives in Milestone 3 and the
  recipes in Milestone 5; until then the data can be seen with Android Studio's Database Inspector.
- **The seed's ingredient names are canonical by hand.** `IngredientNormaliser` (Issue 18) does not
  exist yet, so `RecipesJsonTest` checks that each name is trimmed and lower case, and the singular
  form was checked by eye. Issue 18 tightens the test to `normalise(name).equals(name)`.
- **Library versions are held back on purpose.** Room stays at 2.6.1, as decision 1 pins it, and that
  holds lifecycle at 2.10.0: lifecycle 2.11 stops Room 2.6.1's annotation processor from compiling a
  `@Transaction` method (`docs/REPORT/CHALLENGES.md`). Both move together when Room moves.
- **Instrumented tests run only on a laptop.** CI has no emulator. And when the test APK fails to
  install on a device, the Android Gradle plugin reports the run as passed with no tests run on that
  device; until the gate checks the test count per device, check it in the run that is pasted in the
  pull request.
- **Two Lint warnings remain**, as in `v0.1.0`: `androidx.appcompat` 1.8.0 is available, and the
  `spacing_xs` dimension is not used yet.
