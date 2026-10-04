# Release v0.7.0: Evidence, Report & Submission

**Date:** 2026-10-04 · **Milestone:** M7 · **Issues closed:** 33–37

This release adds the evidence, not features. It brings the report's screenshots and diagrams, the
written report's source and how to build it, the video's script, and the script that packages the
submission. **No app code changed since `v0.6.0`:** `git diff v0.6.0 v0.7.0 -- app/` prints nothing,
so the APK attached here behaves exactly as `v0.6.0`'s.

## What shipped

- **The report's screenshots** (Issue 33). A *Report set* at the top of
  [`docs/REPORT/screenshots/INDEX.md`](../../REPORT/screenshots/INDEX.md) lists 16 shots, each with a
  one-sentence caption and the issue that took it:
  - every screen: the pantry list, the add and edit forms, Suggested Recipes, the recipe detail and Settings;
  - every core function: a validation error, delete and Undo, the expiry badges, four of five ingredients against five, the zero-match state, the almost-there section, a setting changing a match, the permission prompt and the notification.

  They were chosen from the working copies taken during M3 to M6 (1080 px, API 35, light theme), not
  re-taken on one build.
- **The design diagrams** (Issue 34). Both are drawn in Mermaid, so GitHub renders them and a diff
  shows any change. [`screen_flow.md`](../../REPORT/diagrams/screen_flow.md) has one node per real
  class and one edge per Intent, tab or notification between them.
  [`er_diagram.md`](../../REPORT/diagrams/er_diagram.md) has the three tables exactly as schema
  version 1 exports them. `scripts/export_diagrams.sh` renders each to SVG and PNG, and both images
  are committed so the report build needs no Node. The script that would fail the gate when the ER
  diagram drifts from the schema was cut from scope.
- **The written report's source** (Issue 35). [`docs/REPORT/REPORT.md`](../../REPORT/REPORT.md) has
  the brief's nine sections. [`docs/REPORT/README.md`](../../REPORT/README.md) gives the pandoc command
  that builds the DOCX with the student number filled in from the git-ignored `.submission.env`. The
  student number is in no committed file.
- **The video script** (Issue 36). [`docs/DEMO/VIDEO_SCRIPT.md`](../../DEMO/VIDEO_SCRIPT.md) covers
  the brief's four parts in 6:00:
  - the GitHub walkthrough;
  - the live app, with the create, read, update and delete cycle and the match proof;
  - three concepts at the code: strict matching, Room end to end, and Intents, each with its file and line range;
  - the database justification.

  It also has the recording checklist and the `ffmpeg` and `ffprobe` checks. Video files under
  `docs/DEMO/` are git-ignored.
- **The submission package** (Issue 37). `scripts/package_submission.sh --tag <tag> --video <path>
  --report <path>` is plain Bash 3.2 and `shellcheck` clean. It checks the names, the tag, both
  paths, the repository link in the DOCX, the video's length (300–420 s) and the 50 MB limit. Each
  failure stops it with a one-line reason, and it never leaves a ZIP behind. The source is
  `git archive` of the tag, so `build/`, `.gradle/` and `local.properties` cannot get in.

## Database

- Room schema version 1, unchanged since `v0.2.0`. No migration.

## Upgrade notes

- Nothing changes on the device. `spm-v0.7.0.apk` is `v0.6.0`'s app, and, as before, it will not
  install over an earlier release's APK (each release is signed by a fresh runner's debug key).
- The submission tools need `pandoc` and `ffmpeg` (`brew install pandoc ffmpeg`).

## Known issues

- **The video length check has not run on a real video yet.** `ffprobe` was not installed when the
  package script was written, so its duration check was proven with a stand-in; every other check
  ran for real.
- **The report source is a draft to be rewritten in the author's own words before export**
  (brief §9).
- **The report set was not re-taken on one build.** The 16 shots come from the debug builds of
  Issues 17 to 31; each row names its issue.
