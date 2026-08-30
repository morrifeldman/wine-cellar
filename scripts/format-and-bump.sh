#!/bin/bash
# Format Clojure sources and give the app a new version number.
#
# These are the two steps that nothing else in the repo does for you: jj has no
# commit hooks, so run this yourself before every commit that touches app code.
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

# --- Format -----------------------------------------------------------------
# `jj fix` formats only the files the revision changed, which is why we use it
# over a whole-tree pass. Pass --all to format every source file instead; the
# file patterns and zprint settings both live in jj-config.toml.
#
# jj reaches that config through a path it owns, outside the repo, so a fresh
# clone has none and `jj fix` exits with "No `fix.tools` are configured". Install
# it and retry rather than making the user work out what went wrong.
FIX_ARGS=""
if [ "${1:-}" = "--all" ]; then
  FIX_ARGS="--include-unchanged-files"
  shift
fi

if [ ! -d .jj ]; then
  echo "Not a jj checkout, so there is no jj fix to run." >&2
  echo "Format by hand with: zprint '{:style :community :map {:comma? false} :width 80}'" >&2
  exit 1
fi

if ! command -v zprint > /dev/null; then
  echo "zprint is not on PATH — install it from https://github.com/kkinnear/zprint" >&2
  exit 1
fi

echo "Formatting with jj fix${FIX_ARGS:+ $FIX_ARGS}..."
if ! output=$(jj fix $FIX_ARGS 2>&1); then
  if printf '%s' "$output" | grep -q 'fix\.tools'; then
    echo "jj has no formatting tools configured; linking the config and retrying..."
    ./scripts/setup-jj-config.sh
    output=$(jj fix $FIX_ARGS 2>&1)
  else
    printf '%s\n' "$output" >&2
    exit 1
  fi
fi
printf '%s\n' "$output"

# --- Version ----------------------------------------------------------------
# The app polls /version.json and prompts users to refresh when the version
# changes, and the service worker keys its asset cache on the same value. Reuse
# a version number and a deploy leaves everyone quietly on the old bundle.
VERSION_FILE=public/version.json
CURRENT=$(grep -o '"[0-9]\+\.[0-9]\+\.[0-9]\+"' "$VERSION_FILE" | tr -d '"' || echo "1.0.0")
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT"
NEW_VERSION="$MAJOR.$MINOR.$((PATCH + 1))"

cat > "$VERSION_FILE" << JSON
{
  "version": "$NEW_VERSION",
  "date": "$(date -u +"%Y-%m-%d %H:%M:%S UTC")"
}
JSON

echo "Version $CURRENT -> $NEW_VERSION"
