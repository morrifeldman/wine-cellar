# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working Style Preferences

- **Incremental Development**: Suggest only one change at a time so changes can be tested before moving on. When in doubt suggest less code!
- **Pair Programming**: Be collaborative and don't get too far ahead with suggestions
- **Declarative Approach**: Minimize duplication and create reusable abstractions when sensible
- **Minimal Comments**: Code comments are generally not needed
- **Double Check Parentheses**: Keep function size short and ensure parentheses match in Clojure code
- **Minimal Summaries**: Provide only brief summaries of changes or none at all
- **Direct File Sharing**: Don't use shell tools to inspect files - files will be provided directly when needed
- **Code Suggestions**: If code replacement tools encounter errors, suggest changes in chat instead

## Current Priorities

1. Implement AI-driven wine pairing features
2. Enable contextual AI conversations about wine selection  
3. Improve the editor with pretty-print or Portal-like features
4. Use app-state :view state more broadly throughout the application
5. UI/UX improvements

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

## Project Structure

```
.
├── automation
│   └── postgresql.yml
├── CLAUDE.md
├── deps.edn
├── dev
│   ├── clj
│   │   ├── user.clj
│   │   └── vivino_process.clj
│   └── cljs
│       └── user.clj
├── Dockerfile
├── docs
│   ├── ai-drinking-window-feature.md
│   ├── ai-form-fill-feature.md
│   ├── ai-form-fill-implementation.md
│   ├── alcohol-percentage-implementation.md
│   ├── chat-summary.md
│   ├── environment-variables.md
│   ├── ideal-taxonomy.md
│   ├── pairing-feature.md
│   ├── schema-unification-datomic.md
│   └── varieties-implementation.md
├── fly.toml.template
├── package.json
├── package-lock.json
├── public
│   ├── apple-touch-icon.png
│   ├── favicon-96x96.png
│   ├── favicon.ico
│   ├── favicon.svg
│   ├── index.html
│   ├── site.webmanifest
│   ├── web-app-manifest-192x192.png
│   └── web-app-manifest-512x512.png
├── README.md
├── resources
│   └── wine-classifications.edn
├── Screenshots
│   └── overview.png
├── scripts
│   ├── format-clj.sh
│   ├── format_zprint.clj
│   ├── pre-commit
│   └── wine_cellar
│       └── dev.clj
├── shadow-cljs.edn
├── src
│   ├── clj
│   │   └── wine_cellar
│   │       ├── ai
│   │       │   └── anthropic.clj
│   │       ├── auth
│   │       │   ├── config.clj
│   │       │   └── core.clj
│   │       ├── config_utils.clj
│   │       ├── db
│   │       │   ├── api.clj
│   │       │   ├── connection.clj
│   │       │   ├── schema.clj
│   │       │   └── setup.clj
│   │       ├── debug
│   │       ├── handlers.clj
│   │       ├── routes.clj
│   │       └── server.clj
│   ├── cljc
│   │   └── wine_cellar
│   │       └── common.cljc
│   └── cljs
│       └── wine_cellar
│           ├── api.cljs
│           ├── config.cljs
│           ├── core.cljs
│           ├── portal_debug.cljs
│           ├── theme.cljs
│           ├── utils
│           │   ├── filters.cljs
│           │   ├── formatting.cljs
│           │   └── vintage.cljs
│           └── views
│               ├── classifications
│               │   ├── form.cljs
│               │   └── list.cljs
│               ├── components
│               │   ├── classification_fields.cljs
│               │   ├── debug.cljs
│               │   ├── form.cljs
│               │   ├── image_upload.cljs
│               │   ├── portal_debug.cljs
│               │   ├── wine_card.cljs
│               │   └── wine_chat.cljs
│               ├── components.cljs
│               ├── grape_varieties
│               │   └── list.cljs
│               ├── main.cljs
│               ├── tasting_notes
│               │   ├── form.cljs
│               │   └── list.cljs
│               └── wines
│                   ├── detail.cljs
│                   ├── filters.cljs
│                   ├── form.cljs
│                   ├── list.cljs
│                   └── varieties.cljs
├── vivino_data
│   ├── cellar.csv
│   ├── external_accounts.csv
│   ├── full_wine_list.csv
│   ├── label_scans.csv
│   ├── last_login_information.csv
│   ├── mobile_client_information.csv
│   ├── user_prices.csv
│   ├── user_profile.csv
│   └── wine_menu_scans.csv
├── vivino_images
│   ├── wine_000_full.jpg
│   ├── wine_000_thumb.jpg
│   ├── wine_001_full.jpg
│   ├── wine_001_thumb.jpg
│   ├── wine_002_full.jpg
│   ├── wine_002_thumb.jpg
│   ├── wine_003_full.jpg
│   ├── wine_003_thumb.jpg
│   ├── wine_004_full.jpg
│   └── wine_004_thumb.jpg
└── vivino_processed_sample.edn

31 directories, 95 files
```

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