#!/bin/sh

set -eu

fail() {
    echo "CI artifact error: $*" >&2
    exit 1
}

action="${1:-}"
mode="${2:-}"
case "${action}" in
    create|verify) ;;
    *) fail 'usage: ci-artifacts.sh create|verify cloud|single' ;;
esac
case "${mode}" in
    cloud|single) ;;
    *) fail 'usage: ci-artifacts.sh create|verify cloud|single' ;;
esac

script_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
root="${CI_PROJECT_DIR:-${script_root}}"
root="$(CDPATH= cd -- "${root}" && pwd)"
metadata_dir="target/ci-artifacts/${mode}"
manifest="${metadata_dir}/manifest.env"
checksums="${metadata_dir}/SHA256SUMS"

commit="${CI_COMMIT_SHA:-}"
printf '%s\n' "${commit}" | grep -Eq '^[0-9a-fA-F]{7,64}$' \
    || fail 'CI_COMMIT_SHA must be a Git commit ID'

project_version="${BIXI_PROJECT_VERSION:-}"
if [ -z "${project_version}" ]; then
    command -v mvn >/dev/null 2>&1 || fail 'mvn is required to resolve the project version'
    project_version="$(cd "${root}" && mvn -q -B -N -Dstyle.color=never \
        -DforceStdout -Dexpression=project.version help:evaluate)"
fi
printf '%s\n' "${project_version}" | grep -Eq '^[0-9A-Za-z][0-9A-Za-z._+-]*$' \
    || fail 'project version is empty or invalid'

digest() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$@"
    else
        command -v shasum >/dev/null 2>&1 || fail 'sha256sum or shasum is required'
        shasum -a 256 "$@"
    fi
}

check_digests() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum --check --strict "$1"
    else
        command -v shasum >/dev/null 2>&1 || fail 'sha256sum or shasum is required'
        shasum -a 256 --check "$1"
    fi
}

list_artifacts() {
    if [ "${mode}" = 'single' ]; then
        [ -d bixi-single/target ] || fail 'bixi-single/target is missing'
        find bixi-single/target -maxdepth 1 -type f -name '*.jar' -print
    else
        find . \
            \( -path './.git' -o -path './.m2' -o -path './bixi-ui/node_modules' -o -path './target/ci-artifacts' \) -prune \
            -o -type f -name '*.jar' -print \
            | awk -F/ '$(NF - 1) == "target" { path = $0; if (substr(path, 1, 2) == "./") path = substr(path, 3); print path }'
    fi | LC_ALL=C sort
}

write_manifest() {
    destination="$1"
    count="$2"
    {
        printf 'commit=%s\n' "${commit}"
        printf 'project_version=%s\n' "${project_version}"
        printf 'mode=%s\n' "${mode}"
        printf 'artifact_count=%s\n' "${count}"
    } > "${destination}"
}

cd "${root}"
mkdir -p "${metadata_dir}"

if [ "${action}" = 'create' ]; then
    artifact_list="$(mktemp "${TMPDIR:-/tmp}/bixi-ci-artifacts.XXXXXX")"
    trap 'rm -f "${artifact_list}" "${manifest}.tmp" "${checksums}.tmp"' EXIT HUP INT TERM
    list_artifacts > "${artifact_list}"

    artifact_count="$(wc -l < "${artifact_list}" | tr -d ' ')"
    [ "${artifact_count}" -gt 0 ] || fail "no ${mode} jars were produced by the verified build"
    write_manifest "${manifest}.tmp" "${artifact_count}"
    mv "${manifest}.tmp" "${manifest}"
    {
        digest "${manifest}"
        while IFS= read -r artifact; do
            digest "${artifact}"
        done < "${artifact_list}"
    } > "${checksums}.tmp"
    mv "${checksums}.tmp" "${checksums}"
    exit 0
fi

[ -s "${manifest}" ] || fail "missing ${manifest}"
[ -s "${checksums}" ] || fail "missing ${checksums}"
checksum_count="$(wc -l < "${checksums}" | tr -d ' ')"
[ "${checksum_count}" -gt 1 ] || fail 'checksum list contains no jars'
artifact_count="$((checksum_count - 1))"
expected_manifest="$(mktemp "${TMPDIR:-/tmp}/bixi-ci-manifest.XXXXXX")"
expected_artifacts="$(mktemp "${TMPDIR:-/tmp}/bixi-ci-expected.XXXXXX")"
current_artifacts="$(mktemp "${TMPDIR:-/tmp}/bixi-ci-current.XXXXXX")"
trap 'rm -f "${expected_manifest}" "${expected_artifacts}" "${current_artifacts}"' EXIT HUP INT TERM
write_manifest "${expected_manifest}" "${artifact_count}"
cmp -s "${expected_manifest}" "${manifest}" \
    || fail 'artifact manifest does not match this commit, version, mode, and checksum list'

awk -v manifest="${manifest}" -v mode="${mode}" '
    NR == 1 { if ($2 != manifest) exit 1; next }
    mode == "single" && $2 !~ "^bixi-single/target/[^/]+[.]jar$" { exit 1 }
    mode == "cloud" && $2 !~ "/target/[^/]+[.]jar$" { exit 1 }
    END { if (NR < 2) exit 1 }
' "${checksums}" || fail 'checksum list contains an unexpected artifact path'

awk 'NR > 1 { print $2 }' "${checksums}" > "${expected_artifacts}"
list_artifacts > "${current_artifacts}"
cmp -s "${expected_artifacts}" "${current_artifacts}" \
    || fail 'publishable artifact set does not match the verified build'

check_digests "${checksums}" || fail 'artifact checksum verification failed'
