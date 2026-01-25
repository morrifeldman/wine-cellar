# Gemini Context: Wine Cellar App

## Project Overview
**Wine Cellar** is a self-hosted, full-stack wine collection management application. It features detailed inventory tracking, smart search, and AI-powered capabilities like label analysis and drinking window suggestions. It also integrates with a custom ESP32 hardware device for monitoring cellar conditions (temperature/humidity).

## Technology Stack
*   **Backend:** Clojure (Ring, Reitit, http-kit, HoneySQL, Mount).
*   **Frontend:** ClojureScript (Shadow-CLJS, Reagent/React, Material-UI).
*   **Database:** PostgreSQL.
*   **Embedded:** C/C++ (ESP-IDF) for ESP32-based cellar sentinel.
*   **AI:** Anthropic Claude API.
*   **Infrastructure:** Docker, Fly.io.


## Key Directories
*   `src/clj/wine_cellar`: Backend source code (API, DB, Auth).
*   `src/cljs/wine_cellar`: Frontend source code (Views, State, Components).
*   `src/cljc/wine_cellar`: Shared code between frontend and backend.
*   `dev/`: Development entry points and tools.
*   `embedded/esp32-sentinel`: Firmware for the cellar monitoring hardware.
*   `automation/`: Ansible playbooks for infrastructure (PostgreSQL setup).
*   `scripts/`: Utility scripts for formatting, git hooks, etc.
*   `docs/`: Detailed documentation on features and architecture.

## Development Workflow

### Prerequisites
*   Java 11+
*   Node.js 18+
*   PostgreSQL
*   `pass` password manager (recommended for local dev)

### Common Commands

| Action | Command | Description |
| :--- | :--- | :--- |
| **Start Full Stack** | `clj -M:dev-all` | Starts both backend server and frontend watcher. |
| **Start Backend** | `clj -M:dev:run-server` | Starts only the backend API server (port 3000). |
| **Start Frontend** | `npx shadow-cljs watch app` | Starts frontend compilation watcher (port 8080). |
| **Start REPL** | `clj -M:dev:repl/conjure` | Starts nREPL with CIDER middleware. |
| **Format Code** | `clj -M:format` | Formats all Clojure/Script files using zprint. |
| **Lint Code** | `clj -M:clj-kondo --lint src` | Runs static analysis. |
| **Install Deps** | `npm install` | Installs JavaScript dependencies. |

### Database Setup
A `postgresql.yml` Ansible playbook is provided in `automation/` to provision the database and user. Schema is managed via HoneySQL in `src/clj/wine_cellar/db/schema.clj` and initialized on server start.

## Project Conventions & Guidelines (from AGENTS.md & CLAUDE.md)

*   **Code Style:**
    *   Use 2-space indentation.
    *   Align maps for readability.
    *   Use kebab-case for functions/vars, PascalCase for records/components.
    *   **Format code before committing.**
*   **Working Style:**
    *   **Incremental Changes:** Make small, testable changes.
    *   **Minimal Comments:** Focus on clean code; comment only "why", not "what".
    *   **Verification:** Always run linting (`clj -M:clj-kondo`) after changes.
*   **Architecture:**
    *   Backend logic in `src/clj`, frontend UI in `src/cljs`.
    *   Shared logic in `src/cljc` (use Reader Conditionals if needed).
    *   Frontend state managed via Reagent atoms; backend lifecycle via Mount.
*   **Security:**
    *   **NEVER** commit secrets.
    *   Use `pass` or environment variables for credentials.

## Embedded System (ESP32)
Located in `embedded/esp32-sentinel`. Uses ESP-IDF v5.x.
*   **Build:** `idf.py build`
*   **Flash:** `idf.py -p /dev/ttyUSB0 flash`
*   **Monitor:** `idf.py -p /dev/ttyUSB0 monitor`
*   **Config:** Copy `main/config.example.h` to `main/config.h`.

## Deployment
*   **Platform:** Fly.io.
*   **Config:** `fly.toml` (generated from `fly.toml.template`).
*   **CI/CD:** GitHub Actions workflow in `.github/workflows/deploy.yml`.
