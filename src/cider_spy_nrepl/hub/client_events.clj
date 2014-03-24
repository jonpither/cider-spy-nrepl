(ns cider-spy-nrepl.hub.client-events)

(def registrations (atom #{}))

(defmulti process (comp keyword :op))

(defmethod process :default [m]
  (println "Did not understand message from hub" m))

(defmethod process :registered [{:keys [alias registered] :as msg}]
  (reset! registrations registered)
  (println (format "Registered: %s" alias)))
