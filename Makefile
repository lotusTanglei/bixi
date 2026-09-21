SHELL := /bin/sh
ROOT := $(CURDIR)
FRONTEND_DIR := $(ROOT)/bixi-ui
JAVA_17_HOME := $(shell if [ -x /usr/libexec/java_home ]; then /usr/libexec/java_home -v 17 2>/dev/null; fi)
JAVA_ENV := $(if $(JAVA_17_HOME),JAVA_HOME="$(JAVA_17_HOME)",)
MVN := $(JAVA_ENV) mvn

.PHONY: init-env doctor doctor-dev start-cloud start-single verify-cloud verify-single status diagnose logs stop reset credentials backend-dev backend-test backend-prod frontend-dev frontend-test frontend-prod architecture-check runtime-config-check backend-cloud-ci backend-single-ci backend-ci frontend-ci ci-gate workflow-test

init-env:
	./scripts/bixi.sh init-env

doctor:
	./scripts/bixi.sh doctor

doctor-dev:
	./scripts/bixi.sh doctor-dev

start-cloud:
	./scripts/bixi.sh start-cloud

start-single:
	./scripts/bixi.sh start-single

verify-cloud:
	BIXI_MODE=cloud node ./scripts/acceptance.mjs

verify-single:
	BIXI_MODE=single node ./scripts/acceptance.mjs

.PHONY: verify-security-cloud verify-security-single
verify-security-cloud:
	BIXI_MODE=cloud node ./scripts/security-acceptance.mjs

verify-security-single:
	BIXI_MODE=single node ./scripts/security-acceptance.mjs

status:
	./scripts/bixi.sh status

diagnose:
	./scripts/bixi.sh diagnose

logs:
	./scripts/bixi.sh logs

stop:
	./scripts/bixi.sh stop

reset:
	./scripts/bixi.sh reset

credentials:
	./scripts/bixi.sh credentials

backend-dev:
	$(MVN) -Pcloud -Dprofiles.active=dev clean compile

backend-test:
	$(MVN) -Pcloud -Dprofiles.active=test clean compile

backend-prod:
	$(MVN) -Pcloud -Dprofiles.active=prod clean compile

frontend-dev:
	cd $(FRONTEND_DIR) && npm ci && npm run build:dev

frontend-test:
	cd $(FRONTEND_DIR) && npm ci && npm run build:test

frontend-prod:
	cd $(FRONTEND_DIR) && npm ci && npm run build:prod

architecture-check:
	./scripts/verify-architecture.sh

runtime-config-check:
	./scripts/verify-runtime-config.sh

backend-cloud-ci: architecture-check runtime-config-check
	$(MVN) -Pcloud clean verify

backend-single-ci: architecture-check runtime-config-check
	$(MVN) -Psingle -pl bixi-single -am clean verify

backend-ci: backend-cloud-ci backend-single-ci

workflow-test:
	$(MVN) -Pcloud -pl bixi-module/bixi-workflow-biz,bixi-module/bixi-upms-biz,bixi-gateway -am -Dtest='Workflow*Test,Leave*Test' -Dsurefire.failIfNoSpecifiedTests=false test
	$(MVN) -Psingle -pl bixi-single -am -Dtest='Workflow*Test,Leave*Test' -Dsurefire.failIfNoSpecifiedTests=false test

.PHONY: workflow-mysql-test
workflow-mysql-test:
	bash scripts/test-workflow-mysql.sh both

.PHONY: reliable-mysql-test
reliable-mysql-test:
	bash scripts/test-reliable-mysql.sh both

frontend-ci:
	cd $(FRONTEND_DIR) && npm ci && npm run lint:eslint && npm run build:prod

ci-gate: backend-cloud-ci backend-single-ci frontend-ci
