#!/usr/bin/env python3
"""Makes GitHub's issue labels equal to docs/GITHUB/LABELS/labels.yml (Issue 7).

labels.yml is the single source of truth: edit it, then run this script; never edit labels on
GitHub directly. Every label in the file is created on GitHub, or updated when its colour or
description differs. Labels on GitHub that the file does not have are listed and, only with
--prune, deleted. Running it twice changes nothing the second time.

Usage:
  python scripts/gh_sync_labels.py              create and update; list labels the file lacks
  python scripts/gh_sync_labels.py --prune      also delete the labels the file lacks
  python scripts/gh_sync_labels.py --dry-run    print the plan, change nothing
  python scripts/gh_sync_labels.py --check      exit 1 if GitHub differs from the file
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

import spm_github as gh

LABELS_FILE = gh.GITHUB_DOCS / "LABELS" / "labels.yml"
COLOUR = re.compile(r"^[0-9a-fA-F]{6}$")
# "- name: "AREA: UI"" starts an entry; "  color: "bfd4f2"" continues it. Values are double-quoted.
LINE = re.compile(r'^(?P<dash>- |  )(?P<key>[a-z]+):\s*"(?P<value>(?:[^"\\]|\\.)*)"\s*$')


@dataclass(frozen=True)
class Label:
    """One label: its name, its colour as six lower-case hex digits, and its description."""

    name: str
    color: str
    description: str


def parse_labels(path: Path) -> list[Label]:
    """Reads labels.yml.

    Accepts exactly the shape the file uses (a list of name/color/description entries with
    double-quoted values, comments and blank lines) and stops on anything else, naming the line,
    rather than guessing; the file is too important to half-read.
    """
    labels: list[Label] = []
    current: dict[str, str] | None = None

    def close_entry(line_no: int) -> None:
        if current is None:
            return
        name = current.get("name", "")
        color = current.get("color", "").lower()
        if not name:
            gh.fail(f"{path}:{line_no}: a label has no name")
        if not COLOUR.match(color):
            gh.fail(f"{path}:{line_no}: label {name!r} needs a 6-digit hex color, has {color!r}")
        labels.append(Label(name, color, current.get("description", "")))

    for line_no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        match = LINE.match(raw)
        if not match or match["key"] not in ("name", "color", "description"):
            gh.fail(f"{path}:{line_no}: cannot read this line: {raw.strip()}")
        value = match["value"].replace('\\"', '"').replace("\\\\", "\\")
        if match["dash"] == "- ":
            if match["key"] != "name":
                gh.fail(f"{path}:{line_no}: an entry must start with its name")
            close_entry(line_no)
            current = {}
        elif current is None:
            gh.fail(f"{path}:{line_no}: a field before the first '- name:'")
        current[match["key"]] = value
    close_entry(line_no=0)

    names = [label.name.lower() for label in labels]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        gh.fail(f"{path}: duplicate label names: {', '.join(duplicates)}")
    return labels


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--prune", action="store_true", help="delete labels the file does not have")
    parser.add_argument("--dry-run", action="store_true", help="print the plan, change nothing")
    parser.add_argument("--check", action="store_true", help="exit 1 if GitHub differs from the file")
    args = parser.parse_args()

    wanted = parse_labels(LABELS_FILE)
    remote = {
        item["name"].lower(): Label(item["name"], item["color"].lower(), item.get("description") or "")
        for item in gh.api_list("repos/{owner}/{repo}/labels?per_page=100")
    }

    create, update, same = [], [], []
    for label in wanted:
        existing = remote.get(label.name.lower())
        if existing is None:
            create.append(label)
        elif existing != label:
            update.append((existing, label))
        else:
            same.append(label)
    wanted_names = {label.name.lower() for label in wanted}
    extra = sorted((label for key, label in remote.items() if key not in wanted_names), key=lambda label: label.name)

    print(f"{LABELS_FILE.relative_to(gh.REPO_ROOT)}: {len(wanted)} labels; GitHub ({gh.repo_slug()}): {len(remote)}")
    print(f"  unchanged {len(same)}, to create {len(create)}, to update {len(update)}, only on GitHub {len(extra)}")
    rows = [("create", label.name, label.color, label.description) for label in create]
    for old, new in update:
        changes = []
        if old.name != new.name:
            changes.append(f"name {old.name!r}")
        if old.color != new.color:
            changes.append(f"color {old.color} -> {new.color}")
        if old.description != new.description:
            changes.append("description")
        rows.append(("update", new.name, new.color, ", ".join(changes)))
    rows += [("prune" if args.prune else "extra", label.name, label.color, "not in labels.yml") for label in extra]
    gh.print_table(rows)

    in_sync = not create and not update and not extra
    if args.check:
        print("labels in sync" if in_sync else "labels differ from labels.yml")
        return 0 if in_sync else 1
    if args.dry_run:
        return 0

    for label in create + [new for _, new in update]:
        gh.gh(["label", "create", label.name, "--color", label.color,
               "--description", label.description, "--force"])
        # --force on an existing label also renames a case-only difference (Ui -> UI)
    if args.prune:
        for label in extra:
            gh.api(f"repos/{{owner}}/{{repo}}/labels/{quote(label.name, safe='')}", method="DELETE")
    elif extra:
        print(f"  {len(extra)} label(s) exist only on GitHub; run with --prune to delete them")
    print("done" if (create or update or (extra and args.prune)) else "nothing to change")
    return 0


if __name__ == "__main__":
    sys.exit(main())
