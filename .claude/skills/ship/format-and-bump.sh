#!/bin/bash
# Format Clojure sources and give the app a new version number.
#
# These are the two steps that nothing else in the repo does for you: jj has no
# commit hooks, so run this yourself before every commit that touches app code.
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
cd "$ROOT_DIR"

# --- Format -----------------------------------------------------------------
# `jj fix` formats only the files the revision actually changed, which is why we
# use it over a whole-tree pass. It needs two things installed: the zprint binary
# on PATH, and jj-config.toml reaching jj through a symlink at
# .jj/repo/config.toml. Both fail loudly, and the missing symlink we can just fix.
if ! command -v zprint > /dev/null; then
  echo "zprint is not on PATH — install it from https://github.com/kkinnear/zprint" >&2
  echo "or run the slower JVM fallback by hand: clj -M:format" >&2
  exit 1
fi

if [ -d .jj ]; then
  echo "Formatting with jj fix..."
  if ! output=$(jj fix 2>&1); then
    if printf '%s' "$output" | grep -q 'fix\.tools'; then
      echo "jj has no formatting tools configured; linking the config and retrying..."
      ./scripts/setup-jj-config.sh
      output=$(jj fix 2>&1)
    else
      printf '%s\n' "$output" >&2
      exit 1
    fi
  fi
  printf '%s\n' "$output"
else
  echo "Not a jj repo; formatting every Clojure file with clj -M:format..."
  clj -M:format
fi

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
