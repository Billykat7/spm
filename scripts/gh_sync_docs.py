#!/usr/bin/env python3
"""Keeps GitHub's milestones and issues in step with docs/GITHUB (Issue 7).

Each docs/GITHUB/MILESTONES/M<n>_*.md is GitHub milestone n ("Milestone n: <title>", described by
its "In short" line), and each docs/GITHUB/ISSUES/M<n>/ISSUE_<N>_*.md is GitHub issue #N in
milestone n: its title, its labels (from the spec's Area / Rubric row) and its body (the spec, with
relative links made absolute and without its closing "Closes #N" line).

What a run does:
  (no option)       creates what is missing, in numeric order, and touches nothing that exists. An
                    issue is only created when GitHub's next number is its own, so #N is always
                    ISSUE_N_*.md; issues and pull requests share numbering, and the script refuses
                    rather than create #46 for ISSUE_8.
  --check           compares every milestone and issue with its file; exit 1 on any difference
  --update-bodies   also rewrites existing issues and milestones to match their files: title, body,
                    milestone, and any spec label they are missing (labels added on GitHub by hand,
                    such as a STATUS, are kept)
  --dry-run         prints what would be created or updated, changes nothing

Run gh_sync_labels.py first: the issues use its labels.
"""

from __future__ import annotations

import argparse
import posixpath
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import spm_github as gh
from gh_sync_labels import LABELS_FILE, parse_labels

MILESTONES_DIR = gh.GITHUB_DOCS / "MILESTONES"
ISSUES_DIR = gh.GITHUB_DOCS / "ISSUES"

ISSUE_FILE = re.compile(r"^ISSUE_(\d+)_[a-z0-9_]+\.md$")
MILESTONE_FILE = re.compile(r"^M(\d+)_[a-z0-9_]+\.md$")
RELATIVE_LINK = re.compile(r"\]\((?!https?://|mailto:|#)([^)\s]+)\)")
AREA_RUBRIC_ROW = re.compile(r"^\| \*\*Area\*\* / \*\*Rubric\*\* \| (?P<cell>.+?) \|\s*$", re.MULTILINE)


@dataclass(frozen=True)
class MilestoneSpec:
    """A milestone as its doc describes it."""

    number: int
    title: str
    description: str
    path: Path


@dataclass(frozen=True)
class IssueSpec:
    """An issue as its spec file describes it, rendered for GitHub."""

    number: int
    milestone: int
    title: str
    labels: frozenset[str]
    body: str
    path: Path


def rel(path: Path) -> str:
    """The path relative to the repository root, for messages."""
    return str(path.relative_to(gh.REPO_ROOT))


def render_body(text: str, path: Path, slug: str) -> str:
    """Turns a spec file into the issue body GitHub shows.

    Relative links are resolved against the spec's folder and pointed at the file on main, keeping
    any #anchor; the closing "Closes #N" line is dropped, because in an issue it would be noise.
    """
    folder = path.parent.relative_to(gh.REPO_ROOT).as_posix()

    def absolute(match: re.Match[str]) -> str:
        target, _, anchor = match.group(1).partition("#")
        resolved = posixpath.normpath(posixpath.join(folder, target)) if target else ""
        url = f"https://github.com/{slug}/blob/main/{resolved}" if resolved else ""
        return f"]({url}{'#' + anchor if anchor else ''})"

    lines = text.rstrip().splitlines()
    if lines and re.fullmatch(r"(Closes|Fixes) #\d+", lines[-1].strip()):
        lines = lines[:-1]
    return RELATIVE_LINK.sub(absolute, "\n".join(lines)).rstrip()


