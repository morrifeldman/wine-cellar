# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a full-stack wine cellar tracking application built with:
- **Backend**: Clojure with http-kit server, Ring middleware, Reitit routing
- **Frontend**: ClojureScript with Reagent (React wrapper) and Material-UI
- **Database**: PostgreSQL with HoneySQL for query generation
- **Build**: Shadow-CLJS for ClojureScript compilation
- **Authentication**: JWT tokens with Google OAuth integration
- **AI Integration**: Anthropic API for wine label analysis

### Key Architectural Components

- **State Management**: Mount for lifecycle management on backend, Reagent atoms on frontend
- **Database Layer**: Custom API in `src/clj/wine_cellar/db/api.clj` with schema definitions in `schema.clj`
- **Authentication**: JWT-based auth with Google OAuth in `src/clj/wine_cellar/auth/`
- **AI Features**: Wine label analysis using Anthropic's API in `src/clj/wine_cellar/ai/`
- **Frontend Structure**: Views organized by feature (wines, tasting-notes, classifications)

## Development Commands

### Starting Development Environment
```bash
# Start both backend and frontend together
clj -M:dev-all

# Or start individually:
# Backend only
clj -M:dev:run-server

# Frontend only (requires npm install first)
npm install
npx shadow-cljs watch app
```

### REPL Development
```bash
# Start REPL with Conjure/CIDER support
clj -M:dev:repl/conjure

# In REPL, mount the system:
(mount/start)
(mount/stop)
```

### Code Formatting
```bash
# Format all Clojure files
clj -M:format

# Format specific file (used by pre-commit hook)
./scripts/format-clj.sh <file>
```

### Testing
The project uses a pre-commit hook that automatically formats Clojure files and updates documentation. Link it with:
```bash
ln -s -f ../.././scripts/pre-commit .git/hooks/pre-commit
```

## Database Operations

### Schema Management
Database schemas are defined in `src/clj/wine_cellar/db/schema.clj` using HoneySQL format. The database is automatically initialized on server startup via `db-setup/initialize-db`.

### Key Tables
- `wines`: Main wine inventory with detailed wine information
- `wine_classifications`: Wine classification data (AOC, regions, etc.)
- `tasting_notes`: Wine tasting records and ratings
- `grape_varieties` + `wine_grape_varieties`: Many-to-many grape variety relationships

## Credential Management

### Local Development
Uses the `pass` password manager for secure credential storage. Required credentials:
- `wine-cellar/anthropic-api-key`
- `wine-cellar/jwt-secret`
- `wine-cellar/cookie-store-key`
- `wine-cellar/admin-email`
- `wine-cellar/google-oath-json`

### Production (Fly.io)
Credentials are managed through Fly.io secrets. See README.md for deployment instructions.

## Frontend Development

### Component Structure
- Views are organized by feature in `src/cljs/wine_cellar/views/`
- Shared components in `src/cljs/wine_cellar/views/components/`
- Utilities for filtering, formatting, and vintage calculations in `utils/`

### Material-UI Integration
Uses `arttuka/reagent-material-ui` for Material-UI components. Components are typically wrapped in Reagent functions.

### State Management
- Local component state using Reagent atoms
- API calls through `wine-cellar.api` namespace
- Client-side routing handled in `core.cljs`

## AI Features

The application includes AI-powered wine label analysis using Anthropic's API. This automatically extracts wine details from uploaded label images.

## File Upload and Image Handling

Wine label images are stored as bytea in PostgreSQL with both full images and thumbnails. Upload handling is done through multipart form data.