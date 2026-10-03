---
name: Bug
about: Something in the app or the tooling does not do what its issue or the brief says.
title: "Bug: <what goes wrong, in a few words>"
labels: ["TYPE: Bug"]
---

## What happens

<What you saw, in one or two sentences.>

## What should happen

<What the app or the script should do instead, and where that is written down: the issue number, a
non-negotiable in docs/guideline.md, or a section of the brief.>

## Steps to reproduce

1. <From a fresh install or a named state: "pantry holds 500 g flour and 2 eggs">
2.
3.

## Where

- **Build:** <debug from a commit (`git rev-parse --short HEAD`), or a release such as `spm-v0.1.0.apk`>
- **Device:** <emulator AVD and API level, or phone model and Android version>

## Evidence

<A screenshot, the logcat lines (`adb logcat -s AndroidRuntime Lifecycle`), or the failing gate
stage from `./scripts/ci-local.sh`. Real output, trimmed; no paraphrase.>

## Area

<Which part of the app: Pantry, Recipes, Matching, Database, UI, Settings, Notifications, Build/CI or
Evidence. Add the matching `AREA:` label.>
