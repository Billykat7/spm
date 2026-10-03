# Smart Pantry Manager: engineering non-negotiables

The rules below are the ones a marker tests directly, or the ones that keep the codebase honest
enough to defend in the video. Each is enforced by a test or a CI guard, not by review attention.
The IDE rules in [`docs/IDE/RULES`](IDE/RULES/) restate them with examples; the issue that first
makes each one enforceable is named in brackets.

## 1. The strict-matching rule is decided in one place

A recipe is **suggested** only when **every** ingredient it requires is in the pantry, in **at least**
the required quantity. Four of five ingredients is not a match. One missing ingredient is not a
match. `StrictMatcher.match()` is the only function that decides it, every screen reads its
`MatchResult`, and no adapter, fragment or query re-implements the comparison (Issue 20).

## 2. Matching is normalised, never a raw string comparison

Ingredient names pass through `IngredientNormaliser` (case, whitespace, punctuation, plurals,
aliases) and quantities through `UnitConverter` (one canonical amount per `UnitKind`) before they
are compared. `"Tomatoes"` and `"tomato"` are the same ingredient; `1 kg` covers a recipe's `250 g`.
A naive `equals()` on the raw name anywhere in the matching path fails the guard test (Issues 18, 19).

## 3. "Almost there" never leaks into the suggested list

The optional list of recipes missing exactly one ingredient is computed as its own
`MatchStatus.ALMOST_THERE`, rendered under its own heading, and is never mixed into, sorted with or
counted as a suggestion (Issues 22, 27).

## 4. No maps, no location, no payments, no store

The manifest declares no location permission, Gradle declares no Maps, Places, Location or payment
dependency, and no screen asks where the user is. A guard test reads the manifest and the
dependency list and fails if any appears (Issue 4). Publishing to Google Play is out of scope.

## 5. Java only, in `app/`

Every file under `app/src` is `.java`, `.xml`, `.json` or a resource. No `.kt` file enters the app
module; CI greps for one and fails (Issue 4). Build scripts and the repository's helper scripts are
tooling and are not covered by the rule, but the app the marker reads is Java.

## 6. Room is the single source of truth

Pantry items and recipes live in the Room database and nowhere else. No screen keeps its own list;
every list observes a DAO through a repository and a `ViewModel`, so a change made on one screen is
visible on the next without a refresh. Only `data/` touches a DAO: `DaoBoundaryTest` fails the build
when a file outside it imports from `data.db` or calls `pantryItemDao()` or `recipeDao()`, and
`SpmApplication` may name `AppDatabase` only to build it (Issue 9). Data survives closing and
reopening the app, and a test proves it (Issues 8, 12).

## 7. Fixed sets are enums; keys are constants

Units, unit kinds, match statuses and preference keys are Java `enum`s. Intent extras and
`SharedPreferences` keys are `static final` constants in one class each. A string literal used as a
status, a unit or an extra key fails the conventions test (Issue 6).

## 8. Every form validates before it writes

A pantry item cannot be saved with an empty name, a non-positive quantity, no unit or an expiry date
in the past without the user seeing a field-level error, and nothing reaches the database until the
form is valid (Issues 14, 31).

## 9. The student number is filled in locally and never committed

The repository is public. The student number appears in git only as the `<StudentNumber>`
placeholder; the real value lives in the git-ignored `.submission.env` and is substituted at build
time into the exported report and the ZIP name, under the ignored `build/` folder (Issues 35, 37; see
[`student-number-never-committed.mdc`](IDE/RULES/student-number-never-committed.mdc)).

## 10. The commit history is the evidence

The brief treats the GitHub history as proof of the development process and checks it against the
video and report. Every commit starts with `Issue <N>: `, describes one real step, and is pushed
when the issue's branch is ready; there are at least ten of them, spread across the weeks of
development. No bulk commit, no `final`, no `update` (see
[`commit-history-evidence.mdc`](IDE/RULES/commit-history-evidence.mdc)).

---

**See also:** [GitHub workflow docs](GITHUB/README.md) · [IDE rules](IDE/RULES/) · [The brief](../.btk/MAD700D/MAD700D_WORK.md) (local, not in git)
