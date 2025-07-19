#!/bin/bash

# Get git information
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
DATE=$(date -u +"%Y-%m-%d %H:%M:%S UTC")
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

# Read current version number from version.json
CURRENT_VERSION=$(grep -o '"[0-9]\+\.[0-9]\+\.[0-9]\+"' public/version.json | tr -d '"' 2>/dev/null || echo "1.0.0")

# Auto-increment patch version for commits (optional - could be manual)
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]}

# Only increment if this is a real commit (not during development)
if [[ "$1" == "--increment" ]]; then
    PATCH=$((PATCH + 1))
fi

NEW_VERSION="$MAJOR.$MINOR.$PATCH"

# Create a JSON version for the frontend
cat > public/version.json << EOF
{
  "version": "$NEW_VERSION",
  "commit": "$COMMIT",
  "date": "$DATE",
  "branch": "$BRANCH"
}
EOF

echo "Updated version to $NEW_VERSION (commit: $COMMIT)"