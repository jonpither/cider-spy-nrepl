(ns cider-spy-nrepl.hub.server-events)

(def registrations (atom #{}))

(defmulti process (comp keyword :op))

(defmethod process :register [{:keys [alias]}]
  (swap! registrations conj alias)
  (println "Registrations Updated:" @registrations))
