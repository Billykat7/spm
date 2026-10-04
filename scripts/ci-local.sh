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
#   5. schema       git status --porcelain -- app/schemas/ (the build left the committed Room schema as it was)
#   6. device tests ./gradlew connectedDebugAndroidTest  (only with --with-device; needs an emulator)
#
# Stage 6 does not trust Gradle's exit status alone. Gradle can print BUILD SUCCESSFUL when the APK
# would not install on one of the devices, which then runs no test at all. So the stage uninstalls
# the app and its test APK from every target device first, and afterwards fails when the Gradle
# output says "AndroidTestRunner failed on <serial>" or when a target device has no JUnit XML with at
# least one test in it. It prints one line per device, which the summary repeats.
#
# Usage:
#   ./scripts/ci-local.sh                  stages 1-5
#   ./scripts/ci-local.sh --with-device    stages 1-6 on every attached device; ANDROID_SERIAL picks
#                                          one device, or several separated by commas
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
total=$((with_device ? 6 : 5))
stage_no=0
results=()
stage_notes=()
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
# A stage can add lines to stage_notes; the summary prints them under the stage's own line.
stage() {
    local name="$1"
    shift
    stage_no=$((stage_no + 1))
    stage_notes=()
    printf '\n==> [%d/%d] %s: %s\n' "$stage_no" "$total" "$name" "$*"
    local start=$SECONDS
    "$@"
    local status=$?
    local line note
    line="$(printf '%-4s %-12s %4ds' "$([[ $status -eq 0 ]] && echo OK || echo FAIL)" "$name" "$((SECONDS - start))")"
    results+=("$line")
    printf '==> %s\n' "$line"
    # Bash 3.2 (macOS) treats an empty "${array[@]}" as unbound under set -u, hence the + form.
    for note in ${stage_notes[@]+"${stage_notes[@]}"}; do
        results+=("     $note")
    done
    if [[ $status -ne 0 ]]; then
        print_summary "FAILED at $name"
        exit "$status"
    fi
}

# Room's annotation processor rewrites app/schemas/ on every build, so a change to an entity that keeps
# the @Database version shows up here as a modified or new file. The committed JSON is what the ER
# diagram is drawn from (Issue 34): it must never silently differ from the code (Issue 12).
schema_unchanged() {
    local changed
    if ! changed="$(git status --porcelain -- app/schemas/)"; then
        printf 'ci-local: cannot read git status for app/schemas/\n' >&2
        return 1
    fi
    if [[ -n "$changed" ]]; then
        printf '%s\n' "$changed"
        printf 'schema changed: bump @Database version and commit app/schemas/com.btk.spm.data.db.AppDatabase/<N>.json\n' >&2
        return 1
    fi
    printf 'app/schemas/ matches the commit\n'
}

adb="adb"
[[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]] && adb="$ANDROID_HOME/platform-tools/adb"
device_results="app/build/outputs/androidTest-results/connected/debug"

# device_label <serial>: "spm_api26" for an emulator (its AVD name), the model for a phone.
device_label() {
    local label
    if [[ "$1" == emulator-* ]]; then
        label="$("$adb" -s "$1" emu avd name 2>/dev/null | head -1)"
    else
        label="$("$adb" -s "$1" shell getprop ro.product.model 2>/dev/null)"
    fi
    label="$(printf '%s' "$label" | tr -d '\r')"
    printf '%s' "${label:-unknown}"
}

