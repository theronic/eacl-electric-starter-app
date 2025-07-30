(ns electric-starter-app.main
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            #?(:clj [eacl.datomic.schema :as schema])
            #?(:clj [datomic.api :as d])
            #?(:clj [electric-starter-app.data.config :as data.config :refer [conn]])
            #?(:clj [electric-starter-app.data.globals :as g])
            #?(:clj [electric-starter-app.eacl :as authz :refer [acl ->user]])
            #?(:clj [clojure.tools.logging :as log])
            #?(:clj [eacl.core :as eacl])))


#?(:clj (defn try-lookup-resources [acl qry]
          (log/debug try-lookup-resources qry)

          (try
            (let [{:as   result
                   :keys [data cursor]} (eacl/lookup-resources acl qry)]
              ;(log/debug 'try-lookup-resources 'results (count data))
              result)
            ; temp. exception.
            (catch Exception ex
              (log/error 'try-lookup-resources-ex ex)
              {:data   []
               :error  (ex-message ex)
               :cursor nil}))))

(e/defn PaginationButtons [!page-number]
  (e/client
    (dom/div
      (dom/button
        (dom/On "click" (fn [_] (reset! !page-number 1)) nil)
        (dom/text "<< First Page"))

      (dom/button
        (dom/On "click" (fn [_] (swap! !page-number dec)) nil)
        (dom/text "Prev Page"))

      (dom/button
        (dom/On "click" (fn [_] (swap! !page-number inc)) nil)
        (dom/text "Next Page"))

      (dom/button
        (dom/On "click" (fn [_] (reset! !page-number 1)) nil)
        (dom/text "Last Page >>")))))

(e/defn CursorPaginationButtons [!cursors next-cursor]
  (e/client
    (let [cursors (e/watch !cursors)]
      (dom/div
        (dom/button
          (dom/On "click" (fn [_] (reset! !cursors '())) nil)
          (dom/text "<< First Page"))

        (dom/button
          (dom/On "click" (fn [_] (swap! !cursors pop)) nil)
          (dom/props {:disabled (empty? cursors)})
          (dom/text "Prev Page"))

        (dom/button
          (dom/On "click" (fn [_] (swap! !cursors conj next-cursor)) nil)
          (dom/text "Next Page"))

        #_(dom/button
            (dom/On "click" (fn [_] (reset! !page-number 1)) nil)
            (dom/text "Last Page >>"))))))


(e/defn AccountServerList [db acl account-id !server]
  (e/client
    (let [selected-server (e/watch !server)

          !cursors        (atom (list))                     ; todo initial value.
          cursors         (e/watch !cursors)
          cursor          (peek cursors)
          limit           50]
      (e/server
        (let [account        (if account-id (authz/->account account-id)) ; if nil, should probably not query.

              {:as         result
               error       :error
               next-cursor :cursor
               paginated   :data} (if account
                                    (e/Offload #(try-lookup-resources acl {:subject       account
                                                                           :permission    :view
                                                                           :resource/type :server
                                                                           :cursor        cursor
                                                                           :limit         limit}))
                                    {:data   []
                                     :error  "missing account-id"
                                     :cursor nil})
              server-ids (for [result paginated]
                           [:eacl/id (:id result)])
              hydrated       (e/Offload #(d/pull-many db '[:db/id
                                                           :eacl/id
                                                           :server/name]
                                                      server-ids))

              diffed-servers (e/diff-by :id hydrated)
              total-count    (if account (e/Offload #(eacl/count-resources acl {:subject       account
                                                                                :permission    :view
                                                                                :resource/type :server}))
                                         0)]
          (e/client

            (dom/h1 (dom/text total-count " Account Servers"))

            (CursorPaginationButtons !cursors next-cursor)

            (dom/ul
              (e/for [server diffed-servers]
                (let [obj-id (:eacl/id server)]
                  (dom/li
                    (dom/props {:style {:background-color (if (= selected-server obj-id) "yellow" "inherit")}})
                    (dom/On "click" (fn [_e] (reset! !server obj-id)) nil)
                    (dom/text "Server: " (e/server (pr-str server)))))))))))))

