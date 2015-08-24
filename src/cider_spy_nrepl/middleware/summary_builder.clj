(ns cider-spy-nrepl.middleware.summary-builder
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
  [{:keys [session-started registrations tracking]}]
  (let [{:keys [ns-trail commands nses-loaded]} tracking]
    {:ns-trail (->> ns-trail
                    remove-duplicate-entries
                    enrich-with-duration
                    (map #(dissoc % :dt)))
     :nses-loaded nses-loaded
     :fns commands
     :devs registrations
     :session {:started (.toString session-started "hh:mm:ss")
               :seconds (.getSeconds (Seconds/secondsBetween session-started
                                                             (LocalDateTime.)))}}))
