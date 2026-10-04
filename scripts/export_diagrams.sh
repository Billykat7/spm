#!/usr/bin/env bash
# Renders the Mermaid block of each docs/REPORT/diagrams/*.md to <name>.svg and <name>.png next to
# it, with mermaid-cli (mmdc) run through npx. The .md is the source of truth; the images are derived
# and committed, so the report build needs no Node.
#
# Usage: scripts/export_diagrams.sh
# Needs Node (npx) and a Chrome for puppeteer: PUPPETEER_EXECUTABLE_PATH if set, else the installed
# Google Chrome on macOS, else puppeteer's own (npx puppeteer browsers install chrome-headless-shell).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
diagrams="$root/docs/REPORT/diagrams"
mmdc=(npx -y @mermaid-js/mermaid-cli@12.0.0)

mac_chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
if [ -z "${PUPPETEER_EXECUTABLE_PATH:-}" ] && [ -x "$mac_chrome" ]; then
    export PUPPETEER_EXECUTABLE_PATH="$mac_chrome"
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

shopt -s nullglob
sources=("$diagrams"/*.md)
if [ "${#sources[@]}" -eq 0 ]; then
    echo "No diagram sources in $diagrams" >&2
    exit 1
fi

for source in "${sources[@]}"; do
    name="$(basename "$source" .md)"
    mmd="$work/$name.mmd"
    # The lines between the first ```mermaid fence and the fence that closes it
    awk '/^```mermaid[[:space:]]*$/ { inside = 1; next }
         inside && /^```[[:space:]]*$/ { exit }
         inside { print }' "$source" > "$mmd"
    if [ ! -s "$mmd" ]; then
        echo "Skipping $name.md: no mermaid block" >&2
        continue
    fi

    echo "Rendering $name"
    "${mmdc[@]}" -q -i "$mmd" -o "$diagrams/$name.svg" -b white
    # mmdc 12 has no -w; -s 3 renders at three times the pixel density, over 1600 px wide, for print
    "${mmdc[@]}" -q -i "$mmd" -o "$diagrams/$name.png" -b white -s 3
done
