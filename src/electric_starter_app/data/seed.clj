(ns electric-starter-app.data.seed
  (:require [datomic.api :as d]
            [eacl.datomic.impl :as impl :refer [Relationship Relation Permission]]
            [eacl.datomic.schema :as schema]
            [clojure.tools.logging :as log]
            [electric-starter-app.eacl :as eacl :refer [->user ->account ->server ->team ->platform ->vpc]]))

(declare install-schema+fixtures!)

(comment
  (do
    (require '[electric-starter-app.data.config :as data.config :refer [conn]])
    (install-schema+fixtures! conn)))

(def eacl-schema-fixtures
  [(Relation :platform :super_admin :user)
   (Relation :account :owner :user)                         ; Account has an owner (a user)
   (Relation :account :platform :platform)

   (Permission :account :admin {:relation :owner})                      ; Owner of account gets admin on account
   (Permission :account :admin {:arrow :platform :relation :super_admin})

   ; definition account { permission view = owner + platform->super_admin }
   (Permission :account :view {:relation :owner})
   (Permission :account :view {:arrow :platform :relation :super_admin})

   (Relation :vpc :account :account)                         ; vpc, relation account: account.

   ; vpc { permission admin = account->admin + shared_admin }
   (Relation :vpc :shared_admin :user)
   (Relation :vpc :owner :user)

   ; vpc { permission admin = owner + account->admin + shared_admin
   (Permission :vpc :admin {:relation :owner})
   (Permission :vpc :admin {:arrow :account :permission :admin})
   (Permission :vpc :admin {:relation :shared_admin})              ; just admin?

   ; Teams:
   ; definition team {
   ;   relation account: account
   ; }
   (Relation :team :account :account)

   ;; Servers:
   ; definition {
   ;   relation account: account
   ;   relation shared_admin: user
   ;
   ;   permission view = account + account->admin + shared_admin
   ;   permission edit = account
   ;   permission reboot = account + account->admin + shared_admin
   ;   permission delete = account + account->admin + shared_admin
   ; }
   (Relation :server :account :account)
   (Relation :server :shared_admin :user)

   ; server { permission view = account + account->admin + shared_admin }
   (Permission :server :view {:relation :account})
   (Permission :server :view {:arrow :account :permission :admin})
   (Permission :server :view {:relation :shared_admin})

   ; server { permission edit = account }. need admin here?
   (Permission :server :edit {:relation :account})

   ; server { permission admin = account->admin + shared_admin }
   (Permission :server :admin {:arrow :account :permission :admin})
   (Permission :server :admin {:relation :shared_admin})

   ; some missing from here. todo check.
   ; server { permission delete = account->admin + shared_admin }
   (Permission :server :delete {:arrow :account :permission :admin})
   (Permission :server :delete {:relation :shared_admin})

   ; server { permission reboot = account->admin }
   (Permission :server :reboot {:arrow :account :permission :admin})])

  ; Server Shared Admin:

(def entity-fixtures
  [; Schema

   ;; Entity Fixtures & Relationships:

   ; Global Platform for Super Admins:
   {:db/id     "platform"
    :db/ident  :test/platform
    :eacl/id   "platform"}

   ; Users:
   {:db/id     "user-1"
    :db/ident  :test/user1
    :eacl/id   "user-1"}


   ; Super User can do all the things:
   {:db/id     "super-user"
    :db/ident  :user/super-user
    :eacl/id   "super-user"}

   {:db/id     "user-2"
    :db/ident  :test/user2
    :eacl/id   "user-2"}

   ; Accounts
   {:db/id     "account-1"
    :db/ident  :test/account1
    :eacl/id   "account-1"}

   {:db/id     "account-2"
    :db/ident  :test/account2
    :eacl/id   "account-2"}

   ; VPC
   {:db/id     "vpc-1"
    :db/ident  :test/vpc
    :eacl/id   "vpc-1"}

   {:db/id     "vpc-2"
    :db/ident  :test/vpc2
    :eacl/id   "vpc-2"}

   ; Team
   {:db/id     "team-1"
    :db/ident  :test/team
    :eacl/id   "team-1"}

   ; Teams belongs to accounts

   {:db/id     "team-2"
    :db/ident  :test/team2
    :eacl/id   "team-2"}

   ;; Servers:
   {:db/id     "server-1"
    :db/ident  :test/server1
    :eacl/id   "server-1"}

   {:db/id     "server-2"
    :db/ident  :test/server2
    :eacl/id   "server-2"}])

(def relationship-fixtures
  [(Relationship (->user "user-1") :member (->team "team-1")) ; User 1 is on Team 1
   (Relationship (->user "user-1") :owner (->account "account-1"))
   (Relationship (->user "super-user") :super_admin (->platform "platform"))
   (Relationship (->user "user-2") :owner (->account "account-2"))
   (Relationship (->platform "platform") :platform (->account "account-1"))
   (Relationship (->platform "platform") :platform (->account "account-2"))
   (Relationship (->account "account-1") :account (->vpc "vpc-1"))
   (Relationship (->account "account-2") :account (->vpc "vpc-2"))
   (Relationship (->account "account-1") :account (->team "team-1"))
   (Relationship (->account "account-2") :account (->team "team-2"))
   (Relationship (->account "account-1") :account (->server "server-1"))
   (Relationship (->account "account-2") :account (->server "server-2"))])

(def acl-fixtures (concat eacl-schema-fixtures entity-fixtures relationship-fixtures))

(defn tx! [conn tx-data]
  ;(prn tx-data)
  @(d/transact conn tx-data))

; todo: switch to write-relationships!

(defn ids->tempid-map [uuid-coll]
  (->> uuid-coll
       (reduce (fn [acc uuid]
                 (assoc acc uuid (d/tempid :db.part/user)))
               {})))

(defn make-account-user-txes [account-tempid n]
  (let [user-uuids        (repeatedly n d/squuid)
        user-uuid->tempid (ids->tempid-map user-uuids)]
    (for [user-uuid user-uuids]
      (let [user-tempid (user-uuid->tempid user-uuid)]
        [{:db/id        user-tempid
          :eacl/id      (str user-uuid)
          :user/account account-tempid}                     ; only to police permission checks.
         (impl/Relationship (->user user-tempid) :owner (->account account-tempid))]))))

(defn make-account-server-txes [account-tempid n]
  (let [server-uuids        (repeatedly n d/squuid)
        server-uuid->tempid (ids->tempid-map server-uuids)]

    (for [server-uuid server-uuids]
      (let [server-tempid (server-uuid->tempid server-uuid)]
        [{:db/id          server-tempid
          :server/account account-tempid
          :server/name    (str "Servers " server-uuid)      ; only to police permission checks.
          :eacl/id        (str server-uuid)}
         (impl/Relationship (->account account-tempid) :account (->server server-tempid))]))))

(defn make-account-txes [{:keys [num-users-per-account num-servers-per-account]} account-uuid]
  (let [account-tempid (d/tempid :db.part/user)
        account-txes   [{:db/id     account-tempid
                         :eacl/id   (str account-uuid)}
                        (impl/Relationship (->platform [:eacl/id "platform"]) :platform (->account account-tempid))]
        user-txes      (make-account-user-txes account-tempid num-users-per-account)
        server-txes    (make-account-server-txes account-tempid num-servers-per-account)]
    (concat account-txes (flatten user-txes) (flatten server-txes))))

(defn server->user-ids
  "To validate EACL: directly queries user -> account <- server."
  [db server-id]
  (d/q '[:find [?user-id ...]
         :in $ ?server-id
         :where
         [?server :eacl/id ?server-id]
         [?server :server/account ?account]
         [?user :user/account ?account]
         [?user :eacl/id ?user-id]]
       db server-id))

;(defn tx-seeds
;  "Generate test data with specified number of users, accounts, and servers"
;  [{:keys [num-accounts num-users-per-account num-servers-per-account]}]
;  (let [account-uuids (repeatedly num-accounts d/squuid)
;        nested-seeds  (for [account-uuid account-uuids]
;                        (make-account-txes {:num-users-per-account   num-users-per-account
;                                            :num-servers-per-account num-servers-per-account}
;                                           account-uuid))]
;    (flatten nested-seeds)))

(defn tx-commit-seeds-doseq!
  "Returns promises.
  Todo: batch this.
  Generate test data with specified number of users, accounts, and servers"
  [conn {:keys [num-accounts

                min-users-per-account
                max-users-per-account

                min-servers-per-account
                max-servers-per-account]}]
  (let [account-uuids (repeatedly num-accounts d/squuid)]
    (for [[n account-uuid] (map-indexed vector account-uuids)]
      (do
        (prn 'transacting n 'of num-accounts)
        (let [txes (make-account-txes {:num-users-per-account   (+ min-users-per-account (rand-int (- max-users-per-account min-users-per-account)))
                                       :num-servers-per-account (+ min-servers-per-account (rand-int (- max-servers-per-account min-servers-per-account)))}
                                      account-uuid)]
          (d/transact conn txes))))))


;(defn seed-data! [conn opts]
;  (->> (tx-seeds opts)
;       (d/transact conn)
;       (deref)))

;(defn add-platform-relationship-for-accounts! [conn account-eids]
;  (->>
;    (for [a-eid])))

(defn install-schema+fixtures! [conn]
  ; run (dev/-main) first.

  @(d/transact conn schema/v6-schema)
  @(d/transact conn acl-fixtures)

  ; Add some application-specific attributes for display:
  (tx! conn [{:db/ident       :server/name
              :db/doc         "Just to add some real data into the mix."
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/string
              :db/index       true}

             {:db/ident       :user/account
              :db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref
              :db/index       true}

             {:db/ident       :server/account
              :db/cardinality :db.cardinality/one
              :db/valueType   :db.type/ref
              :db/index       true}])

  (doall
    (map deref (tx-commit-seeds-doseq! conn {:num-accounts            300 ; more than 750 fries in-mem Datomic
                                             :min-users-per-account   10
                                             :max-users-per-account   15
                                             :min-servers-per-account 500
                                             :max-servers-per-account 1000})))
  ; don't return results
  nil)

;(comment
;
;  ; Some attributes to police EACL results:
;
;  (seed-data! conn {:num-accounts            1000
;                    :num-users-per-account   10
;                    :num-servers-per-account 1000})
;
;
;
;  (tx-seeds
;    {:num-accounts 1000
;     :num-users-per-account   100
;     :num-servers-per-account 1000})
;
;  (seed-data! conn)
;
;  (def see)
;
;  (tx-seeds {:num-users-per-account   1
;             :num-account             1
;             :num-servers-per-account 1}))
;
;
; OLD v5 SCHEMA:
;
;; definition platform {
;;   relation super_admin: user;
;;   permission platform_admin = super_admin  ; this is a hack for EACL because we traverse relations via permissions.
;; }
;(Relation :platform/super_admin :user)                   ; means resource-type/relation subject-type, e.g. definition platform { relation super_admin: user }.
;;(Permission :platform :super_admin :platform_admin)      ; hack to support platform->platform_admin
;
;; Accounts:
;; definition account {
;;   relation platform: platform
;;   relation owner: user
;;
;;   permission admin = owner + platform->platform_admin
;;   permission view = owner + platform->platform_admin
;; }
;(Relation :account :owner :user)                         ; Account has an owner (a user)
;(Relation :account :platform :platform)
;(Permission :account :owner :admin)                      ; Owner of account gets admin on account
;(Permission :account :owner :view)
;(Permission :account :platform :platform_admin :admin)   ; hack for platform->super_admin. should no longer be necessary.
;(Permission :account :platform :platform_admin :view)    ; spurious.
;
;; VPCs:
;; definition vpc {
;;   relation account: account ; account is implied
;;   relation shared_admin: user
;;   relation owner: user  # do we need this?
;;
;;   permission admin = owner + shared_admin + account->admin
;; }
;
;(Relation :vpc/account :account)                         ; vpc, relation account: account.
;;permission admin = account->admin + shared_admin
;(Relation :vpc :shared_admin :user)
;(Relation :vpc/owner :user)
;
;(Permission :vpc :shared_admin :admin)
;(Permission :vpc :account :admin :admin)                 ; vpc/admin = account->admin (arrow syntax)
;(Permission :vpc/owner :admin)                           ; just admin?
;
;; Teams:
;; definition team {
;;   relation account: account
;; }
;(Relation :team/account :account)
;
;;; Servers:
;; definition {
;;   relation account: account
;;   relation shared_admin: user
;;
;;   permission view = account + account->admin + shared_admin
;;   permission edit = account
;;   permission reboot = account + account->admin + shared_admin
;;   permission delete = account + account->admin + shared_admin
;; }
;(Relation :server/account :account)
;(Relation :server/shared_admin :user)
;
;(Permission :server/account :view)
;(Permission :server :account :admin :view)
;(Permission :server :account :admin :delete)
;(Permission :server :account :admin :reboot)
;;(Permission :server :account :admin :reboot)
;
;(Permission :server/account :edit)
;; Server Shared Admin:
;(Permission :server/shared_admin :view)
;(Permission :server/shared_admin :admin)
;(Permission :server/shared_admin :delete)
;
;;(Relation :server/company :company)
;; can we combine these into one with multi-cardinality?
;
;;; these can go away
;;(Relation :server/owner :user)
;;
;;(Permission :server/owner :view)
;;;(Permission :server/owner :reboot)
;;(Permission :server/owner :edit)
;;(Permission :server/owner :delete)
