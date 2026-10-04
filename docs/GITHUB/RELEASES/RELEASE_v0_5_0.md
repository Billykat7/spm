# Release v0.5.0: Suggested Recipes & Detail

**Date:** 2026-10-04 · **Milestone:** M5 · **Issues closed:** 23–27

The engine of `v0.4.0` is on screen. The Recipes tab lists the recipes the pantry can make right now,
worked out by `StrictMatcher` on a thread of its own and worked out again whenever the pantry
changes, so a recipe appears the moment its last ingredient goes in and disappears the moment one
comes out. When nothing can be made, the tab says so in the brief's words. A tap opens the recipe in
full, every ingredient marked have or need. Below the suggestions, clearly apart, are the recipes one
ingredient away, the brief's bonus.

## What shipped

- **The Suggested Recipes tab** (Issue 23, pull request #66). `SuggestedRecipesViewModel` observes
  the pantry and the recipes through Room's `LiveData`, maps the entities to the engine's values in
  `data/mapping/`, and runs `matchAll` on a single matching thread, never the main thread. The tab
  lists `MatchResults.partition(...).canMake` and nothing else, under "Suggested recipes (N)". A
  generation counter throws away a match of an older pantry that finishes late. A JVM test records the
  thread the matcher runs on; a scratch commit that moved it to the main thread made that test fail.
- **Feedback when nothing matches** (Issue 24, #67). Three reasons, one shared layout:
  - the brief's "No recipes match your pantry yet, add more ingredients";
  - an empty pantry;
  - recipes that did not load, with no button.

  "Add ingredients" switches the running host to the Pantry tab rather than opening a second one. The
  progress indicator shows once, before the first result. On a fresh install, the recipes are passed
  on only after the first-run seed, so the tab never says they failed to load while they are still
  going in.
- **The recipe detail screen** (Issue 25, #68). `RecipeDetailActivity` is the second screen opened by
  an explicit Intent, with the recipe's id in `IntentKeys.EXTRA_RECIPE_ID`; an unknown id says so and
  closes. Each ingredient shows a check or a cross and "need 250 g, have 1 kg", then the numbered
  method follows. The marks are the matcher's own answer, through one additive engine change:
  `MatchResult.checks()`, one `IngredientCheck` per required line. No screen compares a quantity, and
  every engine test of `v0.4.x` passes unchanged.
- **The live re-evaluation, proven** (Issue 26, #69).
  - A JVM test moves Tomato pasta from "almost there" into the suggestions and back, one state per pantry push.
  - A device test inserts and deletes its fifth ingredient with the tab open, waiting on the matcher through an `IdlingResource`.
  - The "count expired items" setting is observed live (`AppPreferences.observeBoolean`), so changing it runs the matcher again.
  - `docs/DEMO/MATCH_PROOF_STEPS.md` holds the steps the video films.
- **"Almost there", clearly separated** (Issue 27, #70). Below the suggestions, under a divider and
  the heading "Almost there (missing one ingredient)", outlined cards name each recipe's one missing
  or short ingredient: "Missing: 50 g cheese", "Short: need 50 g butter, have 40 g". One
  `RecyclerView` over a `ConcatAdapter` holds both sections, each its own adapter, and each refuses a
  recipe of the other status. They are never counted in "Suggested recipes (N)". The full-screen empty
  state is now for when there is nothing at all to show.

At this tag: 806 JVM unit tests and 109 instrumented tests, all passing, the instrumented ones on an
API 26 and an API 35 emulator. `domain/matching/` is covered at 100% of lines, and Lint reports no
error.

## Database

- Room schema version 1, unchanged since `v0.2.0`: no migration. Nothing new is stored. The "count
  expired items" setting is read from the app's `SharedPreferences`; nothing writes it yet.

## Upgrade notes

- Install `spm-v0.5.0.apk` from the release page. The Recipes tab, the empty states, the detail screen
  and the "Almost there" section are new; the Pantry tab behaves as in `v0.4.x`.
- It will not install over `v0.4.1`: each release is signed by a fresh GitHub runner's debug key, so
  Android refuses it as an update. Uninstall `v0.4.1` first; this deletes the pantry items in it.

## Known issues

- **"Count expired items" has no screen until Issue 28.** It is off, as decision 6 sets it, so expired
  items never count. The matching side is done and tested: the Recipes tab observes the setting live
  and matches again when it changes. Nothing in the app can change it yet.
- **Amounts are shown in metric units only, until Issue 28** adds the units preference: "need 2 tbsp,
  have 500 ml", never ounces. Matching does not depend on it.
- **The detail screen reads "count expired items" at each match, not live.** Once Issue 28 can change
  it, an open detail screen shows the change at its next match or when it is opened again.
- **Units are symbols after the number.** A card reads "Missing: 2 pcs egg", not "2 eggs": the unit
  comes from the `Unit` enum's symbol, not from the ingredient's name.
- **"Almost there" is in name order, not ranked by closeness.** Short by a gram and missing entirely
  look the same; a ranking is on the backlog.
- **A mass still never covers a volume** (decision 5), as in `v0.4.0`. `500 g` of flour does not cover
  `2 cups`; the seed writes each ingredient in the kind a pantry would hold it in.
