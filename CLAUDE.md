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
- **Small Edits**: For single character additions (like missing parentheses), suggest the fix in chat rather than using Edit tool

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
├── AGENTS.md
├── automation
│   └── postgresql.yml
├── CLAUDE.md
├── deps.edn
├── dev
│   ├── assets
│   │   ├── klimt-back.jpg
│   │   └── klimt-front.jpg
│   ├── user.clj
│   ├── vivino_process.clj
│   └── wine_cellar
│       └── dev
│           └── label_demo.clj
├── Dockerfile
├── docs
│   ├── ai-chat-persistence-feature.md
│   ├── ai-drinking-window-feature.md
│   ├── ai-form-fill-feature.md
│   ├── ai-form-fill-implementation.md
│   ├── alcohol-percentage-implementation.md
│   ├── cellar-conditions.md
│   ├── chat-summary.md
│   ├── code-quality-improvements.md
│   ├── environment-variables.md
│   ├── ideal-taxonomy.md
│   ├── pairing-feature.md
│   ├── refactor-aoc-to-appellation.md
│   ├── refactor-appellation-tier.md
│   ├── schema-unification-datomic.md
│   ├── screenshots
│   │   ├── assistant.png
│   │   └── overview.png
│   ├── testing-strategy.md
│   ├── varieties-implementation.md
│   ├── wset_l3_wines_sat_en_jun-2016.pdf
│   └── wset-tasting-notes-feature.md
├── embedded
│   └── esp32-sentinel
│       ├── CMakeLists.txt
│       ├── components
│       │   ├── cellar_display
│       │   │   ├── cellar_display.c
│       │   │   ├── CMakeLists.txt
│       │   │   └── include
│       │   │       └── cellar_display.h
│       │   ├── cellar_http
│       │   │   ├── cellar_auth.c
│       │   │   ├── cellar_auth.h
│       │   │   ├── cellar_http.c
│       │   │   ├── CMakeLists.txt
│       │   │   └── include
│       │   │       └── cellar_http.h
│       │   ├── cellar_light
│       │   │   ├── cellar_light.c
│       │   │   ├── CMakeLists.txt
│       │   │   └── include
│       │   │       └── cellar_light.h
│       │   ├── opt3001
│       │   │   ├── CMakeLists.txt
│       │   │   ├── include
│       │   │   │   └── opt3001.h
│       │   │   └── opt3001.c
│       │   └── veml7700
│       │       ├── CMakeLists.txt
│       │       ├── include
│       │       │   └── veml7700.h
│       │       └── veml7700.c
│       ├── dependencies.lock
│       ├── main
│       │   ├── CMakeLists.txt
│       │   ├── config.example.h
│       │   ├── idf_component.yml
│       │   ├── main.c
│       │   └── server_root_cert.pem
│       └── README.md
├── fly.toml.template
├── GEMINI.md
├── package.json
├── package-lock.json
├── public
│   ├── apple-touch-icon.png
│   ├── favicon-96x96.png
│   ├── favicon.ico
│   ├── favicon.svg
│   ├── index.html
│   ├── service-worker.js
│   ├── site.webmanifest
│   ├── version.json
│   ├── web-app-manifest-192x192.png
│   └── web-app-manifest-512x512.png
├── README.md
├── resources
│   └── wine-classifications.edn
├── scripts
│   ├── format-clj.sh
│   ├── format_zprint.clj
│   ├── pre-commit
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       └── dev.clj
├── shadow-cljs.edn
├── src
│   ├── clj
│   │   └── wine_cellar
│   │       ├── admin
│   │       │   └── bulk_operations.clj
│   │       ├── ai
│   │       │   ├── anthropic.clj
│   │       │   ├── core.clj
│   │       │   ├── gemini.clj
│   │       │   ├── openai.clj
│   │       │   └── prompts.clj
│   │       ├── auth
│   │       │   ├── config.clj
│   │       │   └── core.clj
│   │       ├── config_utils.clj
│   │       ├── db
│   │       │   ├── api.clj
│   │       │   ├── connection.clj
│   │       │   ├── migrations.clj
│   │       │   ├── schema.clj
│   │       │   └── setup.clj
│   │       ├── dev
│   │       │   └── seed_sample_wines.clj
│   │       ├── devices.clj
│   │       ├── handlers.clj
│   │       ├── logging.clj
│   │       ├── reports
│   │       │   └── core.clj
│   │       ├── routes.clj
│   │       └── server.clj
│   ├── cljc
│   │   └── wine_cellar
│   │       ├── common.cljc
│   │       └── summary.cljc
│   └── cljs
│       └── wine_cellar
│           ├── api.cljs
│           ├── config.cljs
│           ├── core.cljs
│           ├── portal_debug.cljs
│           ├── state.cljs
│           ├── theme.cljs
│           ├── utils
│           │   ├── filters.cljs
│           │   ├── formatting.cljs
│           │   ├── mui.cljs
│           │   └── vintage.cljs
│           ├── version.cljs
│           └── views
│               ├── admin
│               │   ├── devices.cljs
│               │   └── sql.cljs
│               ├── cellar_conditions.cljs
│               ├── classifications
│               │   ├── form.cljs
│               │   └── list.cljs
│               ├── components
│               │   ├── ai_provider_toggle.cljs
│               │   ├── classification_fields.cljs
│               │   ├── debug.cljs
│               │   ├── form.cljs
│               │   ├── image_upload.cljs
│               │   ├── portal_debug.cljs
│               │   ├── stats_charts.cljs
│               │   ├── technical_data.cljs
│               │   ├── wine_card.cljs
│               │   ├── wine_chat.cljs
│               │   ├── wine_color.cljs
│               │   ├── wset_appearance.cljs
│               │   ├── wset_conclusions.cljs
│               │   ├── wset_nose.cljs
│               │   ├── wset_palate.cljs
│               │   └── wset_shared.cljs
│               ├── components.cljs
│               ├── grape_varieties
│               │   └── list.cljs
│               ├── main.cljs
│               ├── reports
│               │   └── core.cljs
│               ├── tasting_notes
│               │   ├── form.cljs
│               │   └── list.cljs
│               └── wines
│                   ├── detail.cljs
│                   ├── filters.cljs
│                   ├── form.cljs
│                   ├── list.cljs
│                   └── varieties.cljs
└── tools
    └── wine-color-picker
        ├── debug_sampling.jpg
        ├── extract_colors_programmatic.py
        ├── refine_whites.py
        ├── requirements.txt
        ├── serve_color_picker.py
        ├── web_color_picker.html
        └── wine-colors.jpg

50 directories, 151 files
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

See [docs/environment-variables.md](docs/environment-variables.md) for complete list of required credentials and environment variables for both local development and production.

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

## Clojure Specifics

- **Function Declaration**: In Clojure, functions need to be declared before they're used.

## Json Processing Recommendations

- **Use Jsonista**: Recommend using jsonista for json processing
- To confirm thte code is valid, run `clj -M:clj-kondo --lint src/<FILE CHANGED>` or `clj -M:clj-kondo --lint src` for larger changes.  Do this after each change.