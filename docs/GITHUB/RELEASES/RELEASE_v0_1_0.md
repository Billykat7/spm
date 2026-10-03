# Release v0.1.0: Foundation & Local CI

**Date:** 2026-10-03 · **Milestone:** M1 · **Issues closed:** 1–7

The first tagged build: a Java Android app that installs, opens on three tabs and does nothing else
yet, inside a repository that checks every change the same way on a laptop and on GitHub. There is
no pantry, no recipe and no database in this release; those are Milestones 2 to 5.

## What shipped

- **The project** (Issue 1, pull requests #39 and #40): an Android Studio *Empty Views Activity*
  project in Java 17 with Groovy build scripts, package and application id `com.btk.spm`, minSdk 26,
  target and compile SDK 35, ViewBinding on, and a package skeleton for the data layer, the engine and
  the screens. Gradle 9.8.0 runs on a pinned JetBrains Runtime 25.
- **The theme** (Issue 2, #41): one Material 3 theme, `Theme.Spm`, in light and dark from a leaf-green
  and amber palette; one type scale (`TextAppearance.Spm.*`); spacing, touch-target and corner sizes;
  every visible string in `strings.xml`, with a typed string failing Lint; the app's own adaptive
  launcher icon with a themed-icon layer.
- **The navigation shell** (Issue 3, #44): `MainActivity` hosts a bottom navigation that swaps the
  Pantry, Recipes and Settings fragments, keeps the selected tab across rotation, returns Back to
  Pantry, and opens on any tab through `MainActivity.intentFor(Context, Tab)`. Debug builds log every
  lifecycle callback under the `Lifecycle` tag; release builds log nothing.
- **The gate** (Issue 4, #42): `scripts/ci-local.sh` runs the scope guards (no Kotlin, no location
  permission or feature, no maps, location, places or billing dependency, no location or maps API),
  Lint, the unit tests and the debug build, and `--with-device` adds the instrumented tests. GitHub
  Actions runs the same script on every pull request and on `main`, and `main` requires it to pass.
- **Releases** (Issue 5, #45): pushing a `v*.*.*` tag runs the gate on the tagged commit, builds the
  release APK with the tag's version (`versionName` 0.1.0, `versionCode` 100) and publishes it as
  `spm-v0.1.0.apk`. This note is its body.
- **The shared kernel** (Issue 6, #43): the `Unit` and `UnitKind` enums with decision 5's factors,
  `Quantity`, `MatchStatus`, `IntentKeys`, `PrefKey`, `ValidationResult` and `FieldError`, and a
  conventions test that fails the build on a typed unit, status, Intent extra or preference key.
- **Repository tooling** (Issue 7, #46): scripts that sync labels, milestones and issues from the plan
  and draw the progress bars from GitHub's issue states; issue and pull request templates.

At this tag: 70 JVM unit tests and 7 instrumented tests (run on API 26 and API 35 emulators), all
passing; Lint reports no error.

## Database

- None yet. Room arrives in Milestone 2 (`v0.2.0`); this build stores nothing.

## Upgrade notes

- First release: install `spm-v0.1.0.apk` from the release page (`adb install spm-v0.1.0.apk`, or
  open the file on a phone that allows installs from files).
- The APK is signed with the debug keystore of the GitHub runner that built it (no release key: brief
  §3.3). Android refuses it as an update to a debug build installed from Android Studio
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): uninstall that build first.

## Known issues

- **Every tab is a placeholder.** The pantry list, the recipes and the settings arrive in Milestones
  3, 5 and 6.
- **The next release will not install over this one.** Each release is signed by a fresh runner's
  debug key, so `v0.2.0` and later need this build uninstalled first. A shared debug keystore would fix
  it, but the repository does not hold signing material today; that is a decision still to make.
- **Links into the plan are dead on GitHub.** The issue specs, milestone docs and IDE rules under
  `docs/GITHUB` and `docs/IDE` stay on the maintainer's machine, so the links to them from the GitHub
  issues and from `docs/guideline.md` lead nowhere on github.com.
- **Two Lint warnings remain.** `androidx.appcompat` 1.8.0 is available (1.7.1 is used), and the
  `spacing_xs` dimension is not used yet.
- **Instrumented tests run only on a laptop.** CI has no emulator, so the device tests are run with
  `./scripts/ci-local.sh --with-device` before a pull request is merged, and recorded in its
  description.
