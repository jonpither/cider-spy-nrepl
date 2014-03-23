(ns cider-spy-nrepl.hub.client-events)

(def registrations (atom #{}))

(defmulti process (comp keyword :op))

(defmethod process :default [m]
  (println "Did not understand message from hub" m))

(defmethod process :register [{:keys [alias]}]
  (swap! registrations conj alias)
  (println "Spy Agent, Registrations Updated:" @registrations))
