# justfile for Postest

set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

gradle := "./gradlew"

# List available recipes
default:
    @just --list

# Compile project, generate SQLDelight, and run tests
build:
    {{gradle}} build

# Run the test suite (JUnit Platform + kotlin.test)
test:
    {{gradle}} test

# Launch the desktop app
run:
    {{gradle}} run

# Build an installer for the current OS
package:
    {{gradle}} packageDistributionForCurrentOS

# Remove build outputs
clean:
    {{gradle}} clean

# Run linting and static analysis
lint:
    {{gradle}} ktlintCheck detekt

# Auto-format Kotlin sources
format:
    {{gradle}} ktlintFormat

# Run a single test class (e.g., just test-class VariableResolverTest)
test-class name:
    {{gradle}} test --tests "{{name}}"
