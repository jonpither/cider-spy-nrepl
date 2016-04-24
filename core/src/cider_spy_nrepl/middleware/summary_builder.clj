(ns cider-spy-nrepl.middleware.summary-builder
  (:require [cider-spy-nrepl.middleware.session-vars :refer :all])
  (:import (org.joda.time LocalDateTime Seconds)))

(defn- remove-duplicate-entries
  "[:a :a :b] -> [:a :b]"
  [msgs]
  (when msgs
    (map last (partition-by :ns msgs))))

(defn- seconds-between [msg1 msg2]
  (when-not (or (nil? msg1) (nil? msg2))
    (.getSeconds (Seconds/secondsBetween (:dt msg1) (:dt msg2)))))

(defn enrich-with-duration [msgs]
  (reduce (fn [v msg]
            (conj v (assoc msg :seconds (seconds-between msg (last v)))))
          []
          msgs))

(defn summary
  "Build a summary of the users REPL session."
  [session]
  (let [cider-spy-session @(session #'*cider-spy-session*)
        {:keys [ns-trail commands nses-loaded]} (cider-spy-session #'*tracking*)
        cider-spy-session-started (cider-spy-session #'*session-started*)]
    {:hub-connection (when-let [{:keys [when-connected alias]} (cider-spy-session #'*hub-connection-details*)]
                       {:started (when when-connected (.toString (LocalDateTime. when-connected) "hh:mm:ss"))
                        :alias alias})
     :ns-trail (->> ns-trail
                    remove-duplicate-entries
                    enrich-with-duration
                    (map #(dissoc % :dt)))
     :nses-loaded nses-loaded
     :fns commands
     :devs (cider-spy-session #'*registrations*)
     :session {:started (.toString cider-spy-session-started "hh:mm:ss")
               :seconds (.getSeconds (Seconds/secondsBetween cider-spy-session-started
                                                             (LocalDateTime.)))}}))
