# Context for Claude

I am making a wine cellar tracking web app. It is a learning project. I'm good
with back end Clojure and am using Clojurescript for the front end. I currently
use Viveno, but I'm a bit frustrated with Viveno. Too many ads and difficult to
do the simple tracking that I want. I've made some good progress so far.

Please follow these instructions as we work together. Please consider this
context but don't jump right into the task till I focus you. Sometimes I will
want to have short conversations focused on tooling setup. I use nvim. Please do
not give start answering if you want more information or files -- I'd rather
just give you the data.  Don't bother using shell tools -- just ask me and I
will be happy to provide any files directly.  It is really important that we
work incrementally, so please only suggest one change at a time to give me a
chance to test it before we move on.

I'd like you to be a pair programmer with me -- so while you should suggest
changes, don't get too far ahead of me and let's stay together collaboratively.

I strongly favor a declarative approach to code, and I like to minimize
duplication and create reusable abstractions when it makes sense.

I'm an experience back end data developer but I have little front end
experience.  Nonetheless, I don't generally need any code comments.

After suggesting code, give only a very minimal summary of the changes or no
summary at all.

If you hit the error: "Failed to find the old string" when using the
str_replace_editor tool, please start just suggesting changes into the chat
response itself and I will apply them directly


# Source Code Structure


.
├── automation
│   └── postgresql.yml
├── deps.edn
├── dev
│   └── user.clj
├── Dockerfile
├── docs
│   ├── ai-drinking-window-feature.md
│   ├── ai-form-fill-feature.md
│   ├── ai-form-fill-implementation.md
│   ├── chat-context.md
│   ├── chat-summary.md
│   ├── environment-variables.md
│   ├── ideal-taxonomy.md
│   └── schema-unification-datomic.md
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
│   └── pre-commit
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
            ├── theme.cljs
            ├── utils
            │   ├── filters.cljs
            │   ├── formatting.cljs
            │   └── vintage.cljs
            └── views
                ├── admin
                │   └── schema.cljs
                ├── classifications
                │   └── form.cljs
                ├── components
                │   ├── form.cljs
                │   ├── image_upload.cljs
                │   └── wine_card.cljs
                ├── components.cljs
                ├── main.cljs
                ├── tasting_notes
                │   ├── form.cljs
                │   └── list.cljs
                └── wines
                    ├── detail.cljs
                    ├── filters.cljs
                    ├── form.cljs
                    └── list.cljs

24 directories, 61 files

## Technical Stack
- Backend: Clojure
- Frontend: ClojureScript with Reagent
- Database: PostgreSQL 15
- UI Framework: Material UI (via reagent-mui)
- Build Tool: Shadow-cljs
- Clojure: tools.deps
- Editor: Neovim

### Libraries
- Jsonista for JSON
- metosin/reitit for routing
- http-kit for http client and server

## Deployment
- Fly.io

# Next tasks

## General

* Track wine alcohol percentage
* Track wine grape varietals
* Manage/Edit Classifications + remove classification form code
* Use app-state :view state more broadly
* Extend wine db export to write out to object storage or local machine storage

## Bugs
