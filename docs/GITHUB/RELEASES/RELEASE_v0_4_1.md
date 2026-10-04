# Release v0.4.1: Plurals of -ie and -i words

**Date:** 2026-10-04 · **Milestone:** M4 (patch) · **Issues:** follow-up to 18

A patch to the strict-matching engine of `v0.4.0`, fixing one of its known issues. The normaliser
turned `cookies` into `cooky`, `brownies` into `browny` and `chillies` into `chilly`, so a pantry
holding chillies never covered a recipe that needs a chilli. Now each comes back to its own singular.
Nothing on screen changes: the engine still has no screen until Milestone 5.

## What shipped

- **Plurals of words whose singular ends in -ie or -i** (Issue 18 follow-up, pull request #65). The
  `-ies → -y` rule is right for berries, cherries and anchovies, but nothing in the spelling tells
  those apart from cookies, so `IngredientNormaliser` now keeps a short list,
  `SINGULAR_WORDS_ENDING_IN_IE_OR_I`: brownie, chili, chilli and cookie. A word ending in `-ies` that
  is one of these without its `s` or `es` keeps that singular; every other `-ies` word still ends in
  `-y`. With the alias table's existing `chili → chilli`, `chilli`, `chillies`, `chili` and `chilies`
  are now one ingredient.
- **Rows for each word.** Five new rows in `IngredientNormaliserTest` (cookies, brownies, chillies,
  chilies, chocolate chip cookies), written and failing before the fix; berries, cherries, anchovies
  and pies still pass unchanged. Three new rows in `scenarios.csv`: cookies cover a cookie, a brownie
  covers brownies, chillies cover a chilli. `AliasesJsonTest` checks the four chilli spellings against
  the shipped table. `docs/REPORT/CHALLENGES.md` has the story.

At this tag: 720 JVM unit tests, all passing, and `domain/matching/` still covered at 100% of lines
and branches. This release changes no screen, DAO or instrumented test; the 101 instrumented tests
are those of `v0.4.0`. Lint reports no error.

## Database

- Room schema version 1, unchanged: no migration. `assets/aliases.json` is unchanged.

## Upgrade notes

- Install `spm-v0.4.1.apk` from the release page. It looks and behaves like `v0.4.0`.
- It will not install over `v0.4.0`, for the same reason as before: each release is signed by a
  fresh GitHub runner's debug key. Uninstall `v0.4.0` first; this deletes the pantry items in it.

## Known issues

- **The next word like these needs its own entry.** Another ingredient whose singular ends in `-ie`
  or `-i` (`veggies`, say) still comes out with a `-y` until it is added to the list with a row.
  Both sides go through the same rules, so the plural still matches the plural; only the plural
  against the singular is missed, and a missed match is the safer mistake under the strict rule.
- The other known issues of `v0.4.0` still apply: a mass never covers a volume, no stemming and no
  fuzzy matching, a one-ingredient recipe with that ingredient missing is "almost there", and the
  engine is not on screen yet.
