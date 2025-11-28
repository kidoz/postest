#!/usr/bin/env bash

set -euo pipefail

if [[ "$(uname)" != "Darwin" ]]; then
  echo "This script must be run on macOS to build the DMG." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

./gradlew packageDmg

echo
echo "DMG available at: ${ROOT_DIR}/build/compose/binaries/main/dmg"
