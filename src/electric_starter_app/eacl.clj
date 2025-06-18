(ns electric-starter-app.eacl
  (:require [electric-starter-app.data.config :refer [conn]]
            [eacl.datomic.core]
            [eacl.core :as eacl :refer [spice-object]]
            [mount.core :as mount :refer [defstate]]))

(defstate acl ; named `acl` to distinguish from generic `client`, `e/client` or `eacl`.
  :start (eacl.datomic.core/make-client conn)
  :stop nil)

; Helpers are specific to cloud hosting
(def ->user (partial spice-object :user))
(def ->team (partial spice-object :team))
(def ->server (partial spice-object :server))
(def ->platform (partial spice-object :platform))
(def ->account (partial spice-object :account))
(def ->vpc (partial spice-object :vpc))
(def ->backup (partial spice-object :backup))
(def ->host (partial spice-object :host))
