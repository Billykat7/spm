#!/usr/bin/env bash
#
# package_submission.sh: builds the assignment ZIP from a release tag, the demo video and the report
# (Issue 37), or refuses with a one-line reason rather than produce a wrong ZIP.
#
# Usage: scripts/package_submission.sh --tag <tag> --video <path> --report <path> [--out <dir>]
#
#   --tag     the release tag; the source is `git archive --prefix=spm-source/` of that tag, so the
#             ZIP holds exactly the tagged commit and never build/, .gradle/, local.properties or
#             .submission.env
#   --video   the demo video, copied in as spm_demo.mp4; must be 300 to 420 seconds by ffprobe
#   --report  the report, copied in as <StudentNumber>_<Surname>_MobileAppDev700_Assignment.docx;
#             its text or links must contain github.com/Billykat7/spm
#   --out     where the ZIP goes (default: build/submission under the repository root); inside the
#             repository it must be a git-ignored path
#
# STUDENT_NUMBER and SURNAME come from the environment, or else from the git-ignored .submission.env
# at the repository root (lines STUDENT_NUMBER=... and SURNAME=...). The student number is never
# committed. The checks run in this order and the first failure ends the run: arguments, student
# number and surname, tag, paths, report link, ffprobe and video length, ZIP build, ZIP size (an
# oversize ZIP is deleted). On success it prints a manifest of the ZIP.
#
# Exit codes: 0 packaged, 1 a check failed, 2 bad usage.
#
# Plain Bash that runs under macOS's Bash 3.2 and Linux's Bash 5; needs git, zip, unzip, awk and
# ffprobe (brew install ffmpeg, or apt-get install ffmpeg).

set -euo pipefail

readonly REPO_LINK='github.com/Billykat7/spm'
readonly MIN_SECONDS=300
readonly MAX_SECONDS=420
readonly MAX_BYTES=52428800   # 50 MB, the submission limit
readonly PROG='package_submission.sh'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
    printf '%s: %s\n' "$PROG" "$1" >&2
    exit 1
}

usage_fail() {
    printf '%s: %s\n' "$PROG" "$1" >&2
    printf 'usage: %s --tag <tag> --video <path> --report <path> [--out <dir>]\n' "$PROG" >&2
    exit 2
}

need() {
    command -v "$1" >/dev/null 2>&1 || fail "$1 not found: $2"
}

# Absolute path of an existing directory.
abs_dir() {
    (cd "$1" && pwd)
}

# --- 1. Arguments -------------------------------------------------------------------------------

tag=''
video=''
report=''
out="$ROOT/build/submission"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tag|--video|--report|--out)
            [[ $# -ge 2 && -n "$2" ]] || usage_fail "$1 needs a value"
            case "$1" in
                --tag) tag="$2" ;;
                --video) video="$2" ;;
                --report) report="$2" ;;
                --out) out="$2" ;;
            esac
            shift 2
            ;;
        -h|--help)
            sed -n '3,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            usage_fail "unknown argument: $1"
            ;;
    esac
done

[[ -n "$tag" ]] || usage_fail "--tag is required"
[[ -n "$video" ]] || usage_fail "--video is required"
[[ -n "$report" ]] || usage_fail "--report is required"

# --- 2. Student number and surname --------------------------------------------------------------

# Reads KEY from .submission.env (KEY=value, optional `export `, optional quotes); empty if absent.
env_file_value() {
    local key="$1" file="$ROOT/.submission.env" line value=''
    [[ -f "$file" ]] || return 0
    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line#export }"
        case "$line" in
            "$key="*)
                value="${line#*=}"
                value="${value%\"}"; value="${value#\"}"
                value="${value%\'}"; value="${value#\'}"
                ;;
        esac
    done < "$file"
    printf '%s' "$value"
}

student_number="${STUDENT_NUMBER:-}"
surname="${SURNAME:-}"
[[ -n "$student_number" ]] || student_number="$(env_file_value STUDENT_NUMBER)"
[[ -n "$surname" ]] || surname="$(env_file_value SURNAME)"

[[ -n "$student_number" ]] || fail "STUDENT_NUMBER is not set: export it or put it in .submission.env"
[[ -n "$surname" ]] || fail "SURNAME is not set: export it or put it in .submission.env"
[[ "$student_number" =~ ^[0-9]+$ ]] || fail "STUDENT_NUMBER must be digits only"
[[ "$surname" =~ ^[A-Za-z][A-Za-z-]*$ ]] || fail "SURNAME must be letters and hyphens only"

base="${student_number}_${surname}_MobileAppDev700_Assignment"

# --- 3. Tag -------------------------------------------------------------------------------------

