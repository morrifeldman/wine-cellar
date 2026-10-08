#!/bin/bash
# Cloud-session setup: npm deps, a local Postgres with a wine_cellar
# database, the Clojure CLI, babashka, and Clojure deps (incl. clj-kondo).
# The Clojure deps step needs the environment network policy to allow
# repo.clojars.org (Maven Central and GitHub downloads are allowed by
# default).
# Local machines are left alone.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# JS deps (shadow-cljs, MUI); install rather than ci so the cache is reused
npm install --no-audit --no-fund

# Postgres: start the preinstalled cluster and create the dev database
if command -v pg_ctlcluster >/dev/null; then
  PG_VER=$(pg_lsclusters -h | awk 'NR==1{print $1}')
  pg_ctlcluster "$PG_VER" main start 2>/dev/null || true
  su postgres -c "psql -qc \"ALTER USER postgres PASSWORD 'postgres'\""
  su postgres -c "psql -tAc \"SELECT 1 FROM pg_database WHERE datname='wine_cellar'\"" | grep -q 1 \
    || su postgres -c "createdb wine_cellar"
  echo 'export DATABASE_URL="postgresql://postgres:postgres@localhost:5432/wine_cellar"' >> "$CLAUDE_ENV_FILE"
fi

# Clojure CLI
if ! command -v clojure >/dev/null; then
  curl -fsSL -o /tmp/clojure-install.sh \
    https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
  bash /tmp/clojure-install.sh
  rm -f /tmp/clojure-install.sh
fi

# babashka (scripts/repl_client.clj)
if ! command -v bb >/dev/null; then
  curl -fsSL https://raw.githubusercontent.com/babashka/babashka/master/install \
    | bash -s -- --static
fi

# Clojure deps for the server, dev and lint aliases
clojure -P -M:dev:clj-kondo