def read_milestones() -> list[MilestoneSpec]:
    """Reads every milestone doc, checking that they are numbered 1..n without gaps."""
    specs = []
    for path in sorted(MILESTONES_DIR.glob("M*.md")):
        match = MILESTONE_FILE.match(path.name)
        if not match:
            continue
        number = int(match.group(1))
        text = path.read_text(encoding="utf-8")
        heading = re.match(rf"^# Milestone {number}: (.+)$", text, re.MULTILINE)
        in_short = re.search(r"^> \*\*In short:\*\* (.+)$", text, re.MULTILINE)
        if not heading or not in_short:
            gh.fail(f"{rel(path)}: needs '# Milestone {number}: <title>' and a '> **In short:**' line")
        specs.append(MilestoneSpec(number, f"Milestone {number}: {heading.group(1).strip()}",
                                   in_short.group(1).strip(), path))
    specs.sort(key=lambda m: m.number)
    if [m.number for m in specs] != list(range(1, len(specs) + 1)):
        gh.fail(f"{rel(MILESTONES_DIR)}: milestones must be numbered 1..n, found {[m.number for m in specs]}")
    return specs


def read_issues(slug: str, known_labels: set[str]) -> list[IssueSpec]:
    """Reads every issue spec, checking numbering, titles and labels before anything touches GitHub."""
    specs = []
    for path in sorted(ISSUES_DIR.glob("M*/ISSUE_*.md")):
        match = ISSUE_FILE.match(path.name)
        folder = re.fullmatch(r"M(\d+)", path.parent.name)
        if not match or not folder:
            gh.fail(f"{rel(path)}: expected docs/GITHUB/ISSUES/M<n>/ISSUE_<N>_<slug>.md")
        number = int(match.group(1))
        text = path.read_text(encoding="utf-8")
        heading = re.match(rf"^# (Issue {number}: .+)$", text, re.MULTILINE)
        if not heading:
            gh.fail(f"{rel(path)}: the first heading must be '# Issue {number}: <title>'")

        row = AREA_RUBRIC_ROW.search(text)
        if not row:
            gh.fail(f"{rel(path)}: missing the '| **Area** / **Rubric** |' row")
        area, _, rest = row.group("cell").partition("·")
        labels = set(re.findall(r"`([^`]+)`", rest))
        unknown = labels - known_labels
        if unknown:
            gh.fail(f"{rel(path)}: labels not in labels.yml: {', '.join(sorted(unknown))}")
        # The area is a word ("Matching"); it is a label only when labels.yml has "AREA: <word>"
        if f"AREA: {area.strip()}" in known_labels:
            labels.add(f"AREA: {area.strip()}")

        specs.append(IssueSpec(number, int(folder.group(1)), heading.group(1).strip(),
                               frozenset(labels), render_body(text, path, slug), path))
    specs.sort(key=lambda s: s.number)
    if [s.number for s in specs] != list(range(1, len(specs) + 1)):
        gh.fail(f"{rel(ISSUES_DIR)}: issues must be numbered 1..N without gaps")
    return specs


def normal(text: str | None) -> str:
    """Body text as compared: GitHub may return CRLF line endings and trailing whitespace."""
    return (text or "").replace("\r\n", "\n").rstrip()


