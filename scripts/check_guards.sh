#!/usr/bin/env bash
#
# check_guards.sh: the brief's hard restrictions, checked by a machine (Issue 4).
#
#   1. Java only (brief 3.1, decision 2): no Kotlin source under app/, no Kotlin build script anywhere.
#   2. No location (brief 2.3, 3.3): no location permission or location <uses-feature> in any manifest.
#   3. No maps, location, places, payments or billing SDK in any Gradle file or the version catalog.
#   4. No android.location or Google Play maps/location reference in the app's sources or layouts.
#
# Every offence is printed as "file:line: text" under the guard it breaks, all guards run, and the
# script exits 1 if any guard failed, 0 otherwise. It only reads files, so it is safe to run anywhere;
# ./scripts/ci-local.sh runs it first, locally and in CI.
#
# Usage: scripts/check_guards.sh            (from any directory)

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

failed=0

# report <guard name> <why it is forbidden> <offending lines, one per line>
report() {
    local name="$1" why="$2" hits="$3"
    if [[ -n "$hits" ]]; then
        printf 'guard FAILED: %s\n  %s\n' "$name" "$why"
        printf '%s\n' "$hits" | sed 's/^/    /'
        failed=1
    else
        printf 'guard ok:     %s\n' "$name"
    fi
}

# Files the guards read, never generated output: build/, .gradle/ and .git/ are skipped.
find_sources() {
    find "$@" \( -name build -o -name .gradle -o -name .git \) -prune -o -type f -print 2>/dev/null
}

# Guard 1: Kotlin. The app is marked as Java (brief 3.1); the build scripts are Groovy (decision 2).
kotlin_files="$(
    find_sources app | grep -E '\.kts?$'
    find_sources . | grep -E '\.gradle\.kts$' | sed 's|^\./||'
)"
report "no Kotlin" \
    "The app is Java only and the build scripts are Groovy; delete or convert these files:" \
    "$(printf '%s\n' "$kotlin_files" | sed '/^$/d' | sort -u)"

# Guard 2: location permissions and features, in every manifest of every source set.
manifests="$(find_sources app/src | grep -E '/AndroidManifest\.xml$')"
location_manifest=""
if [[ -n "$manifests" ]]; then
    # shellcheck disable=SC2086 # one path per line, no spaces in this repository's paths
    location_manifest="$(grep -nHE \
        'ACCESS_(FINE|COARSE|BACKGROUND)_LOCATION|android\.hardware\.location' $manifests)"
fi
report "no location permission or feature" \
    "The app must not read the device's location (brief 2.3); remove these manifest entries:" \
    "$location_manifest"

# Guard 3: maps, location, places and payment dependencies, wherever Gradle can declare one.
gradle_files="$(find_sources . | grep -E '\.gradle(\.kts)?$|/libs\.versions\.toml$' | sed 's|^\./||')"
forbidden_dependency='play-services-(maps|location|places|wallet)|com\.google\.android\.libraries\.places|com\.google\.maps|maps-(compose|utils|ktx)|mapbox|osmdroid|billingclient|[:"'"'"']billing[:"'"'"'-]|com\.google\.android\.gms:(.*-)?wallet'
location_dependency=""
if [[ -n "$gradle_files" ]]; then
    # shellcheck disable=SC2086
    location_dependency="$(grep -nHiE "$forbidden_dependency" $gradle_files)"
fi
report "no maps, location, places or billing dependency" \
    "No mapping, location or payment SDK may be declared (brief 2.3, 3.3); remove these lines:" \
    "$location_dependency"

# Guard 4: code or layouts that reach a location or maps API, imported or fully qualified.
code_files="$(find_sources app/src | grep -E '\.(java|kt|xml)$')"
location_code=""
if [[ -n "$code_files" ]]; then
    # shellcheck disable=SC2086
    location_code="$(grep -nHE \
        '\bandroid\.location\.|\bcom\.google\.android\.gms\.(maps|location)\b' $code_files)"
fi
report "no android.location or Play maps/location API" \
    "No source or layout may use a location or maps API (brief 2.3); remove these references:" \
    "$location_code"

exit "$failed"
