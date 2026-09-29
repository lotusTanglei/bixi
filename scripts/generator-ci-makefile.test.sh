#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
MAKEFILE="${ROOT}/Makefile"

fail() {
	printf 'Generator CI Makefile contract failed: %s\n' "$1" >&2
	exit 1
}

target_definition() {
	target="$1"
	awk -v target="${target}" '
		$0 ~ "^" target ":" { found = 1; print; next }
		found && /^[^[:space:]#][^:]*:/ { exit }
		found { print }
		END { if (!found) exit 1 }
	' "${MAKEFILE}"
}

assert_phony() {
	target="$1"
	awk -v target="${target}" '
		/^\.PHONY:/ {
			for (i = 2; i <= NF; i++) {
				if ($i == target) found = 1
			}
		}
		END { exit found ? 0 : 1 }
	' "${MAKEFILE}" || fail "${target} must be declared phony"
}

assert_phony frontend-deps
assert_phony generator-ci

frontend_definition="$(target_definition frontend-ci)" || fail 'frontend-ci target is missing'
case "${frontend_definition}" in
	*'frontend-ci: frontend-deps'*) ;;
	*) fail 'frontend-ci must reuse frontend-deps' ;;
esac
case "${frontend_definition}" in
	*'npm ci'*) fail 'frontend-ci must not run a second npm ci' ;;
	*) ;;
esac

generator_definition="$(target_definition generator-ci)" || fail 'generator-ci target is missing'
case "${generator_definition}" in
	*'generator-ci: frontend-deps'*) ;;
	*) fail 'generator-ci must install frontend dependencies through frontend-deps' ;;
esac

maven_line="$(printf '%s\n' "${generator_definition}" | awk '/\$\(MVN\).*bixi-module\/bixi-generator.*-am.*test/ { print NR; exit }')"
ui_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/test-generator-parent-child-ui\.test\.mjs/ { print NR; exit }')"
syntax_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/test-generated-parent-child-frontend\.test\.mjs/ { print NR; exit }')"
import_export_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/test-generated-import-export-frontend\.test\.mjs/ { print NR; exit }')"
generator_ui_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/test-generator-ui\.mjs/ { print NR; exit }')"
single_output_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/test-generator-output\.mjs/ { print NR; exit }')"
acceptance_support_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/generator-acceptance-support\.test\.mjs/ { print NR; exit }')"
migration_contract_line="$(printf '%s\n' "${generator_definition}" | awk '/node --test scripts\/test-generator-migration\.test\.mjs/ { print NR; exit }')"

[ -n "${maven_line}" ] || fail 'generator-ci must run the full generator Maven test suite'
[ -n "${ui_line}" ] || fail 'generator-ci must run the generator UI behavior tests'
[ -n "${syntax_line}" ] || fail 'generator-ci must validate Java-generated frontend syntax'
[ -n "${import_export_line}" ] || fail 'generator-ci must validate generated import/export frontend behavior'
[ -n "${generator_ui_line}" ] || fail 'generator-ci must validate generator mutation and fixed-source UI contracts'
[ -n "${single_output_line}" ] || fail 'generator-ci must validate generated single-table frontend output'
[ -n "${acceptance_support_line}" ] || fail 'generator-ci must validate bounded Generator acceptance helpers'
[ -n "${migration_contract_line}" ] || fail 'generator-ci must validate the generator migration harness contract'
[ "${maven_line}" -lt "${ui_line}" ] || fail 'Maven tests must run before generator UI tests'
[ "${maven_line}" -lt "${syntax_line}" ] || fail 'Maven tests must produce fixtures before syntax validation'
[ "${maven_line}" -lt "${import_export_line}" ] || fail 'Maven tests must produce import/export fixtures before frontend validation'
[ "${maven_line}" -lt "${generator_ui_line}" ] || fail 'Maven tests must run before generator fixed-source UI contracts'
[ "${maven_line}" -lt "${single_output_line}" ] || fail 'Maven tests must produce single-table fixtures before frontend validation'

ci_gate="$(target_definition ci-gate)" || fail 'ci-gate target is missing'
case "${ci_gate}" in
	*'ci-gate:'*'generator-ci'*) ;;
	*) fail 'ci-gate must include generator-ci' ;;
esac

printf 'Generator CI Makefile contract tests passed.\n'
