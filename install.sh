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

verify_apk() {
    local expected actual
    # Read only the APK entry; never interpret checksum-file paths as local paths.
    expected=$(awk '$2 == "strata.apk" { print $1 }' "$1")
    [[ "$expected" =~ ^[a-fA-F0-9]{64}$ ]] || fail 'The release has no unique valid APK checksum.'
    actual=$(sha256sum "$2")
    actual=${actual%% *}
    [[ "${actual,,}" == "${expected,,}" ]] || fail 'APK checksum failed. No APK was opened. Run this script again.'
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
    tag=$(release_tag)
    printf '\nDownloading Strata %s…\n' "${tag#strata-v}"
    download "$strata_repo/releases/download/$tag/SHA256SUMS" "$strata_stage/SHA256SUMS"
    download "$strata_repo/releases/download/$tag/strata.apk" "$strata_stage/strata.apk"
    verify_apk "$strata_stage/SHA256SUMS" "$strata_stage/strata.apk"
    apk="$cache/strata.apk"
    mv -f -- "$strata_stage/strata.apk" "$apk"
    printf '\nAPK verified. Connecting Termux…\n'
    connect_termux "$HOME"
    printf '\n1. Allow installs from Termux if Android asks, then tap Install.\n'
    printf '2. Open Strata → Setup → Grant command permission → Allow.\n'
    printf '3. Tap Choose default home and select Strata.\n'
    printf '\nOpening Android’s installer. APK saved at %s\n' "$apk"
    printf 'If no installer appears, run: termux-open --view --content-type application/vnd.android.package-archive %q\n' "$apk"
    termux-open --view --content-type application/vnd.android.package-archive "$apk" \
        || fail 'Android’s installer could not be opened. Use the termux-open command printed above.'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