(e/defn UserAccountList [db acl user-id !account-id]
  (e/client
    (let [selected-account-id (e/watch !account-id)

          !cursors            (atom (list))                 ; todo initial value.
          cursors             (e/watch !cursors)
          cursor              (peek cursors)

          ;!page-number (atom 1)
          ;page-number  (e/watch !page-number)

          ;offset       (* (dec page-number) page-size)
          limit               50]
      (e/server
        (let [user        (if user-id (authz/->user user-id))

              query       {:subject       user
                           :permission    :view
                           :resource/type :account
                           :cursor        cursor
                           :limit         limit}

              _           (case query
                            (prn 'query query))

              {:as         result
               error       :error
               next-cursor :cursor
               paginated   :data} (if user
                                    (e/Offload #(try-lookup-resources acl query))
                                    {:data   []
                                     :error  "missing user-id"
                                     :cursor nil})
              hydrated    (d/pull-many db '[*]
                                       (for [result paginated]
                                         [:eacl/id (:id result)]))
              ;[:eacl/id (:id result)]))

              total-count (if user (e/Offload #(eacl/count-resources acl {:subject       user
                                                                          :permission    :view
                                                                          :resource/type :account}))
                                   0)

              diffed      (e/diff-by :eacl/id hydrated)]
          (e/client
            ;(dom/text "Servers todo fix")
            (dom/h1 (dom/text total-count " User Accounts"))

            ;(dom/text "Cursors:" (pr-str cursors))
            (CursorPaginationButtons !cursors next-cursor)
            ;(PaginationButtons !page-number)

            (dom/ul
              (e/for [account diffed]
                (let [account-id (:eacl/id account)]
                  (dom/li
                    (dom/props {:style {:background-color (if (= selected-account-id account-id) "yellow" "inherit")}})
                    (dom/On "click" (fn [_e] (reset! !account-id account-id)) nil)
                    (dom/text "Account: " (e/server (pr-str account)))))))))))))

(comment
  ()
  (try-lookup-resources acl {:subject       (->user 17592186490487) ; user            ; todo Offload.
                             :permission    :view
                             :resource/type :account
                             :cursor        nil             ; cursor
                             :limit         50}))           ; limit}))

(e/defn UserServerList [db acl user-id !server]
  (e/client
    (let [current-server (e/watch !server)

          !cursors       (atom (list))                      ; todo initial value.
          cursors        (e/watch !cursors)
          cursor         (peek cursors)

          ;!page-number (atom 1)
          ;page-number  (e/watch !page-number)

          ;offset       (* (dec page-number) page-size)
          limit          50]
      (e/server
        (let [user           (if user-id (authz/->user user-id))

              {:as         result
               error       :error
               next-cursor :cursor
               paginated   :data} (if user
                                    (e/Offload #(try-lookup-resources acl {:subject       user
                                                                           :permission    :view
                                                                           :resource/type :server
                                                                           :cursor        cursor
                                                                           :limit         limit}))
                                    {:data   []
                                     :error  "missing user-id"
                                     :cursor nil})
              hydrated       (d/pull-many db '[:db/id ; offload this?
                                               :eacl/id
                                               :server/name]
                                          (for [result paginated]
                                            [:eacl/id (:id result)]))

              total-count    (if user (e/Offload #(eacl/count-resources acl {:subject       user
                                                                             :permission    :view
                                                                             :resource/type :server}))
                                      0)

              diffed-servers (e/diff-by :eacl/id hydrated)]
          (e/client
            ;(dom/text "Servers todo fix")
            (dom/h1 (dom/text total-count " User Servers"))

            ;(dom/text "Cursors:" (pr-str cursors))
            (CursorPaginationButtons !cursors next-cursor)
            ;(PaginationButtons !page-number)

            (dom/ul
              (e/for [server diffed-servers]
                (let [obj-id (:eacl/id server)]
                  (dom/li
                    (dom/props {:style {:background-color (if (= current-server obj-id) "yellow" "inherit")}})
                    (dom/On "click" (fn [_e] (reset! !server obj-id)) nil)
                    (dom/text "Server: " (e/server (pr-str server)))))))))))))

