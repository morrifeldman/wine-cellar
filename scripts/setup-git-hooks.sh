#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(dirname "$SCRIPT_DIR")
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
