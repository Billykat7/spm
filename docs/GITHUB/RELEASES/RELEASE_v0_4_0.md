# Release v0.4.0: Strict-Matching Engine

**Date:** 2026-10-04 · **Milestone:** M4 · **Issues closed:** 18–22

The engine behind the app's main promise, finished and tested before any screen uses it. Given the
pantry and a recipe, `StrictMatcher` says whether the recipe can be made right now. Every required
ingredient must be there, under its normalised name, in at least the required amount; four of five
is not a match. The answer is pure Java with no Android, no Room and no clock in it, so the JVM tests
check it case by case in under a second. Nothing on screen changes in this release: the Recipes tab
still shows its placeholder, and Milestone 5 puts the engine behind it.

## What shipped

- **Ingredient names compared in one form** (Issue 18, pull request #60). `IngredientNormaliser`
  lower-cases (`Locale.ROOT`), trims, collapses spaces, strips punctuation but keeps an inner hyphen or
  apostrophe, and makes the last word singular by rule. `Tomatoes`, ` tomato ` and `TOMATO.` all
  become `tomato`. Then it applies an alias table read from `assets/aliases.json` (thirty entries),
  so `cilantro` is `coriander` and `scallion` is `spring onion`. It does no stemming and no fuzzy
  matching. The table test went red on its first run (`olives` became `olif`), and the plural rules
  were fixed, not the rows; `docs/REPORT/CHALLENGES.md` has the story.
- **Amounts compared in one unit per kind** (Issue 19, #61). `UnitConverter` turns every quantity into
  grams, millilitres or pieces by the fixed factors of decision 5: `1 kg` is `1000 g`, `2 cups` is
  `500 ml`. `CanonicalQuantity.isAtLeast` compares within a kind, allowing a millionth for rounding.
  Duplicate pantry rows of one kind are summed; a mass is never added to a volume. A display helper
  for the coming units setting shows `1500 g` as `1.5 kg` or `52.9 oz`, through types that nothing in
  matching accepts.
- **The strict matcher** (Issue 20, #62). `StrictMatcher.match(pantry, recipe, options)` returns a
  `MatchResult` with the status, a `Shortfall` for every line not covered (saying what is there, if
  anything), and the covered count. `matchAll` builds the pantry once for all recipes. Expired rows
  are left out unless the caller counts them (decision 6), with today passed in. `MatchingPurityTest`
  fails the build on an Android or data-layer import, a clock, or a raw name comparison in the engine.
- **The marker's cases, written down** (Issue 21, #63). 56 named scenarios in `scenarios.csv`, from
  four of five to a tenth of a gram short. Also the twenty real seed recipes, and properties checked
  on every one of them: take away or expire any ingredient and the match breaks; add anything and it
  holds. JaCoCo now measures the unit tests, and the gate's new coverage stage fails when any line of
  `domain/matching/` is run by no test.
- **"Almost there", kept apart by type** (Issue 22, #64). A recipe with exactly one shortfall, missing
  or short, is `MatchStatus.ALMOST_THERE`; none is `CAN_MAKE`; two or more is `CANNOT_MAKE`.
  `MatchResults.partition` returns the three as separate lists in input order, and a result cannot be
  built with a status its shortfalls do not give. A property test over the twenty recipes and 200
  random pantries checks that the suggestions and the almost-there list never meet.

At this tag: 706 JVM unit tests and 101 instrumented tests, all passing, the instrumented ones on an
API 26 and an API 35 emulator. `domain/matching/` is covered at 100% of lines and branches, and Lint
reports no error.

## Database

- Room schema version 1 (`app/schemas/com.btk.spm.data.db.AppDatabase/1.json`), unchanged from
  `v0.3.0`: no migration.
- New asset: `assets/aliases.json`, read-only, shipped in the APK. Nothing new is stored on the device.

## Upgrade notes

- Install `spm-v0.4.0.apk` from the release page. It looks and behaves like `v0.3.0`: the engine has
  no screen yet.
- It will not install over `v0.3.0`. Each release is signed by a fresh GitHub runner's debug key, so
  Android refuses it as an update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstall `v0.3.0` first;
  this deletes the pantry items entered in it.

## Known issues

- **A mass never covers a volume** (decision 5). `500 g` of flour does not cover a recipe's
  `2 cups`: it is a shortfall, because turning a volume into a mass needs a density for every
  ingredient, and the app keeps none. The seed recipes write each ingredient in the kind a pantry
  would hold it in, so the seed never meets it. A pantry that holds flour in cups, against a recipe
  in grams, will. `CHALLENGES.md` records it for the report's reflection.
- **Plurals of words that end in -ie or -i in the singular come out wrong.** The `-ies → -y` rule
  turns `cookies` into `cooky`, `brownies` into `browny` and `chillies` into `chilly`, so `chillies`
  in the pantry does not cover a recipe's `chilli`. Both sides go through the same rules, so
  `cookies` still matches `cookies`; only the plural against the singular is missed. No seed
  ingredient is affected. An exception or an alias per word fixes it, with a row in the test table;
  it is a candidate for `v0.4.1`.
- **No stemming and no fuzzy matching, on purpose.** A typo (`tomatoe`) or a synonym missing from
  the alias table is a different ingredient, and the table cannot be changed from the app. That is the
  safer mistake under the strict rule: a missed suggestion, never a wrong one.
- **A one-ingredient recipe with that ingredient missing is "almost there".** That is the rule as
  written, exactly one shortfall. Every seed recipe has three ingredients or more, so it never shows.
- **The engine is not on screen yet.** The Recipes tab is still a placeholder until Milestone 5, and
  the units setting that uses the display helper is Issue 28.
- **Coverage is measured for the JVM tests only.** The whole-app figure in the report (48% of lines)
  leaves out the screens and DAOs, which are tested on a device, where JaCoCo is not run.
- **Instrumented tests run only on a laptop.** CI has no emulator; the gate's device stage is run by
  hand before a merge that touches the database or a screen.
- **One Lint warning remains**, as in `v0.3.0`: `androidx.appcompat` 1.8.0 is available.