(comment
  (try-lookup-resources acl {:subject       (->user [:eacl/id "user-1"])
                             :permission    :view
                             :resource/type :server
                             :cursor        {:path-index  0
                                             :resource-id 17592186045463}
                             ;{:type :user
                             ; :id 17592186045463}} ;cursor
                             :limit         5}))            ;limit}))

;#?(:clj
;   (defn qry-user-ids [db]
;     (d/q '[:find [?obj-id ...]
;            :where
;            [?user :eacl/type :user]
;            [?user :eacl/id ?obj-id]]
;          db)))

#?(:clj (defn spice-object->entity [db {:as obj :keys [type id]}]
          (if-let [eid (d/entid db id)]                     ; we get back DB ID now. [:eacl/id id])]
            (d/entity db eid)
            (do
              (log/warn "Missing :eacl/id" id)
              nil))))

(comment
  (spice-object->entity (d/db conn) {:id 123}))

#?(:clj
   (defn lookup-users-with-accounts [db acl]
     ; note: this enumerates everything.
     (try
       (->> (eacl/read-relationships acl {:subject/type  :user
                                          :resource/type :account})
            (map (comp :id :subject))
            (map (fn [obj-id] [:eacl/id obj-id]))
            (d/pull-many db '[*]))
       ;(map (partial spice-object->entity db))
       ;(remove nil?)                                   ; should not need this
       ;(map d/touch))
       (catch Exception ex
         (log/error "Exception " ex)
         []))))

(comment
  (d/entid (d/db conn) [:eacl/id "user-1"])
  (count (take 30 (lookup-users-with-accounts (d/db conn) acl))))

#?(:clj
   (defn lookup-users-for-account [db acl account]
     (try
       (->> (eacl/lookup-subjects acl {:resource     account
                                       :permission   :view
                                       :subject/type :user})
            (:data)
            (remove nil?)
            (map (partial spice-object->entity db))
            (remove nil?)                                   ; why is this an issue?
            (map d/touch))
       (catch Exception ex
         (log/error 'lookup-users-for-account-ex ex)
         []))))

#?(:clj
   (defn lookup-accounts-for-user [db acl user]
     (->> (eacl/lookup-resources acl {:subject       user
                                      :permission    :view
                                      :resource/type :account})
          (:data)
          (remove nil?)                                     ; should never happen
          (map (partial spice-object->entity db))
          (remove nil?)
          (map d/touch))))

#?(:clj
   (defn qry-accounts [db]
     (d/q '[:find [(pull ?account [*]) ...]
            :where
            [?server :server/account ?account]]
          ;[?account :eacl/type :account]
          ;[?account :eacl/id]]
          db)))

(e/defn PaginatedList [{:keys [total page-size limit offset]} coll diff-fn RenderItem]
  (e/client
    (let [!page-number (atom 1)
          page-number  (max 0 (dec (e/watch !page-number)))
          offset       (* page-number page-size)
          paginated    (->> coll (drop offset) (take limit))
          diffed       (e/diff-by diff-fn paginated)]
      (dom/ul
        (e/server
          (e/for [item diffed]
            (let [obj-id (:eacl/id item)]
              (e/client
                (dom/li
                  ;(dom/On "click" (fn [_e] (on-change-account obj-id)) nil)
                  ; todo link?
                  (dom/text "Object " (e/server (pr-str obj-id))))))))))))

