(ns cider-spy-nrepl.hub.server-events
  (:require [clojure.tools.logging :as log])
  (:import [java.util UUID]))

(def registrations (atom {}))

(defmulti process (comp keyword :op))

(defmethod process :default [m]
  (println "Did not understand message" m))

(defmethod process :register [{:keys [session-id alias]}]
  (swap! registrations assoc session-id alias)
  (log/info "Registrations Updated:" (vals @registrations))
  {:op :registered
   :alias alias
   :registered (set (vals @registrations))})

(defn unregister! [session-id]
  (swap! registrations dissoc session-id))
