#!/usr/bin/env python3
"""Writes the milestone progress bars from GitHub's issue states (Issue 7).

One block per issue, green for closed, green first: `🟩🟩⬜⬜⬜ **40%** (2/5 issues)`. Pull requests
are never counted. The bars live in three places, and this script owns them there (and nothing
else; the Status text beside each bar is written by hand):

  docs/GITHUB/MILESTONES/M<n>_*.md   the "| **Progress** |" row
  docs/GITHUB/README.md              each milestone's row in the Milestone summary, and the
                                     "**Progress:**" line under it
  README.md                          each milestone's row in Delivery at a glance, and the ⭐ row

Usage:
  python scripts/milestone_progress.py                     rewrite the bars from GitHub
  python scripts/milestone_progress.py --assume-closed 14  ...counting #14 as closed: the bars as
                                                           they will read once this branch merges
  python scripts/milestone_progress.py --check [...]       exit 1 if any bar differs; write nothing
  python scripts/milestone_progress.py --close-completed   close GitHub milestones whose issues
                                                           are all closed (run after the merge)
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import spm_github as gh

DONE, OPEN = "🟩", "⬜"
BLOCKS = f"[{DONE}{OPEN}]+"
MILESTONE_DOCS = gh.GITHUB_DOCS / "MILESTONES"
SUMMARY_DOC = gh.GITHUB_DOCS / "README.md"
ROOT_README = gh.REPO_ROOT / "README.md"


@dataclass(frozen=True)
class Progress:
    """How many of a set of issues are closed, and the bar that shows it."""

    closed: int
    total: int

    @property
    def percent(self) -> int:
        """Closed over total as a whole percentage, halves rounded up, in exact integer arithmetic."""
        return (200 * self.closed + self.total) // (2 * self.total) if self.total else 0

    def blocks(self) -> str:
        return DONE * self.closed + OPEN * (self.total - self.closed)

    def short(self) -> str:
        """`🟩⬜ **50%**`, as the Milestone summary shows it."""
        return f"{self.blocks()} **{self.percent}%**"

    def full(self) -> str:
        """`🟩⬜ **50%** (1/2 issues)`, as the milestone docs and the root README show it."""
        return f"{self.short()} ({self.closed}/{self.total} issues)"


def read_github(assume_closed: set[int]) -> tuple[dict[int, Progress], Progress, int, dict[int, list[int]]]:
    """Reads every milestone's issues from GitHub.

    Returns the progress per milestone number, the progress over all tracked issues, the number of
    milestones whose issues are all closed, and each milestone's still-open issue numbers. Issues in
    `assume_closed` count as closed; each must belong to a milestone, so a typo cannot pass silently.
    """
    milestones = {m["number"]: m for m in gh.api_list("repos/{owner}/{repo}/milestones?state=all&per_page=100")}
    issues = [i for i in gh.api_list("repos/{owner}/{repo}/issues?state=all&per_page=100")
              if "pull_request" not in i and i.get("milestone")]
    tracked = {i["number"] for i in issues}
    stray = sorted(assume_closed - tracked)
    if stray:
        gh.fail(f"--assume-closed: {', '.join(f'#{n}' for n in stray)} is in no milestone")

    per: dict[int, Progress] = {}
    still_open: dict[int, list[int]] = {}
    for number in sorted(milestones):
        own = [i for i in issues if i["milestone"]["number"] == number]
        closed = [i for i in own if i["state"] == "closed" or i["number"] in assume_closed]
        per[number] = Progress(len(closed), len(own))
        still_open[number] = sorted(i["number"] for i in own if i not in closed)
    total = Progress(sum(p.closed for p in per.values()), sum(p.total for p in per.values()))
    done = sum(1 for p in per.values() if p.total and p.closed == p.total)
    return per, total, done, still_open


def replace_once(text: str, pattern: str, replacement, where: str) -> str:
    """Substitutes `pattern` exactly once, or stops naming what could not be found."""
    new, count = re.subn(pattern, replacement, text, flags=re.MULTILINE)
    if count != 1:
        gh.fail(f"{where}: expected exactly one match, found {count} (pattern {pattern!r})")
    return new


def render(per: dict[int, Progress], total: Progress, done: int) -> dict[Path, str]:
    """Returns every file this script owns, with its bars redrawn."""
    files: dict[Path, str] = {}

    for number, progress in per.items():
        docs = sorted(MILESTONE_DOCS.glob(f"M{number}_*.md"))
        if len(docs) != 1:
            gh.fail(f"milestone {number}: expected one docs/GITHUB/MILESTONES/M{number}_*.md, found {len(docs)}")
        text = docs[0].read_text(encoding="utf-8")
        files[docs[0]] = replace_once(
            text, r"^(\| \*\*Progress\*\* \| ).*?( \|)$",
            lambda m, p=progress: f"{m.group(1)}{p.full()}{m.group(2)}", docs[0].name)

    summary = SUMMARY_DOC.read_text(encoding="utf-8")
    for number, progress in per.items():
        summary = replace_once(
            summary, rf"^(\| \*\*\[M{number}: .*\| ){BLOCKS} \*\*\d+%\*\*",
            lambda m, p=progress: f"{m.group(1)}{p.short()}", f"{SUMMARY_DOC.name}, M{number} row")
    summary = replace_once(
        summary, rf"^\*\*Progress:\*\* {BLOCKS} \*\*\d+%\*\* \(\d+/\d+ issues\) closed · \*\*\d+ of \d+ milestones done\*\*",
        lambda m: f"**Progress:** {total.full()} closed · **{done} of {len(per)} milestones done**",
        f"{SUMMARY_DOC.name}, Progress line")
    files[SUMMARY_DOC] = summary

    readme = ROOT_README.read_text(encoding="utf-8")
    for number, progress in per.items():
        readme = replace_once(
            readme, rf"^(\| {number} \| \[[^]]+\]\(https://github\.com/[^)]+/milestone/{number}\).*\| ){BLOCKS} \*\*\d+%\*\* \(\d+/\d+ issues\)( \|)$",
            lambda m, p=progress: f"{m.group(1)}{p.full()}{m.group(2)}", f"README.md, milestone {number} row")
    readme = replace_once(
        readme, rf"^(\| ⭐ \|.*\| ){BLOCKS} \*\*\d+%\*\* \(\d+/\d+ issues\)( \|)$",
        lambda m: f"{m.group(1)}{total.full()}{m.group(2)}", "README.md, ⭐ row")
    files[ROOT_README] = readme
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--assume-closed", default="",
                        help="comma-separated issue numbers to count as closed, e.g. 14 or 14,15")
    parser.add_argument("--check", action="store_true", help="exit 1 if any bar differs; write nothing")
    parser.add_argument("--close-completed", action="store_true",
                        help="close GitHub milestones whose issues are all closed")
    args = parser.parse_args()

    try:
        assume = {int(n) for n in args.assume_closed.replace("#", "").split(",") if n.strip()}
    except ValueError:
        gh.fail(f"--assume-closed takes issue numbers such as 14 or 14,15, not {args.assume_closed!r}")

    if args.close_completed:
        if assume:
            gh.fail("--close-completed reads GitHub as it is; run it after the merge, without --assume-closed")
        per, _, _, still_open = read_github(set())
        open_milestones = {m["number"] for m in gh.api_list("repos/{owner}/{repo}/milestones?state=open&per_page=100")}
        closed_any = False
        for number, progress in per.items():
            if number in open_milestones and progress.total and not still_open[number]:
                gh.api(f"repos/{{owner}}/{{repo}}/milestones/{number}", "PATCH", {"state": "closed"})
                print(f"closed milestone {number} ({progress.total}/{progress.total} issues closed)")
                closed_any = True
        if not closed_any:
            print("no open milestone has all its issues closed")
        return 0

    per, total, done, _ = read_github(assume)
    label = f" (assuming {', '.join(f'#{n}' for n in sorted(assume))} closed)" if assume else ""
    print(f"GitHub{label}:")
    gh.print_table([(f"M{n}", p.full()) for n, p in per.items()] + [("all", f"{total.full()}, {done} of {len(per)} milestones done")])

    changed = []
    for path, new in render(per, total, done).items():
        old = path.read_text(encoding="utf-8")
        if new == old:
            continue
        changed.append(path)
        for before, after in zip(old.splitlines(), new.splitlines()):
            if before != after:
                print(f"  {path.relative_to(gh.REPO_ROOT)}:\n    - {before.strip()[:150]}\n    + {after.strip()[:150]}")
        if not args.check:
            path.write_text(new, encoding="utf-8")

    if args.check:
        print("bars agree with GitHub" + label if not changed else f"{len(changed)} file(s) disagree with GitHub{label}")
        return 1 if changed else 0
    print("bars already up to date" if not changed else f"rewrote {len(changed)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