def next_number() -> int:
    """The number GitHub will give the next issue or pull request (they share one sequence)."""
    latest = gh.api("repos/{owner}/{repo}/issues?state=all&sort=created&direction=desc&per_page=1")
    return (latest[0]["number"] + 1) if latest else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--check", action="store_true", help="exit 1 if GitHub differs from the docs")
    parser.add_argument("--update-bodies", action="store_true", help="rewrite existing items to match the docs")
    parser.add_argument("--dry-run", action="store_true", help="print the plan, change nothing")
    args = parser.parse_args()

    slug = gh.repo_slug()
    known_labels = {label.name for label in parse_labels(LABELS_FILE)}
    milestone_specs = read_milestones()
    issue_specs = read_issues(slug, known_labels)
    if any(spec.milestone not in {m.number for m in milestone_specs} for spec in issue_specs):
        gh.fail("an issue spec sits in a milestone folder with no milestone doc")

    remote_milestones = {m["number"]: m for m in gh.api_list("repos/{owner}/{repo}/milestones?state=all&per_page=100")}
    remote_issues = {
        i["number"]: i for i in gh.api_list("repos/{owner}/{repo}/issues?state=all&per_page=100")
        if "pull_request" not in i
    }
    print(f"docs: {len(milestone_specs)} milestones, {len(issue_specs)} issues; "
          f"GitHub ({slug}): {len(remote_milestones)} milestones, {len(remote_issues)} issues")

    problems: list[tuple[str, str, str]] = []   # (what, which, difference)
    actions: list[tuple[str, object]] = []      # (verb, spec)

    for spec in milestone_specs:
        remote = remote_milestones.get(spec.number)
        if remote is None:
            problems.append(("milestone", str(spec.number), "missing on GitHub"))
            actions.append(("create milestone", spec))
            continue
        diffs = [field for field, ours, theirs in (
            ("title", spec.title, remote["title"]),
            ("description", spec.description, normal(remote.get("description"))),
        ) if ours != theirs]
        if diffs:
            problems.append(("milestone", str(spec.number), ", ".join(diffs) + " differ"))
            actions.append(("update milestone", spec))

    for spec in issue_specs:
        remote = remote_issues.get(spec.number)
        if remote is None:
            problems.append(("issue", f"#{spec.number}", "missing on GitHub"))
            actions.append(("create issue", spec))
            continue
        remote_labels = {label["name"] for label in remote["labels"]}
        diffs = []
        if remote["title"] != spec.title:
            diffs.append(f"title is {remote['title']!r}")
        if (remote.get("milestone") or {}).get("number") != spec.milestone:
            diffs.append("milestone")
        if spec.labels - remote_labels:
            diffs.append("missing labels " + ", ".join(sorted(spec.labels - remote_labels)))
        if normal(remote.get("body")) != spec.body:
            diffs.append("body")
        if diffs:
            problems.append(("issue", f"#{spec.number}", "; ".join(diffs)))
            actions.append(("update issue", spec))

    if problems:
        gh.print_table(problems)
    else:
        print("  every milestone and issue matches its file")
    if args.check:
        return 1 if problems else 0

    todo = [(verb, spec) for verb, spec in actions if verb.startswith("create") or args.update_bodies]
    skipped = len(actions) - len(todo)
    if skipped:
        print(f"  {skipped} existing item(s) differ; left alone (run with --update-bodies to rewrite them)")
    if not todo:
        print("nothing to create" + ("" if args.update_bodies else " or update"))
        return 0
    if args.dry_run:
        for verb, spec in todo:
            print(f"  would {verb} {getattr(spec, 'title')}")
        return 0

    titles = {m.number: m.title for m in milestone_specs}
    for verb, spec in todo:
        if verb == "create milestone":
            made = gh.api("repos/{owner}/{repo}/milestones", "POST",
                          {"title": spec.title, "description": spec.description})
            if made["number"] != spec.number:
                gh.fail(f"created {spec.title!r} as milestone {made['number']}, not {spec.number}; fix by hand")
            print(f"  created milestone {spec.number}")
        elif verb == "update milestone":
            gh.api(f"repos/{{owner}}/{{repo}}/milestones/{spec.number}", "PATCH",
                   {"title": spec.title, "description": spec.description})
            print(f"  updated milestone {spec.number}")
        elif verb == "create issue":
            expected = next_number()
            if expected != spec.number:
                gh.fail(f"cannot create {spec.title!r} as #{spec.number}: GitHub's next number is "
                        f"#{expected}. Stopping so issue numbers keep matching their spec files.")
            args_ = ["issue", "create", "--title", spec.title, "--body-file", "-",
                     "--milestone", titles[spec.milestone]]
            for label in sorted(spec.labels):
                args_ += ["--label", label]
            url = gh.gh(args_, stdin=spec.body).strip()
            if not url.endswith(f"/issues/{spec.number}"):
                gh.fail(f"GitHub created {url} for {rel(spec.path)}; expected #{spec.number}")
            print(f"  created #{spec.number}")
        elif verb == "update issue":
            args_ = ["issue", "edit", str(spec.number), "--title", spec.title, "--body-file", "-",
                     "--milestone", titles[spec.milestone]]
            for label in sorted(spec.labels):
                args_ += ["--add-label", label]
            gh.gh(args_, stdin=spec.body)
            print(f"  updated #{spec.number}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