(e/defn AllAccountList [db acl on-change-account]
  (e/client
    (let [!page-number (atom 1)
          page-number  (e/watch !page-number)

          page-size    50

          offset       (* page-size (dec page-number))
          limit        page-size]
      (e/server
        (let [accounts        (e/Offload #(qry-accounts db))
              sorted          (sort-by :db/id accounts)     ; (reverse (sort-by :db/id accounts)) ; temp reverse.
              paginated       (->> sorted (drop offset) (take limit))
              diffed-accounts (e/diff-by :db/id paginated)]
          (e/client
            (dom/h2 (dom/text (e/server (count accounts)) " Accounts:"))
            (dom/ul
              (PaginationButtons !page-number)
              ;(e/server (do (debug-data diffed-accounts)))
              ; if you change this to e/server, Electric crashes on 3rd page
              (e/for [account diffed-accounts]
                (let [obj-id (:eacl/id account)]
                  (e/client
                    (dom/li
                      (dom/On "click" (fn [_e] (on-change-account obj-id)) nil)
                      (dom/text "Account " (e/server (pr-str account))))))))))))))

(e/defn AccountList [db acl user-id on-change-account]
  (e/server
    (let [user            (authz/->user user-id)
          accounts        (e/Offload #(lookup-accounts-for-user db acl user)) ; (qry-user-ids db) ; or use lookup-users-with-accounts
          diffed-accounts (e/diff-by :db/id accounts)]
      (e/client
        (dom/h2 (dom/text (e/server (count accounts)) " User Accounts:"))
        (dom/ul
          (e/server
            (e/for [account diffed-accounts]
              (let [obj-id (:eacl/id account)]
                (e/client
                  (dom/li
                    (dom/On "click" (fn [_e] (on-change-account obj-id)) nil)
                    ; todo link?
                    (dom/text "Account " (e/server (pr-str account)))))))))))))

(comment
  (let [limit     50
        offset    150
        db        (d/db conn)
        users     (lookup-users-with-accounts db acl)       ; (qry-user-ids db) ; or use lookup-users-with-accounts
        sorted    (->> users (sort-by :db/id))
        paginated (->> sorted (drop offset) (take limit))]
    ;diffed-page (e/diff-by :db/id paginated)]
    (count paginated)))                                     ;(e/as-vec diffed-page))))

(e/defn UserList [db acl !user]
  (e/client
    (let [selected-user (e/watch !user)

          !page-number  (atom 1)
          page-number   (e/watch !page-number)
          page-size     50]
      (e/server
        (let [offset      (* page-size (dec page-number))
              limit       page-size

              users       (e/Offload #(lookup-users-with-accounts db acl))
              sorted      (->> users (sort-by :db/id))
              paginated   (->> sorted (drop offset) (take limit))
              diffed-page (e/diff-by :db/id paginated)]     ; should be :db/id

          ;_           (prn 'diffed-page diffed-page)]
          (e/client
            (dom/h2 (dom/text (e/server (count sorted)) " Users"))
            (dom/p (dom/text "Click to change Authenticated User:"))

            (PaginationButtons !page-number)

            (dom/ul
              (e/client
                (e/for [user diffed-page]                   ; if run in e/server, this e/for fails on 3rd page of pagination, regardless of page size.
                  (let [obj-id (:eacl/id user)]
                    (e/client
                      (dom/li
                        (dom/props {:style {:background-color (if (= selected-user obj-id) "yellow" "inherit")}})
                        (dom/On "click" (fn [_e] (reset! !user obj-id)) nil)
                        ; todo link?
                        (dom/text "User " (e/server (pr-str user)))))))))))))))

(e/defn AccountUserList [db acl account-id on-change-user]
  (e/client
    (let [!page-number (atom 1)
          page-number  (e/watch !page-number)
          page-size    50]
      (e/server
        (let [offset      (* page-size (dec page-number))
              limit       page-size

              account-eid (d/entid db [:eacl/id account-id]) ; to check for existence. EACL will throw if missing.
              account     (authz/->account account-id)

              users       (if account-eid (e/Offload #(lookup-users-for-account db acl account)) [])
              total-count (count users)
              sorted      (->> users (sort-by :db/id))
              paginated   (->> sorted (drop offset) (take limit))
              diffed-page (e/diff-by :db/id paginated)
              _           (prn 'diffed-page diffed-page)]
          (e/client
            (dom/h2 (dom/text (e/server (count sorted)) " of " total-count " Account Users"))
            (dom/p (dom/text "Click to change Authenticated User:"))

            ;(dom/button
            ;  (dom/On "click" (fn [_e] (on-change-user "super-user")) nil)
            ;  (dom/text "Select Super User"))

            (PaginationButtons !page-number)

            (dom/ul
              (e/server
                (e/for [user diffed-page]
                  (let [obj-id (:eacl/id user)]
                    (e/client
                      (dom/li
                        (dom/On "click" (fn [_e] (on-change-user obj-id)) nil)
                        ; todo link?
                        (dom/text "User " (e/server (pr-str user)))))))))))))))

(e/defn Authenticated [user-id]
  (e/server
    (e/client
      (dom/div
        (dom/h1 (dom/text "Viewing as " user-id))))))

(e/defn LookupSubjects [acl subject-type permission resource !subject]
  (e/client
    (let [selected-subject (e/watch !subject)]
      (e/server
        (let [lookup-query {:subject/type subject-type
                            :permission   permission
                            :resource     resource}
              ;subject-count (e/Offload #(eacl/count-subjects acl lookup-query )) ; todo impl. but dissoc :limit & cursor.
              subjects (e/Offload #(->> (eacl/lookup-subjects acl lookup-query)
                                        (:data)))
              diffed   (e/diff-by :id subjects)]
          (e/client
            (dom/div
              ;(dom/text (e/server (pr-str subject-type permission resource)))
              (dom/text "Subjects who can " (e/server (pr-str permission)) " " (e/server (pr-str resource)))
              (dom/ul
                (e/for [subject diffed]
                  (let [obj-id (:id subject)]
                    (dom/li
                      (dom/props {:style {:background-color (if (= selected-subject obj-id) "yellow" "inherit")}})
                      (dom/On "click" (fn [_] (reset! !subject obj-id)) nil)
                      (dom/text (:type subject) " " (:id subject))))))))))))) ;(e/server (pr-str subject)))))))))))

