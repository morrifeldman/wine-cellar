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
- **Json Processing**: Recommend using jsonista for json processing.
- **Verification**: Run `clj -M:clj-kondo --lint src/<FILE CHANGED>` or `clj -M:clj-kondo --lint src` after each change.

## Current Priorities

1. Implement AI-driven wine pairing features
2. Enable contextual AI conversations about wine selection  
3. Improve the editor with pretty-print or Portal-like features
4. Use app-state :view state more broadly throughout the application
5. UI/UX improvements

## Project Context

**Refer to `AGENTS.md` for:**
- Architecture Overview & Component Details
- Project Structure & Directory Layout
- Development, Build, and Test Commands
- Database Schema & Operations
- Frontend & Material-UI Structure
- Embedded System (ESP32) Details

## AI Features

The application includes AI-powered wine label analysis using Anthropic's API. This automatically extracts wine details from uploaded label images.

## Credential Management

See [docs/environment-variables.md](docs/environment-variables.md) for complete list of required credentials and environment variables for both local development and production.

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
│   ├── taxonomy-guidelines.md
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
│   ├── populate_test_images.clj
│   ├── pre-commit
│   ├── repl_client.clj
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       ├── dev.clj
│       └── scripts
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

51 directories, 154 files
```


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
│   ├── ui_check.js
│   ├── ui_smoke_test.js
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
│   ├── taxonomy-guidelines.md
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
│   ├── populate_test_images.clj
│   ├── pre-commit
│   ├── repl_client.clj
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       ├── dev.clj
│       └── scripts
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

51 directories, 156 files
```


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
│   ├── taxonomy-guidelines.md
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
│   ├── populate_test_images.clj
│   ├── pre-commit
│   ├── repl_client.clj
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       ├── dev.clj
│       └── scripts
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

51 directories, 154 files
```


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
│   ├── local-search-implementation.md
│   ├── pairing-feature.md
│   ├── refactor-aoc-to-appellation.md
│   ├── refactor-appellation-tier.md
│   ├── schema-unification-datomic.md
│   ├── screenshots
│   │   ├── assistant.png
│   │   └── overview.png
│   ├── taxonomy-guidelines.md
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
├── local_search_failure.png
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
│   ├── populate_test_images.clj
│   ├── pre-commit
│   ├── repl_client.clj
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       ├── dev.clj
│       └── scripts
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
├── test_failure.png
└── tools
    └── wine-color-picker
        ├── debug_sampling.jpg
        ├── extract_colors_programmatic.py
        ├── refine_whites.py
        ├── requirements.txt
        ├── serve_color_picker.py
        ├── web_color_picker.html
        └── wine-colors.jpg

51 directories, 157 files
```


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
│   ├── local-search-implementation.md
│   ├── pairing-feature.md
│   ├── refactor-aoc-to-appellation.md
│   ├── refactor-appellation-tier.md
│   ├── schema-unification-datomic.md
│   ├── screenshots
│   │   ├── assistant.png
│   │   └── overview.png
│   ├── taxonomy-guidelines.md
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
│   ├── populate_test_images.clj
│   ├── pre-commit
│   ├── repl_client.clj
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       ├── dev.clj
│       └── scripts
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
│               ├── chat
│               │   ├── actions.cljs
│               │   ├── context.cljs
│               │   ├── core.cljs
│               │   ├── input.cljs
│               │   ├── message.cljs
│               │   ├── sidebar.cljs
│               │   └── utils.cljs
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

52 directories, 161 files
```


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
│   ├── local-search-implementation.md
│   ├── pairing-feature.md
│   ├── refactor-aoc-to-appellation.md
│   ├── refactor-appellation-tier.md
│   ├── schema-unification-datomic.md
│   ├── screenshots
│   │   ├── assistant.png
│   │   └── overview.png
│   ├── taxonomy-guidelines.md
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
│   ├── populate_test_images.clj
│   ├── pre-commit
│   ├── repl_client.clj
│   ├── setup-git-hooks.sh
│   ├── update-version.sh
│   └── wine_cellar
│       ├── dev.clj
│       └── scripts
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
│               ├── chat
│               │   ├── actions.cljs
│               │   ├── context.cljs
│               │   ├── core.cljs
│               │   ├── input.cljs
│               │   ├── message.cljs
│               │   ├── sidebar.cljs
│               │   └── utils.cljs
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

52 directories, 161 files
```

