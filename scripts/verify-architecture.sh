#!/bin/sh

set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

fail_if_matches() {
    message=$1
    shift
    matches=$("$@" || true)
    if [ -n "$matches" ]; then
        printf '%s\n%s\n' "$message" "$matches" >&2
        exit 1
    fi
}

fail_if_matches \
    "Architecture violation: common modules must not depend on business implementation modules." \
    find bixi-common -name pom.xml -type f -exec grep -Hn '<artifactId>bixi-.*-biz</artifactId>' {} +

fail_if_matches \
    "Architecture violation: API contract modules must not depend on business implementations or deployment entries." \
    find bixi-module -path '*-api/pom.xml' -type f -exec grep -EHn \
        '<artifactId>bixi-(.*-biz|single|auth|gateway|generator|monitor|quartz)</artifactId>' {} +

fail_if_matches \
    "Architecture violation: business modules must not depend on deployment entries." \
    find bixi-module -mindepth 2 -maxdepth 2 -name pom.xml -type f -exec grep -EHn \
        '<artifactId>bixi-(single|auth|gateway)</artifactId>' {} +

fail_if_matches \
    "Architecture violation: consumers must use transport-neutral UPMS and workflow contracts, not Feign types." \
    sh -c "find bixi-auth bixi-common bixi-module bixi-single -type f -path '*/src/main/java/*' -name '*.java' -exec grep -EHn 'import com.lotus.bixi.(upms|workflow).api.feign.Remote.*Service;' {} + | grep -Ev '/(upms|workflow)/api/feign/Remote'"

fail_if_matches \
    "Architecture violation: single mode must not use localhost Feign loopback." \
    grep -R -En 'https?://(localhost|127\.0\.0\.1)|127\.0\.0\.1.*server\.port|BASE_URL.*127\.0\.0\.1' \
        bixi-common/bixi-common-feign/src/main/java bixi-single/src/main/java

fail_if_matches \
    "Architecture violation: bixi-single is a composition root and must not duplicate business layers." \
    find bixi-single/src/main/java -type f \( \
        -path '*/controller/*' -o -path '*/service/*' -o -path '*/mapper/*' -o -path '*/entity/*' \
    \) -print

printf '%s\n' "Validating Maven reactor graphs for cloud and single profiles."
mvn -q -Pcloud -DskipTests validate
mvn -q -Psingle -DskipTests validate

printf '%s\n' "Architecture checks passed."
