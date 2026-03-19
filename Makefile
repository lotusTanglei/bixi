SHELL := /bin/zsh
ROOT := $(CURDIR)
FRONTEND_DIR := $(ROOT)/bixi-ui

.PHONY: backend-dev backend-test backend-prod frontend-dev frontend-test frontend-prod backend-ci frontend-ci ci-gate

backend-dev:
	JAVA_HOME=$$(/usr/libexec/java_home -v 17) mvn -Dprofiles.active=dev clean compile

backend-test:
	JAVA_HOME=$$(/usr/libexec/java_home -v 17) mvn -Dprofiles.active=test clean compile

backend-prod:
	JAVA_HOME=$$(/usr/libexec/java_home -v 17) mvn -Dprofiles.active=prod clean compile

frontend-dev:
	cd $(FRONTEND_DIR) && npm ci && npm run build:dev

frontend-test:
	cd $(FRONTEND_DIR) && npm ci && npm run build:test

frontend-prod:
	cd $(FRONTEND_DIR) && npm ci && npm run build:prod

backend-ci:
	JAVA_HOME=$$(/usr/libexec/java_home -v 17) mvn clean verify

frontend-ci:
	cd $(FRONTEND_DIR) && npm ci && npm run lint:eslint && npm run build:prod

ci-gate: backend-ci frontend-ci
