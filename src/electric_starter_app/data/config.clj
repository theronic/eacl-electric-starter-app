(ns electric-starter-app.data.config
  (:require
    [electric-starter-app.data.globals :as g]
    [electric-starter-app.data.datomic-contrib :as dc]
    [datomic.api :as d]
    [mount.core :as mount :refer [defstate]]))

(def datomic-uri "datomic:mem://electric-eacl")

; For benchmarks:
;(def datomic-uri "datomic:dev://localhost:4597/electric-eacl")

(defstate conn
  :start (try
           (d/create-database datomic-uri)                  ; usually factored out as part of migration.
           (let [conn (d/connect datomic-uri)]
             (dc/init-taker! g/!db conn datomic-uri)
             conn))
  :stop (do
          (when conn (.release conn))
          (dc/stop-taker! datomic-uri)
          nil))

(comment
  datomic-uri
  (d/q '[:find (count ?server) .
         :where
         [?server :eacl/type :server]]
       (d/db conn)))