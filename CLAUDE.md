See [AGENTS.md](AGENTS.md)

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
│               ├── blind_tastings
│               │   ├── form.cljs
│               │   ├── link_dialog.cljs
│               │   └── list.cljs
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

53 directories, 165 files
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
├── e2e
│   └── blind_tasting.spec.js
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
│               ├── blind_tastings
│               │   ├── form.cljs
│               │   ├── link_dialog.cljs
│               │   └── list.cljs
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
├── test-results
└── tools
    └── wine-color-picker
        ├── debug_sampling.jpg
        ├── extract_colors_programmatic.py
        ├── refine_whites.py
        ├── requirements.txt
        ├── serve_color_picker.py
        ├── web_color_picker.html
        └── wine-colors.jpg

55 directories, 166 files
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
├── e2e
│   └── blind_tasting.spec.js
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
│   │       ├── scheduler.clj
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
│               ├── blind_tastings
│               │   ├── form.cljs
│               │   ├── link_dialog.cljs
│               │   └── list.cljs
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
├── test-results
└── tools
    └── wine-color-picker
        ├── debug_sampling.jpg
        ├── extract_colors_programmatic.py
        ├── refine_whites.py
        ├── requirements.txt
        ├── serve_color_picker.py
        ├── web_color_picker.html
        └── wine-colors.jpg

55 directories, 167 files
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
├── e2e
│   └── blind_tasting.spec.js
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
│   │       ├── scheduler.clj
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
│               ├── blind_tastings
│               │   ├── form.cljs
│               │   ├── link_dialog.cljs
│               │   └── list.cljs
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
├── test-results
└── tools
    └── wine-color-picker
        ├── debug_sampling.jpg
        ├── extract_colors_programmatic.py
        ├── refine_whites.py
        ├── requirements.txt
        ├── serve_color_picker.py
        ├── web_color_picker.html
        └── wine-colors.jpg

55 directories, 167 files
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
├── e2e
│   └── blind_tasting.spec.js
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
│   │       ├── scheduler.clj
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
│               ├── blind_tastings
│               │   ├── form.cljs
│               │   ├── link_dialog.cljs
│               │   └── list.cljs
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
├── test-results
└── tools
    └── wine-color-picker
        ├── debug_sampling.jpg
        ├── extract_colors_programmatic.py
        ├── refine_whites.py
        ├── requirements.txt
        ├── serve_color_picker.py
        ├── web_color_picker.html
        └── wine-colors.jpg

55 directories, 167 files
```

