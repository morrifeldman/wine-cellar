#!/bin/sh
# Point jj's repo-scoped config at the zprint settings committed in this repo,
# so `jj fix` formats Clojure files the same way for everyone.
#
# jj keeps repo config outside the repo (~/.config/jj/repos/<hash>/config.toml as
# of jj 0.44), so ask jj where it belongs rather than assuming .jj/repo/.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(dirname "$SCRIPT_DIR")
CONFIG_SOURCE="$ROOT_DIR/jj-config.toml"

if [ ! -d "$ROOT_DIR/.jj" ]; then
  echo "Not a jj repository; skipping jj config installation."
  exit 0
fi

if [ ! -f "$CONFIG_SOURCE" ]; then
  echo "Expected committed config at $CONFIG_SOURCE but none found." >&2
  exit 1
fi

CONFIG_TARGET=$(cd "$ROOT_DIR" && jj config path --repo)
mkdir -p "$(dirname "$CONFIG_TARGET")"
ln -sf "$CONFIG_SOURCE" "$CONFIG_TARGET"
echo "Linked $CONFIG_TARGET -> $CONFIG_SOURCE"