#?(:clj (defn count-servers [db]
          (d/q '[:find (count ?server) .
                 :where
                 [?server :server/name]]
               db)))

(e/defn Main [ring-request]
  (e/server
    (let [db (e/watch g/!db)]
      (e/client
        (let [!signed-in-user-id  (atom "user-1")           ; :eacl/id in Datomic = Spice Object ID
              signed-in-user-id   (e/watch !signed-in-user-id)
              !account-id         (atom nil)
              account-id          (e/watch !account-id)

              !selected-server-id (atom nil)
              selected-server-id  (e/watch !selected-server-id)] ; "user-1")]              ; just a default. doesn't belong on client. just for testing.
          (binding [dom/node js/document.body]              ; DOM nodes will mount under this one
            (dom/div                                        ; mandatory wrapper div to ensure node ordering - https://github.com/hyperfiddle/electric/issues/74

              (dom/h1 (dom/text "Electric EACL Demo"))
              (dom/h2 (dom/text "Total Server Count: " (e/server (count-servers db))))

              (dom/div
                (dom/props {:style {:display               "grid"
                                    :grid-template-columns "1fr 1fr 1fr 1fr 1fr"}})

                (dom/div
                  (dom/p (dom/text "Selected user: " signed-in-user-id))
                  (dom/button
                    (dom/On "click" #(reset! !signed-in-user-id "super-user") nil)
                    (dom/text "Select Super User"))
                  (UserList db acl !signed-in-user-id))

                (dom/div
                  (UserAccountList db acl signed-in-user-id !account-id))

                (dom/div
                  (dom/p (dom/text "Selected Server: " selected-server-id))
                  (when true                                ; false
                    (UserServerList db acl signed-in-user-id !selected-server-id)))

                (dom/div
                  (dom/p (dom/text "Selected Account: " account-id))
                  (when true                                ; false ; true                                ; false
                    (AccountServerList db acl account-id !selected-server-id)))

                (dom/div
                  (dom/text "Selected Server")
                  (when selected-server-id
                    (LookupSubjects acl :user :view
                                    (e/server (authz/->server selected-server-id))
                                    !signed-in-user-id)))))))))))

(defn electric-boot [ring-request]
  #?(:clj  (e/boot-server {} Main (e/server ring-request))  ; inject server-only ring-request
     :cljs (e/boot-client {} Main (e/server (e/amb)))))     ; symmetric – same arity – no-value hole in place of server-only ring-request
