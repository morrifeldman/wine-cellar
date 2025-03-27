# Schema Unification and Datomic Evaluation

This document summarizes our discussion about schema unification approaches and evaluates whether Datomic would be a good fit for the Wine Cellar application.

## Current Schema Challenges

The Wine Cellar app currently maintains three separate schemas:

1. **Frontend Validation** (in `wines/form.cljs`):
   ```clojure
   (defn validate-wine []
     (cond
       (not (valid-name-producer? new-wine)) "Either Wine Name or Producer must be provided"
       (empty? (:country new-wine)) "Country is required"
       ;; more validations...
     ))
   ```

2. **Route Specs** (in `routes.clj`):
   ```clojure
   (def wine-schema
     (s/keys :req-un [(or ::name ::producer)
                      ::country
                      ::region
                      ::vintage
                      ::styles
                      ::quantity
                      ::price]
             :opt-un [::aoc ...]))
   ```

3. **Database Schema** (in `schema.clj`):
   ```clojure
   (def wines-table-schema
     {:create-table [:wines :if-not-exists]
      :with-columns
      [[:id :serial :primary-key]
       [:producer :varchar]
       [:country :varchar [:not nil]]
       ;; more columns...
      ]})
   ```

This duplication creates potential inconsistencies and maintenance overhead.

## Schema Unification Approaches

### 1. Shared Spec Definitions in CLJC

**Concept**: Define core specs in a `.cljc` file that can be used by both Clojure and ClojureScript.

```clojure
;; src/cljc/wine_cellar/specs.cljc
(ns wine-cellar.specs
  (:require [clojure.spec.alpha :as s]
            [wine-cellar.common :as common]))

;; Core specs
(s/def ::producer string?)
(s/def ::country string?)
;; more specs...

;; Entity specs
(s/def ::wine
  (s/keys :req-un [(or ::name ::producer)
                   ::country
                   ::region
                   ::vintage
                   ::styles
                   ::quantity
                   ::price]
          :opt-un [::aoc ...]))
```

**Benefits**:
- Single source of truth for validation rules
- Shared between frontend and backend
- Leverages spec's composability

### 2. Spec-Driven Schema Generation

**Concept**: Generate database schema from specs.

```clojure
;; src/clj/wine_cellar/db/schema.clj
(ns wine-cellar.db.schema
  (:require [wine-cellar.specs :as specs]
            [clojure.spec.alpha :as s]))

(defn spec->column-type [spec-pred]
  (cond
    (= spec-pred string?) :varchar
    (= spec-pred int?) :integer
    ;; more mappings...
    ))

(defn spec->schema [entity-spec]
  (let [req-keys (s/form entity-spec)
        ;; Extract required and optional keys from spec
        ]
    ;; Generate HoneySQL schema
    ))

(def wines-table-schema
  (spec->schema ::specs/wine))
```

**Benefits**:
- Database schema directly derived from specs
- Automatic consistency between validation and storage
- Reduced duplication

### 3. Malli - A Modern Alternative to Spec

**Concept**: Use [Malli](https://github.com/metosin/malli) which is designed for schema unification across contexts.

```clojure
;; deps.edn
{:deps {metosin/malli {:mvn/version "0.14.0"}}}

;; src/cljc/wine_cellar/schemas.cljc
(ns wine-cellar.schemas
  (:require [malli.core :as m]
            [malli.transform :as mt]))

(def Wine
  [:map
   [:name {:optional true} string?]
   [:producer {:optional true} string?]
   [:country string?]
   [:region string?]
   [:vintage int?]
   [:styles [:vector keyword?]]
   [:quantity int?]
   [:price double?]
   ;; more fields...
   ])

;; Validation function that works in both CLJ and CLJS
(defn validate-wine [wine]
  (m/validate Wine wine))

;; Transform for API/DB conversion
(def json-transformer
  (mt/transformer
   mt/json-transformer
   ;; Custom transformations
   ))
```

**Benefits**:
- Purpose-built for schema unification
- Better error messages than spec
- Built-in transformations between formats
- Works well with both frontend and backend

## Datomic Evaluation for Wine Cellar App

### Potential Benefits

#### 1. Time-Based Data & History

Datomic's immutable, time-based data model would be excellent for:
- **Wine aging history**: Track how your ratings and notes evolve as wines age
- **Inventory changes**: See when bottles were added/consumed with perfect historical accuracy
- **Tasting window predictions**: Compare actual aging patterns against predicted drinking windows

#### 2. Schema Evolution

Your current challenge with three schemas could be simplified:
- **Unified schema approach**: Datomic schemas are extensible and can be shared across your stack
- **Schema evolution**: Add new attributes to wines without migrations
- **Spec integration**: Datomic schemas align well with Clojure Spec

#### 3. Query Capabilities

For a wine collection, Datomic's query capabilities are powerful:
- **Complex queries**: Find wines that improved with age based on tasting notes
- **Temporal queries**: "What did my cellar look like last year?" or "When was this wine at its peak?"
- **Datalog expressiveness**: More declarative than SQL for complex wine classification hierarchies

### Implementation Considerations

#### 1. Scale & Complexity Trade-off

For a personal wine cellar app:
- **Pro**: Datomic's features are valuable even at small scale
- **Con**: Introduces more complexity than PostgreSQL for a learning project

#### 2. Development Experience

- **Pro**: Fully Clojure-native database with excellent REPL integration
- **Pro**: Would eliminate your current schema duplication issues
- **Con**: Steeper learning curve than continuing with PostgreSQL

### Unified Schema Example with Datomic

```clojure
;; src/cljc/wine_cellar/schema.cljc
(ns wine-cellar.schema
  (:require 
    #?(:clj [datomic.api :as d])
    [clojure.spec.alpha :as s]))

;; Shared specs that work in CLJ and CLJS
(s/def ::producer string?)
(s/def ::country string?)
(s/def ::vintage int?)
;; etc...

;; Entity spec
(s/def ::wine
  (s/keys :req-un [(or ::name ::producer)
                   ::country
                   ::region
                   ::vintage]
          :opt-un [::aoc ::classification]))

;; Datomic schema (CLJ only)
#?(:clj
   (def wine-schema
     [{:db/ident :wine/name
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      {:db/ident :wine/producer
       :db/valueType :db.type/string
       :db/cardinality :db.cardinality/one}
      ;; etc...
      ]))
```

## Recommendation

### For Schema Unification

1. **Start with shared specs in CLJC**:
   - Move common specs to `src/cljc/wine_cellar/specs.cljc`
   - Keep existing route specs but have them reference the shared specs
   - Update frontend validation to use the specs

2. **Consider Malli** if you're open to new libraries, as it offers the most comprehensive solution for schema unification.

### For Datomic Adoption

1. **If learning is a primary goal**: Datomic would be an excellent addition to your learning project, especially if you're interested in exploring more advanced Clojure ecosystem tools.

2. **If completing the app quickly is the goal**: Stick with PostgreSQL for now, but refactor to use a unified schema approach with `.cljc` files.

3. **Middle ground**: Continue with PostgreSQL but implement a unified schema approach that would make a future migration to Datomic easier.

## Practical Migration Path

If you decide to try Datomic:

1. Start with Datomic Dev-Local (free, embedded version)
2. Create a unified schema in a `.cljc` file
3. Implement a migration strategy to move your existing data
4. Update your API layer to use Datomic's query capabilities

The wine cellar domain is particularly well-suited to Datomic's strengths in historical data tracking and complex relationships, making it a compelling option despite the additional learning curve.
