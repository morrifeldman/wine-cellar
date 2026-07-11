#!/usr/bin/env bash
# Start the full wine-cellar dev environment (backend + shadow-cljs watcher,
# plus ngrok when NGROK_URL is set) inside a tmux session, so it survives any
# one terminal or Claude session and either of us can restart it.
#
# Usage:
#   scripts/start-dev.sh            start (or report) the tmux session "wine-dev"
#   scripts/start-dev.sh --attach   same, then attach to the session
#   scripts/start-dev.sh --no-tmux  run in the foreground of this terminal
#
# Inside tmux: Ctrl-b d detaches (keeps running); Ctrl-C tears everything down.
# Logs: tmux capture-pane -pt wine-dev
set -euo pipefail
cd "$(dirname "$0")/.."

SESSION=wine-dev

# NGROK_URL shouldn't live in this public repo — read it from pass.
if [[ -z "${NGROK_URL:-}" ]]; then
  NGROK_URL="$(pass show wine-cellar/ngrok-url 2>/dev/null || true)"
fi
if [[ -n "${NGROK_URL:-}" ]]; then
  export NGROK_URL
else
  unset NGROK_URL # dev-all treats any set value (even "") as "run ngrok"
  echo "NGROK_URL not configured (pass wine-cellar/ngrok-url); starting without ngrok."
fi

# Defaults — override any of these by exporting before running.
export AI_DEFAULT_PROVIDER="${AI_DEFAULT_PROVIDER:-anthropic}"
export ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-claude-opus-4-8}"
export ANTHROPIC_LIGHT_MODEL="${ANTHROPIC_LIGHT_MODEL:-claude-haiku-4-5}"
export OPENAI_MODEL="${OPENAI_MODEL:-gpt-5.5}"
export OPENAI_LIGHT_MODEL="${OPENAI_LIGHT_MODEL:-gpt-5.4-mini}"
export GEMINI_MODEL="${GEMINI_MODEL:-gemini-3.1-pro-preview}"
export GEMINI_LIGHT_MODEL="${GEMINI_LIGHT_MODEL:-gemini-3.1-flash-lite}"

if [[ "${1:-}" == "--no-tmux" ]] || ! command -v tmux >/dev/null; then
  exec clojure -M:dev-all
fi

# Link the dev window into the invoking tmux session so it's one Ctrl-b n
# away, while the canonical wine-dev session keeps it alive independently.
# $TMUX names the exact session — it's inherited even by Claude's shells,
# since claude is launched inside tmux. Outside tmux we don't guess.
link_into_current_session() {
  [[ -z "${TMUX:-}" ]] && return 0
  local target
  target="$(tmux display-message -p '#S')"
  [[ -z "$target" || "$target" == "$SESSION" ]] && return 0
  if ! tmux list-windows -t "$target" -F '#{window_name}' | grep -qx "$SESSION"; then
    tmux link-window -d -s "$SESSION:^" -t "$target" 2>/dev/null &&
      echo "Linked '$SESSION' as a window in session '$target'."
  fi
}

if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "tmux session '$SESSION' already exists."
else
  for port in 3000 8080; do
    if ss -tln 2>/dev/null | grep -q ":$port "; then
      echo "Port $port is in use but there's no '$SESSION' session — a stray process is still running." >&2
      echo "Check: ss -tlnp | grep $port" >&2
      exit 1
    fi
  done
  tmux new-session -d -s "$SESSION" -n "$SESSION" "scripts/start-dev.sh --no-tmux"
  echo "Started dev environment in tmux session '$SESSION'."
fi
link_into_current_session

if [[ "${1:-}" == "--attach" && -t 0 ]]; then
  if [[ -n "${TMUX:-}" ]]; then
    exec tmux switch-client -t "$SESSION"
  else
    exec tmux attach -t "$SESSION"
  fi
else
  echo "Attach with: tmux attach -t $SESSION (or 'tmux switch -t $SESSION' from inside tmux)"
fi