need git "install git"
git -C "$ROOT" rev-parse -q --verify "refs/tags/$tag^{commit}" >/dev/null \
    || fail "tag $tag does not exist in this repository"

# --- 4. Paths -----------------------------------------------------------------------------------

[[ -f "$video" ]] || fail "video not found: $video"
[[ -f "$report" ]] || fail "report not found: $report"
[[ -s "$video" ]] || fail "video is empty: $video"

out_existed=0
[[ -d "$out" ]] && out_existed=1
mkdir -p "$out" || fail "cannot create the output directory: $out"
out="$(abs_dir "$out")"
case "$out/" in
    "$ROOT"/*)
        # The repository's own ignore rules decide, not a personal global one such as *.zip.
        git -C "$ROOT" -c core.excludesFile=/dev/null check-ignore -q "$out/.probe" \
            || { [[ $out_existed -eq 1 ]] || rmdir "$out"; fail "output directory $out is inside the repository but not git-ignored"; }
        ;;
esac

# --- 5. Report link -----------------------------------------------------------------------------

need unzip "install unzip"
unzip -l "$report" word/document.xml >/dev/null 2>&1 \
    || fail "report is not a DOCX (no word/document.xml): $report"
# The text, and the hyperlink targets kept in the relationships part. grep without -q reads to the
# end, so unzip is never cut off by a closed pipe.
if ! unzip -p "$report" word/document.xml 'word/_rels/document.xml.rels' 2>/dev/null \
        | grep -F "$REPO_LINK" >/dev/null; then
    fail "report does not contain $REPO_LINK: $report"
fi

# --- 6. Video length ----------------------------------------------------------------------------

need ffprobe "brew install ffmpeg"
duration="$(ffprobe -v error -show_entries format=duration \
    -of default=noprint_wrappers=1:nokey=1 "$video" 2>/dev/null || true)"
[[ "$duration" =~ ^[0-9]+(\.[0-9]+)?$ ]] || fail "ffprobe could not read the length of $video"
awk -v d="$duration" -v lo="$MIN_SECONDS" -v hi="$MAX_SECONDS" 'BEGIN { exit !(d >= lo && d <= hi) }' \
    || fail "video is ${duration}s, outside ${MIN_SECONDS}-${MAX_SECONDS}s: $video"

# --- 7. Build the ZIP ---------------------------------------------------------------------------

need zip "install zip"
zip_path="$out/$base.zip"
stage="$out/.stage.$$"
packaged=0
# The stage always goes; the ZIP goes too unless every check below passed.
trap 'rm -rf "$stage"; [[ $packaged -eq 1 ]] || rm -f "$zip_path"' EXIT
rm -rf "$stage" "$zip_path"
mkdir "$stage"

git -C "$ROOT" archive --format=zip --prefix=spm-source/ -o "$zip_path" "$tag" \
    || fail "git archive of $tag failed"
cp "$video" "$stage/spm_demo.mp4"
cp "$report" "$stage/$base.docx"
# The video and the DOCX are compressed already, so they are stored as they are.
(cd "$stage" && zip -q -X -n .mp4:.docx "$zip_path" spm_demo.mp4 "$base.docx") \
    || fail "adding the video and the report to the ZIP failed"

# --- 8. Size ------------------------------------------------------------------------------------

zip_bytes="$(wc -c < "$zip_path" | tr -d ' ')"
if [[ "$zip_bytes" -gt "$MAX_BYTES" ]]; then
    fail "ZIP is $zip_bytes bytes, over the $MAX_BYTES-byte (50 MB) limit; deleted it"
fi
packaged=1

# --- 9. Manifest --------------------------------------------------------------------------------

printf 'Packaged %s from tag %s\n\n' "$zip_path" "$tag"
printf '%-60s %14s %14s\n' 'Top-level entry' 'Bytes' 'In ZIP'
# unzip -v columns: Length Method Size Cmpr Date Time CRC-32 Name; names may contain spaces.
unzip -v "$zip_path" | awk '
    $1 ~ /^[0-9]+$/ && $7 ~ /^[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]$/ && NF >= 8 {
        name = $8
        for (i = 9; i <= NF; i++) name = name " " $i
        top = name; sub(/\/.*/, "/", top)
        if (!(top in len)) order[++n] = top
        len[top] += $1; cmp[top] += $3
    }
    END { for (i = 1; i <= n; i++) printf "%-60s %14d %14d\n", order[i], len[order[i]], cmp[order[i]] }
'
awk -v b="$zip_bytes" -v max="$MAX_BYTES" 'BEGIN {
    printf "\nZIP total: %d bytes, %.2f MB; headroom to 50 MB: %.2f MB\n", b, b / 1048576, (max - b) / 1048576
}'
