#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(dirname "$SCRIPT_DIR")
CONFIG_SOURCE="$ROOT_DIR/jj-config.toml"
CONFIG_TARGET="$ROOT_DIR/.jj/repo/config.toml"

if [ ! -d "$ROOT_DIR/.jj/repo" ]; then
  echo "Not a jj repository (.jj/repo missing); skipping jj config installation."
  exit 0
fi

if [ ! -f "$CONFIG_SOURCE" ]; then
  echo "Expected committed config at $CONFIG_SOURCE but none found."
  exit 1
fi

ln -sf ../../jj-config.toml "$CONFIG_TARGET"
echo "Linked $CONFIG_TARGET -> ../../jj-config.toml"
