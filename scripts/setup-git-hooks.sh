#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
HOOK_SOURCE="$ROOT_DIR/scripts/pre-commit"
HOOK_TARGET="$ROOT_DIR/.git/hooks/pre-commit"

if [ ! -d "$ROOT_DIR/.git" ]; then
  echo "Not a git repository; skipping git hook installation."
  exit 0
fi

mkdir -p "$ROOT_DIR/.git/hooks"
ln -sf "$HOOK_SOURCE" "$HOOK_TARGET"
chmod +x "$HOOK_SOURCE"
echo "Linked pre-commit hook to $HOOK_TARGET"
