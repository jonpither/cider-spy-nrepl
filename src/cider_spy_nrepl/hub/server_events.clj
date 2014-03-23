(ns cider-spy-nrepl.hub.server-events)

(def registrations (atom #{}))

(defmulti process (comp keyword :op))

(defmethod process :default [m]
  (println "Did not understand message" m))

(defmethod process :register [{:keys [alias]}]
  (swap! registrations conj alias)
  (println "Registrations Updated:" @registrations)
  @registrations)
