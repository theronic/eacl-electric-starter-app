# EACL: Electric Clojure v3 Starter App

This template shows how to use [EACL](https://github.com/cloudafrica/eacl) from Electric Clojure v3 using the [Electric v3 Starter App](https://gitlab.com/hyperfiddle/electric3-starter-app) with cursor-based pagination against large result sets.

Note that some of the pagination buttons (like "Last Page") are not impl.

Cursor-based pagination means you can't show page numbers because you don't know the size of the (potentially large) result set, and traversing to the final page traverses the whole index.

Nevertheless, the total server count uses `count-resources` which does this, which is why it's slow for `(->user "super-user")`, which has access to all servers.

# Quickstart

Start a REPL and run in this order:

```
(dev/-main) ; this starts the `conn` Mount component
(require '[electric-starter-app.data.config :as data.config :refer [conn]])
(require '[electric-starter-app.data.seed :as seed])
(seed/install-schema+fixtures! conn)
```
(This will take a few minutes.)

- Open https://localhost:8088/

You'll see 5 panes:
- Paginated list of users (slow because Datomic query of all ~6k users)
- User Accounts
- User Servers: cursor-paginated list of servers user can :view
- Account Servers: cursor-paginated servers that have an :account Relationship to the selected account 
- Selected Server with a list of Subjects who can :view that server

Clicking on any item will select it.

## Links

* Electric github with source code: https://github.com/hyperfiddle/electric
* Tutorial: https://electric.hyperfiddle.net/

## Getting started

* Shell: `clj -A:dev -X dev/-main`. 
* Login instructions will be printed
* REPL: `:dev` deps alias, `(dev/-main)` at the REPL to start dev build
* App will start on http://localhost:8080
* Electric root function: [src/electric_starter_app/main.cljc](src/electric_starter_app/main.cljc)
* Hot code reloading works: edit -> save -> see app reload in browser

```shell
# Prod build
clj -X:build:prod build-client
clj -M:prod -m prod

# Uberjar (optional)
clj -X:build:prod uberjar :build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# Docker
docker build --build-arg VERSION=$(git rev-parse HEAD) -t electric3-starter-app:latest .
docker run --rm -it -p 8080:8080 electric3-starter-app:latest
```

## License
Electric v3 is **free for bootstrappers and non-commercial use,** and otherwise available commercially under a business source available license, see: [Electric v3 license change](https://tana.pub/lQwRvGRaQ7hM/electric-v3-license-change) (2024 Oct). License activation is experimentally implemented through the Electric compiler, requiring **compile-time** login for **dev builds only**. That means: no license check at runtime, no login in prod builds, CI/CD etc, no licensing code even on the classpath at runtime. This is experimental, but seems to be working great so far. We do not currently require any account approval steps, just log in. There will be a EULA at some point once we finalize the non-commercial license, for now we are focused on enterprise deals which are different.
