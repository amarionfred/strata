#!/usr/bin/env bash
# Install the published Strata APK from Termux.

strata_repo=https://github.com/amarionfred/strata
strata_stage=
strata_config_tmp=

fail() { printf '\nStrata: %s\n' "$*" >&2; exit 1; }
cleanup() {
    [[ -z "$strata_stage" ]] || rm -rf -- "$strata_stage"
    [[ -z "$strata_config_tmp" ]] || rm -f -- "$strata_config_tmp"
}

check_phone() {
    case "${PREFIX:-}" in
        /data/data/com.termux/files/usr|/data/user/*/com.termux/files/usr) ;;
        *) fail 'Open Termux on your phone and run this script there.' ;;
    esac
    [[ "$EUID" != 0 ]] || fail 'Run this in a normal Termux session, without su or sudo.'
    local api
    api=$(getprop ro.build.version.sdk) || fail 'Cannot read the Android version.'
    [[ "$api" =~ ^[0-9]+$ ]] || fail 'Cannot read the Android version.'
    (( api >= 29 )) || fail 'Strata requires Android 10 or newer.'
}

ensure_tools() {
    local -a packages=()
    command -v curl >/dev/null || packages+=(curl)
    command -v sha256sum >/dev/null || packages+=(coreutils)
    command -v awk >/dev/null || packages+=(gawk)
    if ! command -v termux-open >/dev/null || ! command -v termux-reload-settings >/dev/null; then
        packages+=(termux-tools)
    fi
    [[ -x "$PREFIX/bin/am" ]] || packages+=(termux-am)
    if (( ${#packages[@]} )); then
        pkg install -y "${packages[@]}" || fail 'Package download failed. Check your connection and run this script again.'
    fi
}

download() {
    curl --fail --location --show-error --retry 3 --connect-timeout 20 --max-time 300 \
        --proto '=https' --proto-redir '=https' --output "$2" "$1" \
        || fail 'Download failed. Check your connection and run this script again.'
}

release_tag() {
    local url tag
    url=$(curl --fail --silent --show-error --location --head --retry 3 \
        --connect-timeout 20 --max-time 90 --proto '=https' --proto-redir '=https' \
        --output /dev/null --write-out '%{url_effective}' "$strata_repo/releases/latest") \
        || fail 'Cannot find the latest release. Check your connection and run this script again.'
    tag=${url#"$strata_repo/releases/tag/"}
    [[ "$url" == "$strata_repo/releases/tag/"* && "$tag" =~ ^strata-v[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9._-]+)?$ ]] \
        || fail 'The latest release address was unexpected. No APK was opened.'
    printf '%s\n' "$tag"
}

apk_checksum() {
    local expected
    # Read only the APK entry; never interpret checksum-file paths as local paths.
    expected=$(awk '$2 == "strata.apk" { print $1 }' "$1")
    [[ "$expected" =~ ^[a-fA-F0-9]{64}$ ]] || fail 'The release has no unique valid APK checksum.'
    printf '%s\n' "$expected"
}

apk_matches() {
    local expected="$1" actual
    [[ -f "$2" ]] || return 1
    actual=$(sha256sum "$2") || return 1
    actual=${actual%% *}
    [[ "${actual,,}" == "${expected,,}" ]]
}

verify_apk() {
    local expected
    expected=$(apk_checksum "$1")
    apk_matches "$expected" "$2" || fail 'APK checksum failed. No APK was opened. Run the same installation command again.'
}

prepare_apk() {
    local tag="$1" cache="$2" stage="$3" expected
    download "$strata_repo/releases/download/$tag/SHA256SUMS" "$stage/SHA256SUMS"
    expected=$(apk_checksum "$stage/SHA256SUMS")
    if apk_matches "$expected" "$cache/strata.apk"; then
        printf 'Using the verified download of Strata %s.\n' "${tag#strata-v}"
        return
    fi
    printf 'Downloading Strata %s…\n' "${tag#strata-v}"
    download "$strata_repo/releases/download/$tag/strata.apk" "$stage/strata.apk"
    verify_apk "$stage/SHA256SUMS" "$stage/strata.apk"
    mv -f -- "$stage/strata.apk" "$cache/strata.apk"
}

connect_termux() {
    local strata_home="$1" config source
    config="$strata_home/.termux/termux.properties"
    mkdir -p -- "$strata_home/.termux"
    if [[ -L "$config" ]]; then
        config=$(readlink -f -- "$config") || fail 'Cannot resolve your termux.properties link.'
    fi
    source=/dev/null
    [[ ! -e "$config" ]] || source=$config
    strata_config_tmp=$(mktemp "${config}.strata.XXXXXX")
    awk '
        /^[[:space:]]*allow-external-apps([[:space:]]|[:=]|$)/ {
            if (!written++) print "allow-external-apps = true"
            next
        }
        { print }
        END { if (!written) print "allow-external-apps = true" }
    ' "$source" > "$strata_config_tmp"
    if [[ -f "$config" ]] && cmp -s -- "$config" "$strata_config_tmp"; then
        rm -f -- "$strata_config_tmp"
    else
        if [[ -f "$config" ]]; then
            [[ -e "$config.strata-backup" || -L "$config.strata-backup" ]] || cp -p -- "$config" "$config.strata-backup"
            chmod --reference="$config" "$strata_config_tmp"
        fi
        mv -f -- "$strata_config_tmp" "$config"
    fi
    strata_config_tmp=
    termux-reload-settings || printf 'Reopen Termux once if its settings did not reload.\n'
}

apk_uri() {
    local path="$1" uri=content://com.termux.files char encoded i LC_ALL=C
    for ((i = 0; i < ${#path}; i++)); do
        char=${path:i:1}
        case "$char" in
            [a-zA-Z0-9/._~-]) uri+=$char ;;
            *) printf -v encoded '%%%02X' "'$char"; uri+=$encoded ;;
        esac
    done
    printf '%s\n' "$uri"
}

open_installer() {
    local apk="$1" log="$2" uri
    [[ -f "$apk" && -r "$apk" ]] || fail 'The downloaded APK is missing or unreadable. Run the same installation command again.'
    apk=$(realpath -- "$apk")
    uri=$(apk_uri "$apk")
    # INSTALL_PACKAGE excludes generic file viewers such as Termux's editor.
    # Use Termux's am; Android's /system/bin/am requires an ADB shell.
    if "$PREFIX/bin/am" start --user "$((UID / 100000))" \
        -a android.intent.action.INSTALL_PACKAGE \
        -t application/vnd.android.package-archive \
        -d "$uri" --grant-read-uri-permission > "$log" 2>&1; then
        return
    fi
    printf '\nAndroid could not open the installer directly:\n' >&2
    cat -- "$log" >&2
    printf '\nOpening the app chooser instead. Choose Package installer.\n'
    printf 'If a Save file or termux-file-editor dialog appears, close it; the APK does not need an editor.\n'
    termux-open --chooser --view --content-type application/vnd.android.package-archive "$apk" \
        || fail "Could not open Android installation. Details saved in $log."
}

main() {
    set -euo pipefail
    umask 077
    check_phone
    ensure_tools
    local cache="$HOME/.cache/strata" tag apk
    mkdir -p -- "$cache"
    strata_stage=$(mktemp -d "$cache/download.XXXXXX")
    trap cleanup EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    printf '\n1/3 Checking the latest Strata download…\n'
    tag=$(release_tag)
    prepare_apk "$tag" "$cache" "$strata_stage"
    apk="$cache/strata.apk"
    printf '\n2/3 Connecting Termux…\n'
    connect_termux "$HOME"
    printf '\n3/3 Opening Android installation…\n'
    printf 'Allow installs from Termux if asked, then tap Install or Update.\n'
    printf 'If an Open with chooser appears, select Package installer.\n'
    printf 'After installation, open Strata → Setup → Grant command permission → Allow.\n'
    printf 'For your first installation, tap Choose default home and select Strata.\n'
    printf '\nIf installation closes, return to Termux and run the same installation command again.\n'
    open_installer "$apk" "$cache/installer.log"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
