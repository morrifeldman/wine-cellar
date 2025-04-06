# Context for Claude

I am making a wine cellar tracking web app. It is a learning project. I'm good
with back end Clojure and am using Clojurescript for the front end. I currently
use Viveno, but I'm a bit frustrated with Viveno. Too many ads and difficult to
do the simple tracking that I want. I've made some good progress so far.

Please follow these instructions as we work together. Please
consider this context but don't jump right into the task till I focus you.
Sometimes I will want to have short conversations focused on tooling setup. I
use nvim. Please do not give an answer if you want more information or files --
I'd rather just give you the data.  Don't bother using shell tools -- just ask
me and I will be happy to provide any files directly.  It is really important
that we work incrementally, so please only suggest one change at a time to give
me a chance to test it before we move on.

I strongly favor a declarative approach to code, and I like to minimize
duplication and create reusable abstractions when it makes sense.

I'm an experience back end data developer but I have little front end
experience.  Nonetheless, I don't generally need any code comments.

# Source Code Structure

.
├── automation
│   ├── deploy-to-fly.sh
│   └── postgresql.yml
├── deps.edn
├── dev
│   └── user.clj
├── Dockerfile
├── docs
│   ├── chat-context.md
│   ├── chat-summary.md
│   ├── ideal-taxonomy.md
│   └── schema-unification-datomic.md
├── fly.toml
├── package.json
├── package-lock.json
├── public
│   └── index.html
├── README.md
├── resources
│   └── wine-classifications.edn
├── shadow-cljs.edn
└── src
    ├── clj
    │   └── wine_cellar
    │       ├── db
    │       │   ├── api.clj
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
                ├── classifications
                │   └── form.cljs
                ├── components
                │   └── form.cljs
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

20 directories, 40 files

## Technical Stack
- Backend: Clojure
- Frontend: ClojureScript with Reagent
- Database: PostgreSQL 15
- UI Framework: Material UI (via reagent-mui)
- Build Tool: Shadow-cljs
- Clojure: tools.deps
- Editor: Neovim

# Next tasks

## General

* Manage/Edit Classifications
* Add a picture field
* Use AI to fill add wine based on the label picture

