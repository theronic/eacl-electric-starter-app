(ns electric-starter-app.data.datomic-contrib
  "Missionary adapters to turn Datomic tx-report-queue into an observable Missionary stream."
  (:require
    [datomic.api :as d]
    [missionary.core :as m]))

(defn next-db< [conn]
  (let [q (d/tx-report-queue conn)]
    (m/observe (fn [!]
                 (! (d/db conn))
                 (let [t (Thread. ^Runnable
                           #(when (try (! (:db-after (.take ^java.util.concurrent.LinkedBlockingQueue q)))
                                       true
                                       (catch InterruptedException _))
                              (recur)))]
                   (.start t)
                   #(doto t .interrupt .join))))))

;; Datomic only allows a single queue consumer, so we need to spawn a singleton here
;; In the next Electric iteration we can use `m/signal` and clean this up

(defonce !takers (atom {}))

(defn init-taker! [!db conn uri]
  (when-let [!taker (get @!takers uri)]
    (!taker))
  (swap! !takers assoc uri ((m/reduce #(reset! !db %2) nil (next-db< conn)) identity identity)))

(defn stop-taker! [datomic-uri]
  (when-let [taker (get @!takers datomic-uri)]
    (taker)))