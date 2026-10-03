"""Shared helpers for the repository tooling scripts (Issue 7).

The scripts keep GitHub and docs/GITHUB saying the same thing. They run on a maintainer's laptop,
never in CI, and talk to GitHub only through the `gh` CLI, so they need no token of their own:
whatever `gh auth status` shows is what they use. Standard library only.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Iterable, Sequence

# The repository root: scripts/ sits directly under it.
REPO_ROOT = Path(__file__).resolve().parent.parent
GITHUB_DOCS = REPO_ROOT / "docs" / "GITHUB"


class GhError(RuntimeError):
    """A `gh` command failed; the message names the command and repeats its error output."""


def gh(args: Sequence[str], stdin: str | None = None) -> str:
    """Runs `gh <args>` in the repository and returns its standard output.

    Raises GhError when gh exits non-zero, so a failed GitHub call stops the script instead of being
    mistaken for an empty answer.
    """
    try:
        result = subprocess.run(
            ["gh", *args], cwd=REPO_ROOT, input=stdin, capture_output=True, text=True, check=False
        )
    except FileNotFoundError as missing:
        raise GhError("The GitHub CLI `gh` is not installed or not on PATH") from missing
    if result.returncode != 0:
        raise GhError(f"gh {' '.join(args)} failed:\n{result.stderr.strip()}")
    return result.stdout


def api(path: str, method: str = "GET", fields: dict[str, Any] | None = None) -> Any:
    """Calls the REST API once and returns the decoded JSON (None for an empty response).

    `path` may use gh's {owner}/{repo} placeholders. `fields` are sent as typed fields (-F), so
    numbers and booleans keep their JSON type.
    """
    args = ["api", "--method", method, path]
    for key, value in (fields or {}).items():
        if isinstance(value, bool):
            args += ["-F", f"{key}={'true' if value else 'false'}"]
        elif isinstance(value, int):
            args += ["-F", f"{key}={value}"]
        else:
            # -f sends the value as a string, whatever it looks like
            args += ["-f", f"{key}={value}"]
    output = gh(args)
    return json.loads(output) if output.strip() else None


def api_list(path: str) -> list[dict[str, Any]]:
    """Fetches every page of a REST list endpoint and returns all items in one list."""
    output = gh(["api", "--paginate", path, "--jq", ".[]"])
    return [json.loads(line) for line in output.splitlines() if line.strip()]


def repo_slug() -> str:
    """Returns owner/name of the repository gh resolves for this checkout."""
    return gh(["repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"]).strip()


def fail(message: str, code: int = 1) -> None:
    """Prints `message` to standard error and exits with `code`."""
    print(message, file=sys.stderr)
    sys.exit(code)


def print_table(rows: Iterable[Sequence[str]]) -> None:
    """Prints rows of text as left-aligned columns."""
    rows = [list(row) for row in rows]
    if not rows:
        return
    widths = [max(len(row[i]) for row in rows) for i in range(len(rows[0]))]
    for row in rows:
        print("  " + "  ".join(cell.ljust(width) for cell, width in zip(row, widths)).rstrip())
