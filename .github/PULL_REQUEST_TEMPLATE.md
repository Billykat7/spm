<!--
A pull request into main. The same headings as docs/GITHUB/PR/M*/PR_<N>_DESCRIPTION.md, which is
where the full description lives. That file is local and git-ignored, never committed: it becomes the
PR body through gh pr create --body-file (pr-description-file.mdc). This template is the only PR text in git.

Title: "Issue <N>: <imperative summary>". Branch: Issue/<N>/<short-slug>. Every commit: "Issue <N>: ...".
No assistant co-author trailer in any commit, and no "Generated with" footer here (docs/IDE/RULES).
-->

# PR: <short imperative title> (Issue <N> / M<MS>)

**Milestone:** [M<MS>](https://github.com/Billykat7/spm/milestones) · **Issue:** #<N>

<One paragraph, plain English: what problem this solves and what a reader should understand
before looking at the diff. Not a list of files: a reason.>

## Scope

- **In:** <what this pull request changes>
- **Out:** <what it deliberately leaves alone, and which issue has it>

## Summary

- **<Change one>:** what it does and the behaviour it produces.
- **<Change two>:** …

## Design notes

<The decisions a reviewer would otherwise have to reverse-engineer: why this approach and not the
obvious alternative, what invariant is being protected, what is deliberately out of scope. If the
change touches the matcher, say which of the non-negotiables in docs/guideline.md it keeps.>

## Changes

- **`app/src/main/java/com/btk/spm/...`:** what changed and why.

## Testing

<Evidence, not intentions: the commands you ran and what they printed (trimmed).>

- [ ] `./scripts/ci-local.sh` green (lint, unit tests, guards, debug build)
- [ ] Instrumented tests run on a device or emulator where the issue asks for them (`--with-device`)
- [ ] New tests cover every acceptance criterion on the issue
- [ ] Manual check: <what you actually did, on what data, on which device or emulator>

## Screenshots

<Required for any change under app/src/main/res/ or any Activity/Fragment: a real screenshot of the
running app, saved under docs/REPORT/screenshots/ with a caption in its index (report-evidence.mdc).
Delete this section only when no UI file changed.>

## Acceptance criteria

<Copy the checklist from the issue, and tick each item with the evidence that shows it.>

## Report notes

<Anything the written report or the video will need from this change: a snippet worth showing, a
challenge you hit and how you solved it (append it to docs/REPORT/CHALLENGES.md), a concept this
code is the example of (lifecycle, Intents, Adapter, Room, the matcher).>

## Risk and rollback

<What could break, who is affected, and how to undo this if it does.>

Closes #<N>
