#!/usr/bin/env bash
#
# ci-local.sh: the gate every change passes before it is pushed, and the exact script CI runs on
# every pull request and on main (.github/workflows/ci.yml). A red CI should never be a surprise.
#
# Stages, in order, stopping at the first failure:
#   1. guards       scripts/check_guards.sh: no Kotlin, no maps, location or billing (brief 2.3, 3.1, 3.3)
#   2. lint         ./gradlew lint                       (Android Lint; HardcodedText is an error)
#   3. unit tests   ./gradlew testDebugUnitTest          (JUnit on the JVM)
#   4. debug build  ./gradlew assembleDebug              (app/build/outputs/apk/debug/app-debug.apk)
#   5. device tests ./gradlew connectedDebugAndroidTest  (only with --with-device; needs an emulator)
#
# Usage:
#   ./scripts/ci-local.sh                  stages 1-4
#   ./scripts/ci-local.sh --with-device    stages 1-5; set ANDROID_SERIAL to pick one device
#
# Exit status: 0 when every stage passed, the failing stage's status otherwise, 2 on bad usage or a
# missing Android SDK.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

usage() {
    sed -n '/^# Usage:/,/^# Exit status/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'
}

with_device=0
for arg in "$@"; do
    case "$arg" in
        --with-device) with_device=1 ;;
        -h | --help) usage; exit 0 ;;
        *) printf 'ci-local: unknown option %s\n\n' "$arg" >&2; usage >&2; exit 2 ;;
    esac
done

# The Gradle build needs the SDK location: ANDROID_HOME (CI runners set it) or local.properties.
if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]] && ! grep -qs '^sdk\.dir=' local.properties; then
    printf 'ci-local: Android SDK not found. Export ANDROID_HOME (macOS default: %s)\n' \
        "$HOME/Library/Android/sdk" >&2
    printf '          or open the project once in Android Studio, which writes local.properties.\n' >&2
    exit 2
fi

gradle=(./gradlew --console=plain)
total=$((with_device ? 5 : 4))
stage_no=0
results=()
gate_start=$SECONDS

print_summary() {
    local verdict="$1"
    printf '\n==> ci-local summary\n'
    printf '    %s\n' "${results[@]}"
    printf '    gate %s in %ds\n' "$verdict" "$((SECONDS - gate_start))"
    # On GitHub Actions, the same table on the run's summary page.
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        {
            printf '### ci-local: gate %s in %ds\n\n```text\n' "$verdict" "$((SECONDS - gate_start))"
            printf '%s\n' "${results[@]}"
            printf '```\n'
        } >> "$GITHUB_STEP_SUMMARY"
    fi
}

# stage <name> <command...>: runs one stage, records "OK|FAIL name (time)", stops the gate on failure.
stage() {
    local name="$1"
    shift
    stage_no=$((stage_no + 1))
    printf '\n==> [%d/%d] %s: %s\n' "$stage_no" "$total" "$name" "$*"
    local start=$SECONDS
    "$@"
    local status=$?
    local line
    line="$(printf '%-4s %-12s %4ds' "$([[ $status -eq 0 ]] && echo OK || echo FAIL)" "$name" "$((SECONDS - start))")"
    results+=("$line")
    printf '==> %s\n' "$line"
    if [[ $status -ne 0 ]]; then
        print_summary "FAILED at $name"
        exit "$status"
    fi
}

# The device stage needs at least one device in the "device" state (ANDROID_SERIAL's, if set).
device_tests() {
    local adb="adb"
    [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]] && adb="$ANDROID_HOME/platform-tools/adb"
    local devices
    devices="$("$adb" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')"
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        devices="$(printf '%s\n' "$devices" | grep -Fx "$ANDROID_SERIAL")"
    fi
    if [[ -z "$devices" ]]; then
        printf 'ci-local: no device for the instrumented tests%s; boot an emulator first.\n' \
            "${ANDROID_SERIAL:+ (ANDROID_SERIAL=$ANDROID_SERIAL)}" >&2
        return 1
    fi
    printf 'devices: %s\n' "$(printf '%s' "$devices" | tr '\n' ' ')"
    "${gradle[@]}" connectedDebugAndroidTest
}

stage guards ./scripts/check_guards.sh
stage lint "${gradle[@]}" lint
stage "unit tests" "${gradle[@]}" testDebugUnitTest
stage "debug build" "${gradle[@]}" assembleDebug
if [[ $with_device -eq 1 ]]; then
    stage "device tests" device_tests
fi

print_summary PASSED
