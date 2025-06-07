# Context for Claude

## Project Overview
I'm building a wine cellar tracking web app as a learning project. I'm experienced with backend Clojure and am using ClojureScript for the frontend. I currently use Viveno for wine tracking, but I'm frustrated with its ads and limitations for simple tracking needs.

## Technical Stack
- Backend: Clojure
- Frontend: ClojureScript with Reagent
- Database: PostgreSQL 15
- UI Framework: Material UI (via reagent-mui)
- Build Tool: Shadow-cljs
- Clojure: tools.deps
- Editor: Neovim

## Deployment
- Fly.io

## Working Style Preferences
- **Incremental Development**: Please suggest only one change at a time so I can
  test before moving on.  When in doubt suggest less code!!!
- **Pair Programming**: Be collaborative and don't get too far ahead with suggestions
- **Declarative Approach**: Minimize duplication and create reusable abstractions when sensible
- **Minimal Comments**: I don't generally need code comments
- **Double Check Parentheses**: You will be programming in Clojure.  Keep the
  function size short and make sure the parentheses match!
- **Minimal Summaries**: After suggesting code, provide only a very brief summary of changes or none at all
- **Direct File Sharing**: Don't use shell tools to inspect files - I'll provide files directly when needed
- **Code Suggestions**: If you encounter errors with code replacement tools, just suggest changes in the chat and I'll apply them

## My Experience
I'm an experienced backend data developer but have limited frontend experience.

## Current Priorities
1. Implement AI-driven wine pairing features
2. Enable contextual AI conversations about wine selection
3. Improve the editor with pretty-print or Portal-like features
4. Use app-state :view state more broadly throughout the application
5. UI/UX improvements

If you need any specific files or additional context, just ask and I'll provide them directly.


## Source Code Structure

.
├── automation
│   └── postgresql.yml
├── CLAUDE.md
├── deps.edn
├── dev
│   ├── clj
│   │   └── user.clj
│   └── cljs
│       └── user.clj
├── Dockerfile
├── docs
│   ├── ai-drinking-window-feature.md
│   ├── ai-form-fill-feature.md
│   ├── ai-form-fill-implementation.md
│   ├── alcohol-percentage-implementation.md
│   ├── chat-context.md
│   ├── chat-summary.md
│   ├── environment-variables.md
│   ├── ideal-taxonomy.md
│   ├── pairing-feature.md
│   ├── schema-unification-datomic.md
│   └── varieties-implementation.md
├── fly.toml
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
├── scripts
│   ├── format-clj.sh
│   ├── format_zprint.clj
│   ├── pre-commit
│   └── wine_cellar
│       └── dev.clj
├── shadow-cljs.edn
└── src
    ├── clj
    │   └── wine_cellar
    │       ├── ai
    │       │   └── anthropic.clj
    │       ├── auth
    │       │   ├── config.clj
    │       │   └── core.clj
    │       ├── config_utils.clj
    │       ├── db
    │       │   ├── api.clj
    │       │   ├── connection.clj
    │       │   ├── schema.clj
    │       │   └── setup.clj
    │       ├── debug
    │       ├── handlers.clj
    │       ├── routes.clj
    │       └── server.clj
    ├── cljc
    │   └── wine_cellar
    │       └── common.cljc
    └── cljs
        └── wine_cellar
            ├── api.cljs
            ├── config.cljs
            ├── core.cljs
            ├── portal_debug.cljs
            ├── theme.cljs
            ├── utils
            │   ├── filters.cljs
            │   ├── formatting.cljs
            │   └── vintage.cljs
            └── views
                ├── classifications
                │   ├── form.cljs
                │   └── list.cljs
                ├── components
                │   ├── classification_fields.cljs
                │   ├── debug.cljs
                │   ├── form.cljs
                │   ├── image_upload.cljs
                │   ├── portal_debug.cljs
                │   └── wine_card.cljs
                ├── components.cljs
                ├── grape_varieties
                │   └── list.cljs
                ├── main.cljs
                ├── tasting_notes
                │   ├── form.cljs
                │   └── list.cljs
                └── wines
                    ├── detail.cljs
                    ├── filters.cljs
                    ├── form.cljs
                    ├── list.cljs
                    └── varieties.cljs

28 directories, 73 files
