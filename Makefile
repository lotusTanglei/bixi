SHELL := /bin/sh
ROOT := $(CURDIR)
FRONTEND_DIR := $(ROOT)/bixi-ui
JAVA_17_HOME ?= $(shell if [ -x /usr/libexec/java_home ]; then /usr/libexec/java_home -v 17 2>/dev/null; fi)
JAVA_ENV := $(if $(JAVA_17_HOME),JAVA_HOME="$(JAVA_17_HOME)",)
MVN := $(JAVA_ENV) mvn

.PHONY: init-env doctor doctor-dev start-cloud start-single verify-cloud verify-single status diagnose logs stop reset credentials backend-dev backend-test backend-prod frontend-dev frontend-test frontend-prod frontend-deps architecture-check runtime-config-check backend-cloud-ci backend-single-ci backend-ci frontend-ci generator-ci generator-migration-test quartz-migration-test quartz-cloud-runtime-config-test quartz-jdbc-failover-static-test quartz-jdbc-failover-test ci-gate workflow-test workflow-cluster-config workflow-process-restart-static-test workflow-process-restart-test local-process-restart-static-test local-process-restart-test workflow-cluster-static-test workflow-cluster-failover-test workflow-schema-migrate phase2-schema-migrate phase2-migration-list reliable-rabbit-static-test reliable-rabbit-test sba-multi-instance-static-test full-application-restart-static-test local-test-preflight local-test-preflight-static-test

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

frontend-deps:
	cd $(FRONTEND_DIR) && npm ci

architecture-check:
	$(JAVA_ENV) ./scripts/verify-architecture.sh

runtime-config-check:
	./scripts/verify-runtime-config.sh

local-test-preflight-static-test:
	node --test scripts/test-local-test-preflight.test.mjs scripts/test-local-resource-preflight.test.mjs scripts/test-disposable-test-preflight.test.mjs
	bash -n scripts/local-test-preflight.sh

local-test-preflight:
	bash scripts/local-test-preflight.sh $(if $(BIXI_LOCAL_TEST_MODE),$(BIXI_LOCAL_TEST_MODE),single)

backend-cloud-ci: architecture-check runtime-config-check
	$(MVN) -Pcloud clean verify

backend-single-ci: architecture-check runtime-config-check
	$(MVN) -Psingle -pl bixi-single -am clean verify

backend-ci: backend-cloud-ci backend-single-ci

workflow-test:
	$(MVN) -Pcloud -pl bixi-module/bixi-workflow-biz,bixi-module/bixi-upms-biz,bixi-gateway -am -Dtest='Workflow*Test,Leave*Test' -Dsurefire.failIfNoSpecifiedTests=false test
	$(MVN) -Psingle -pl bixi-single -am -Dtest='Workflow*Test,Leave*Test' -Dsurefire.failIfNoSpecifiedTests=false test

workflow-cluster-config:
	bash scripts/verify-workflow-cluster-config.sh

workflow-process-restart-static-test:
	node --test scripts/test-workflow-process-restart.test.mjs
	bash -n scripts/test-workflow-process-restart.sh

workflow-process-restart-test: workflow-process-restart-static-test
	bash scripts/test-workflow-process-restart.sh

local-process-restart-static-test:
	node --test scripts/test-local-process-restart.test.mjs
	bash -n scripts/test-local-process-restart.sh

local-process-restart-test: local-process-restart-static-test
	bash scripts/test-local-process-restart.sh

full-application-restart-static-test:
	node --test scripts/test-full-application-restart.test.mjs
	bash -n scripts/test-full-application-restart.sh

workflow-cluster-static-test:
	node --test scripts/workflow-cluster-failover.test.mjs scripts/workflow-performance-metrics.test.mjs scripts/test-workflow-business-occurrence-schema.mjs
	node --check scripts/workflow-cluster-failover.mjs
	node --check scripts/workflow-performance-metrics.mjs
	bash -n scripts/test-workflow-cluster-failover.sh scripts/migrate-workflow-schema.sh

workflow-cluster-failover-test: workflow-cluster-static-test
	bash scripts/test-workflow-cluster-failover.sh

workflow-schema-migrate:
	bash scripts/migrate-workflow-schema.sh

phase2-migration-list:
	bash scripts/migrate-workflow-schema.sh --list --scope phase2

phase2-schema-migrate:
	BIXI_MIGRATION_SCOPE=phase2 bash scripts/migrate-workflow-schema.sh --scope phase2

.PHONY: workflow-mysql-test
workflow-mysql-test:
	bash scripts/test-workflow-mysql.sh both

.PHONY: reliable-mysql-test
reliable-mysql-test:
	bash scripts/test-reliable-mysql.sh both

reliable-rabbit-static-test:
	node --test scripts/test-reliable-rabbit.test.mjs
	bash -n scripts/test-reliable-rabbit.sh

reliable-rabbit-test: reliable-rabbit-static-test
	bash scripts/test-reliable-rabbit.sh

sba-multi-instance-static-test:
	node --test scripts/test-sba-multi-instance-acceptance.test.mjs

frontend-ci: frontend-deps
	node --test scripts/test-ai-session-ui.mjs
	node --test scripts/test-notice-ui.mjs
	node --test scripts/test-quartz-ui.mjs
	node --test scripts/test-quartz-schema.mjs
	node --test scripts/test-quartz-migration.test.mjs
	cd $(FRONTEND_DIR) && npm run lint:eslint && npm run build:prod

generator-ci: frontend-deps
	./scripts/generator-ci-makefile.test.sh
	$(MVN) -pl bixi-module/bixi-generator -am test
	node --test scripts/test-generator-migration.test.mjs
	node --test scripts/test-generator-parent-child-ui.test.mjs
	node --test scripts/test-generated-parent-child-frontend.test.mjs
	node --test scripts/test-generated-import-export-frontend.test.mjs
	node --test scripts/test-generator-ui.mjs
	node --test scripts/test-generator-output.mjs
	node --test scripts/generator-acceptance-ownership.test.mjs
	node --test scripts/generator-acceptance-support.test.mjs

generator-migration-test:
	python3 scripts/test-generator-migration.py

quartz-migration-test:
	python3 scripts/test-quartz-migration.py

quartz-cloud-runtime-config-test:
	bash scripts/quartz-cloud-runtime-config.test.sh

quartz-jdbc-failover-static-test:
	node --test scripts/test-quartz-jdbc-failover.test.mjs
	bash -n scripts/test-quartz-jdbc-failover.sh

quartz-jdbc-failover-test: quartz-jdbc-failover-static-test
	bash scripts/test-quartz-jdbc-failover.sh

ci-gate: backend-cloud-ci backend-single-ci frontend-ci generator-ci sba-multi-instance-static-test quartz-jdbc-failover-static-test
