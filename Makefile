SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c

GRADLE ?= ./gradlew

.PHONY: help build test run package clean

help:
	@echo "Common tasks:"
	@echo "  make build    Compile project, generate SQLDelight, and run tests"
	@echo "  make test     Run the test suite (JUnit Platform + kotlin.test)"
	@echo "  make run      Launch the desktop app"
	@echo "  make package  Build an installer for the current OS"
	@echo "  make clean    Remove build outputs"

build:
	$(GRADLE) build

test:
	$(GRADLE) test

run:
	$(GRADLE) run

package:
	$(GRADLE) packageDistributionForCurrentOS

clean:
	$(GRADLE) clean
