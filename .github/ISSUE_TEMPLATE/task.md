---
name: Task
about: A planned piece of work, in the same shape as the project's issue specs.
title: "Issue <N>: <imperative summary>"
labels: []
---

<!--
The planned issues (#1 to #38) are created from their spec files by scripts/gh_sync_docs.py, which
keeps this shape. Use this template for work the plan did not foresee, and keep the headings: the
pull request that closes it copies the acceptance criteria and ticks each one with its evidence.
-->

> **In short:** <What the app gains when this merges, in one or two plain sentences.>

| | |
|---|---|
| **Milestone** | <M<n>: name> |
| **Area** / **Rubric** | <Area> · `RUBRIC: <criterion>` |
| **Estimate** | <days> |
| **Depends on** | <issues that must be merged first, or "Nothing"> |
| **Brief** | <the section(s) of the brief this satisfies> |

## Context

<Why the issue exists: the problem, in the app's own terms.>

## Starting point

<What already exists to build on, and any recorded decision that affects it.>

## Scope

- <What to build>

## Out of scope

- <What not to build here, and which issue does it instead>

## Acceptance criteria

- [ ] <A checkable statement a reviewer can tick>

## How to verify

1. <The commands or taps that show it working>

## Files touched

- <Where the change is expected to land>

## Evidence for the report

- <A screenshot, a snippet, a diagram or a challenge this issue contributes>
