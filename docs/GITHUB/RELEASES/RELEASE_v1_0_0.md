# Release v1.0.0: Smart Pantry Manager, the submitted build

**Date:** 2026-10-04 · **Milestones:** M1–M7 · **Issues closed:** 1–38, all of them

Smart Pantry Manager is an Android app for one person cooking at home. It keeps a pantry of what is in
the kitchen, with amounts and expiry dates, and suggests only the recipes that pantry can make right
now. The rule is strict: a recipe is suggested only when every one of its ingredients is in the pantry
in at least the amount it needs. This is the build that is submitted. **No app code changed since
`v0.6.0`:** `git diff v0.6.0 v1.0.0 -- app/` prints nothing; M7 added the evidence around it.

## The project, milestone by milestone

| Milestone | Tag | Date | What it delivered |
|-----------|-----|------|-------------------|
| M1 Foundation & Local CI | `v0.1.0` | 2026-10-03 | A Java 17 Android project, the Material 3 theme, three tabs, the local gate and CI, the release APK on every tag, and the shared vocabulary (`Unit`, `Quantity`, `MatchStatus`, `IntentKeys`, `PrefKey`) |
| M2 Local Database (Room) | `v0.2.0` | 2026-10-04 | `AppDatabase` with the pantry, recipes and recipe ingredients, repositories, a seed of twenty recipes, the exported schema and persistence tests |
| M3 Pantry Management | `v0.3.0` | 2026-10-04 | The live pantry list, the add and edit form with validation, delete with Undo, expiry badges and sorting |
| M4 Strict-Matching Engine | `v0.4.0`, `v0.4.1` | 2026-10-04 | Name normalising, unit conversion, `StrictMatcher` at 100% line coverage, and the almost-there status; the patch fixed the plurals of -ie and -i words |
| M5 Suggested Recipes & Detail | `v0.5.0` | 2026-10-04 | The Recipes tab on the live pantry, the zero-match state, the recipe detail screen through an explicit Intent, and the almost-there section |
| M6 Settings, Alerts & UX | `v0.6.0` | 2026-10-04 | The Settings screen, the daily expiring-soon notification, the UX and accessibility pass, hardened validation, and the Espresso suite |
| M7 Evidence, Report & Submission | `v0.7.0`, `v1.0.0` | 2026-10-04 | The report's screenshots and diagrams, the report's source, the video script, the package script and the final README |

## What the app does

- **Pantry:** add, list, edit and delete ingredients, each with a name, a quantity, a unit and an
  optional expiry date. The list is live, sorted soonest-expiring first or by name, and badged
  "Expires in 2 days" or "Expired 1 day ago". Every input error shows under its field, all at once.
- **Suggested recipes:** the Recipes tab lists what the pantry can make, matched off the main thread
  and again on every pantry change. It says why when nothing matches, and shows the recipes one
  ingredient away in their own section.
- **Recipe detail:** every ingredient marked have or need ("need 200 g, have 500 g"), and the method.
- **Settings:** the expiring-soon threshold, metric or imperial amounts, whether expired items count
  when matching, and the daily alert. Each change reaches an open screen at once.
- **Alert:** once a day, with no screen open, one notification names what is about to expire.

## Database

- **Room over SQLite, on the device** (decision 1). The app needs no account, no network and no
  server, works offline, and keeps the data on the phone. Firebase would add a cloud dependency and a
  sign-in the app has no use for. PostgreSQL would need a server and a REST layer to build and host.
- Room schema version 1, exported to `app/schemas/`, unchanged since `v0.2.0`: three tables,
  `pantry_items`, `recipes` and `recipe_ingredients`, with one foreign key from an ingredient to its
  recipe.

## Install

1. Download `spm-v1.0.0.apk` from this release's assets.
2. On an Android 8.0 (API 26) or newer device or emulator, allow installs from that source, or run
   `adb install spm-v1.0.0.apk`.
3. If an earlier release is installed, uninstall it first. Each release is signed by a fresh GitHub
   runner's debug key, so Android refuses it as an update; uninstalling deletes that pantry.

To build from source instead, follow *Getting started* in the [README](../../../README.md).

## The submission

The video, the exported report and the ZIP are in the submission, not in git. Each is named with
`<StudentNumber>`, which no committed file contains:

- `spm_demo.mp4`, recorded from [`docs/DEMO/VIDEO_SCRIPT.md`](../../DEMO/VIDEO_SCRIPT.md);
- `<StudentNumber>_Katalayi_MobileAppDev700_Assignment.docx`, built from
  [`docs/REPORT/REPORT.md`](../../REPORT/REPORT.md) as [`docs/REPORT/README.md`](../../REPORT/README.md) describes;
- `<StudentNumber>_Katalayi_MobileAppDev700_Assignment.zip`, built by
  `scripts/package_submission.sh --tag v1.0.0`, with the source of this tag, the video and the report,
  under 50 MB.

## Known limitations and issues

- **Units convert only within their kind** (decision 5). Grams and millilitres, or pieces and grams,
  are never compared, so an ingredient a recipe gives in millilitres never matches the same item
  stocked in grams. A density table would lift this.
- **Recipes cannot be edited** (decision 7). The twenty recipes come from the seed; editing them is
  future work.
- **The undo Snackbar does not survive a rotation**, and **Accessibility Scanner itself was not run**
  (its engine runs in `AccessibilityChecksTest`). Both carried over from `v0.6.0`.
- **The README's setup steps were not re-timed on a clean clone** for this release. That check was cut
  from scope; the steps are the ones each milestone has used.
