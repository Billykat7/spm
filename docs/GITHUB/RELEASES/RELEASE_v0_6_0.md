# Release v0.6.0: Settings, Alerts & UX

**Date:** 2026-10-04 · **Milestone:** M6 · **Issues closed:** 28–32

The fifth screen the brief asks for is here, and nothing on it is decorative: the Settings tab's
choices change what the badges, the amounts, the matcher and a new daily alert do, on screens that
are already open. The alert is the first thing the app does with no screen open. Every screen and
state went through one checklist for consistency, dark theme, labels, touch targets, large text and
rotation. Every input rule now lives in one class and shows every error at once. And the five flows
the video shows are driven through the real screens by Espresso on every device gate, without one
sleep.

## What shipped

- **The Settings screen** (Issue 28, pull request #71). A `PreferenceFragmentCompat` over
  `preferences.xml`, whose keys are `PrefKey` strings. `PreferencesXmlTest` fails on a key no
  `PrefKey` names, on a user-facing key missing from the screen, or on a default that differs from
  the one `AppPreferences` reads back to. Each setting has a reader, through `LiveData`:
  - **The expiring-soon threshold**, 1–14 days, re-badges the open pantry list.
  - **Imperial** shows `1500 g` as `52.9 oz` on the pantry, the detail screen and the almost-there cards; nothing stored or matched changes.
  - **Count expired items** runs the matcher again on the Recipes tab and on an open detail screen, which ends `v0.5.0`'s known issue that the detail screen read it only once.

  About gives the version name and links the repository.
- **The expiring-soon alert** (Issue 29, #72). `ExpiryCheckWorker`, a WorkManager `Worker`, is enqueued once as unique 24-hour periodic work and cancelled while alerts are off. It reads every dated row up to today plus the threshold in one query, judges each with the badges' `ExpiryRules`, and posts one notification with a fixed id: "2 items expiring soon", "tomato (tomorrow), milk (in 3 days)". A plain-Java `ExpiryMessageBuilder` words it. Tapping it opens the Pantry tab, and "Send a test alert now" runs it at once. On Android 13 and later, turning the alert on asks for `POST_NOTIFICATIONS`, the app's only permission, after a short explanation if one is due; a refusal switches it back off and offers the system settings. No exact alarm, no foreground service.
- **The UX and accessibility pass** (Issue 30, #73), recorded in
  [`docs/REPORT/UX_CHECKLIST.md`](../../REPORT/UX_CHECKLIST.md): eleven screens and states by eight checks, each with a screenshot.
  - Loading, empty and error share `view_state_message.xml`.
  - Lint fails the build on an image with no description.
  - Each pantry row is announced in words ("tomato, 4 pieces, Expires in 3 days").
  - The delete confirmation survives a rotation.
  - At font scale 2.0 a name broke mid-word, so the badge moved under the amount.
  - The Accessibility Test Framework, the checks Accessibility Scanner runs, passes on every screen through `AccessibilityChecksTest`.
- **Validation hardening** (Issue 31, #74). `Validators` is the only class that builds a `FieldError` or names an `error_*` message (`ConventionsTest`).
  - A quantity is read in the device's own number format, the whole text consumed: "1,5" is one and a half in German and refused in English.
  - A name needs a letter, a quantity at most two decimals, and the threshold 1–14 days, even read back corrupted.
  - The form shows every error at once with Save enabled.
  - A damaged `recipes.json` seeds what it can and logs the rest by position, an empty or unreadable one leaves the Recipes tab on "Recipes could not be loaded", and the detail screen says "Recipe not found" for an unknown id. Nothing in either path crashes.
- **The Espresso suite** (Issue 32, #75). `CrudCycleTest`, `ValidationErrorTest`, `MatchToggleTest`, `PersistenceTest` and `SettingsTest` drive the brief's create, read, update and delete cycle, a validation error, the five-of-five rule, persistence and the Settings toggle through the real screens.
  - `FreshAppRule` gives each test an in-memory seeded database and idling executors for the write thread, the matching thread and Room's query thread.
  - `ConventionsTest` fails on any `Thread.sleep` under `androidTest/`.
  - `./scripts/ci-local.sh --with-device` sets the emulator's animation scales to 0 and names each device's API level.

At this tag: 852 JVM unit tests and 143 instrumented tests, all passing on an API 26 and an API 35
emulator; the `com.btk.spm.ui` package ran ten times in a row green on API 35. `domain/matching/` is
covered at 100% of lines, and Lint reports no error.

## Database

- Room schema version 1, unchanged since `v0.2.0`: no migration. One new synchronous query,
  `PantryItemDao.findWithExpiryOnOrBefore`, reads existing columns. The settings live in the app's
  default `SharedPreferences` under `PrefKey` strings; the pantry sort order stored by `v0.5.0` is
  read unchanged.

## Upgrade notes

- Install `spm-v0.6.0.apk` from the release page. As before, it will not install over `v0.5.0`
  (each release is signed by a fresh GitHub runner's debug key): uninstall `v0.5.0` first, which
  deletes its pantry.
- On Android 13 and later the alert is on by default but cannot post until notifications are
  allowed: its summary says so, and turning it off and on asks for the permission.
- A quantity now uses the device's decimal separator only. "1,5" typed on an English phone is refused
  with "Enter a number, such as 4 or 1.5."; `v0.5.0` accepted both separators everywhere.
- WorkManager is pinned to 2.10.5, the last release on Room 2.6.1, which the app pins (decision 1).

## Known issues

- **Accessibility Scanner itself was not run.** The emulator's Play Store needs a signed-in Google
  account to install it. Its engine, the Accessibility Test Framework, runs on every screen in
  `AccessibilityChecksTest` and finds nothing; the Scanner's own summary screenshots wait for a run by
  hand.
- **The TalkBack walk is partial.** TalkBack ran and was captioned on the pantry list and the form,
  and the accessibility tree it reads names every row, hint and error, but a swipe-by-swipe spoken
  walk could not be driven from `adb`. It is part of the video rehearsal (Issue 36).
- **The undo Snackbar does not survive a rotation.** The delete has happened, so after a rotation the
  item has to be added again. Recorded with its reason in the checklist.
- **One unexplained device failure.** While gating Issue 31, one API 35 run failed one test of 138 and
  its result file was overwritten before it was read. It did not come back in five full runs
  afterwards or in the ten-run loop of the UI flows; it is named here rather than called fixed.
- **The alert's timing is WorkManager's.** It runs about once a day at a time the system picks, as
  the brief needs; there is no exact time setting.