# The device stage needs at least one device in the "device" state: every attached one, or the ones
# ANDROID_SERIAL names (Gradle reads the same variable and accepts a comma-separated list).
device_tests() {
    local attached devices serial
    attached="$("$adb" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')"
    devices="$attached"
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        devices="$(printf '%s\n' "$ANDROID_SERIAL" | tr ',' '\n' | sed '/^$/d')"
        for serial in $devices; do
            if ! printf '%s\n' "$attached" | grep -Fxq "$serial"; then
                printf 'ci-local: %s (from ANDROID_SERIAL) is not an attached device; adb devices lists: %s\n' \
                    "$serial" "$(printf '%s' "$attached" | tr '\n' ' ')" >&2
                return 1
            fi
        done
    fi
    if [[ -z "$devices" ]]; then
        printf 'ci-local: no device for the instrumented tests; boot an emulator first.\n' >&2
        return 1
    fi
    printf 'devices: %s\n' "$(printf '%s' "$devices" | tr '\n' ' ')"

    # An APK installed by hand (adb install -r) can make Gradle's install fail with
    # INSTALL_FAILED_ALREADY_EXISTS. Gradle then skips that device and still exits 0, so both
    # packages go first. This also wipes the app's data on the device.
    local app_id pkg
    app_id="$(sed -n 's/^ *applicationId "\(.*\)"/\1/p' app/build.gradle)"
    if [[ -z "$app_id" ]]; then
        printf 'ci-local: no applicationId in app/build.gradle\n' >&2
        return 1
    fi
    for serial in $devices; do
        for pkg in "$app_id" "$app_id.test"; do
            if "$adb" -s "$serial" shell pm list packages "$pkg" 2>/dev/null | tr -d '\r' | grep -Fxq "package:$pkg"; then
                printf 'uninstall %s from %s: %s\n' "$pkg" "$serial" \
                    "$("$adb" -s "$serial" uninstall "$pkg" 2>&1 | tr -d '\r' | tail -1)"
            fi
        done
    done

    # A result left by an earlier run must not count for this one.
    rm -rf "$device_results"
    local log gradle_status
    log="$(mktemp "${TMPDIR:-/tmp}/ci-local-device.XXXXXX")" || return 2
    "${gradle[@]}" connectedDebugAndroidTest 2>&1 | tee "$log"
    gradle_status=${PIPESTATUS[0]}

    # Every target device must have a JUnit XML that names its serial and holds at least one test.
    printf '\n==> tests per device (%s)\n' "$device_results"
    local failed=0 runner_failed xml counts tests failures errors line
    runner_failed="$(grep -o 'AndroidTestRunner failed on [^ ]*' "$log" | sed 's/.* on //; s/[^[:alnum:]]*$//' | sort -u)"
    rm -f "$log"
    for serial in $devices; do
        xml="$(grep -lF "<property name=\"device\" value=\"$serial\" />" "$device_results"/TEST-*.xml 2>/dev/null | head -1)"
        if [[ -n "$xml" ]]; then
            counts="$(sed -n 's/.*<testsuites tests="\([0-9]*\)" failures="\([0-9]*\)" errors="\([0-9]*\)".*/\1 \2 \3/p' "$xml" | head -1)"
            read -r tests failures errors <<< "${counts:-0 0 0}"
            line="$(printf '%s %s: %d tests, %d failures, %d errors' "$serial" \
                "$(basename "$xml" .xml | sed 's/^TEST-//')" "$tests" "$failures" "$errors")"
            if [[ $tests -eq 0 ]]; then
                line="FAIL $line"
                failed=1
            elif [[ $failures -ne 0 || $errors -ne 0 ]]; then
                line="FAIL $line"
            else
                line="OK   $line"
            fi
        else
            line="$(printf 'FAIL %s %s: no test results' "$serial" "$(device_label "$serial")")"
            failed=1
        fi
        if printf '%s\n' "$runner_failed" | grep -Fxq "$serial"; then
            [[ "$line" == FAIL* ]] || line="FAIL ${line#OK   }"
            line="$line (AndroidTestRunner failed on $serial)"
            failed=1
        fi
        printf '    %s\n' "$line"
        stage_notes+=("$line")
    done
    # A serial the runner failed on that is not a target (it should not happen) still fails the stage.
    for serial in $runner_failed; do
        if ! printf '%s\n' "$devices" | grep -Fxq "$serial"; then
            line="FAIL $serial: AndroidTestRunner failed on $serial"
            printf '    %s\n' "$line"
            stage_notes+=("$line")
            failed=1
        fi
    done

    if [[ $gradle_status -ne 0 ]]; then
        return "$gradle_status"
    fi
    if [[ $failed -ne 0 ]]; then
        printf 'ci-local: Gradle passed, but a device above ran no tests. Its install or runner error is in the Gradle output.\n' >&2
        return 1
    fi
}

stage guards ./scripts/check_guards.sh
stage lint "${gradle[@]}" lint
stage "unit tests" "${gradle[@]}" testDebugUnitTest
stage "debug build" "${gradle[@]}" assembleDebug
stage schema schema_unchanged
if [[ $with_device -eq 1 ]]; then
    stage "device tests" device_tests
fi

print_summary PASSED
