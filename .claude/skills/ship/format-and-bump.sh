#!/bin/bash
# Format Clojure sources and give the app a new version number.
#
# These are the two steps that nothing else in the repo does for you: jj has no
# commit hooks, so run this yourself before every commit that touches app code.
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
cd "$ROOT_DIR"

# --- Format -----------------------------------------------------------------
# zprint settings and the file patterns live in jj-config.toml.
if [ -d .jj ]; then
  echo "Formatting with jj fix..."
  jj fix
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
