#!/bin/bash
# Set up a Claude Code cloud session so the dev stack, REPL, linter, formatter
# and Playwright all work: Clojure CLI, babashka, rlwrap, zprint, jj, a local
# Postgres, and placeholder secrets (local dev normally gets these from `pass`).
# Idempotent; the container image is cached after the first run.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

JJ_VERSION=v0.46.0
ZPRINT_VERSION=1.3.0

have() { command -v "$1" >/dev/null 2>&1; }

apt_install() {
  DEBIAN_FRONTEND=noninteractive apt-get install -y -q "$@" >/dev/null 2>&1 ||
    { apt-get update -q >/dev/null 2>&1 &&
      DEBIAN_FRONTEND=noninteractive apt-get install -y -q "$@" >/dev/null; }
}

# --- System tools -------------------------------------------------------------
have rlwrap || apt_install rlwrap        # the `clj` wrapper dev-all uses needs it
have pg_ctlcluster || apt_install postgresql

if ! have clojure; then
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/install.sh" \
    https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
  bash "$tmp/install.sh" >/dev/null
  rm -rf "$tmp"
fi

if ! have bb; then
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/install" \
    https://raw.githubusercontent.com/babashka/babashka/master/install
  bash "$tmp/install" >/dev/null
  rm -rf "$tmp"
fi

if ! have zprint; then
  curl -fsSL -o /usr/local/bin/zprint \
    "https://github.com/kkinnear/zprint/releases/download/$ZPRINT_VERSION/zprintl-$ZPRINT_VERSION"
  chmod +x /usr/local/bin/zprint
fi

if ! have jj; then
  tmp=$(mktemp -d)
  curl -fsSL \
    "https://github.com/jj-vcs/jj/releases/download/$JJ_VERSION/jj-$JJ_VERSION-x86_64-unknown-linux-musl.tar.gz" |
    tar xz -C "$tmp"
  find "$tmp" -name jj -type f -exec cp {} /usr/local/bin/jj \;
  rm -rf "$tmp"
fi

# --- jj, colocated with the git clone so the ship skill works ----------------
if [ ! -d .jj ]; then
  jj git init --colocate >/dev/null 2>&1
fi
jj config set --user user.name "Morris Feldman" >/dev/null 2>&1
jj config set --user user.email "morrifeldman@gmail.com" >/dev/null 2>&1
sh scripts/setup-jj-config.sh >/dev/null

# --- Postgres -----------------------------------------------------------------
service postgresql start >/dev/null
for _ in $(seq 1 20); do
  su postgres -c "pg_isready -q" && break
  sleep 0.5
done
su postgres -c "psql -qc \"ALTER USER postgres PASSWORD 'postgres';\"" >/dev/null
su postgres -c "psql -lqt" | cut -d'|' -f1 | grep -qw wine_cellar ||
  su postgres -c "createdb wine_cellar"

# --- Project dependencies -----------------------------------------------------
npm install --no-audit --no-fund >/dev/null 2>&1
clojure -P -M:dev:clj-kondo:dev-all:repl/conjure >/dev/null 2>&1

# --- Session environment ------------------------------------------------------
# Placeholder secrets: OAuth login can't work here, but dev/test_helpers.js
# mints its own JWT through the REPL, so the app is fully usable via Playwright.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  cat >>"$CLAUDE_ENV_FILE" <<'EOF'
export DB_HOST=localhost DB_PORT=5432 DB_NAME=wine_cellar DB_USER=postgres DB_PASSWORD=postgres
export JWT_SECRET=${JWT_SECRET:-cloud-dev-jwt-secret}
export COOKIE_STORE_KEY=${COOKIE_STORE_KEY:-0123456789abcdef}
export GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID:-cloud-dev} GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET:-cloud-dev}
export ADMIN_EMAIL=${ADMIN_EMAIL:-morrifeldman@gmail.com}
export ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-unset} OPENAI_API_KEY=${OPENAI_API_KEY:-unset} GEMINI_API_KEY=${GEMINI_API_KEY:-unset}
export CHROMIUM_PATH=/opt/pw-browsers/chromium
EOF
fi
