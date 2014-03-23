(ns cider-spy-nrepl.hub.server-events)

(def registrations (atom #{}))

(defmulti process (comp keyword :type))

(defmethod process :register [{:keys [id]}]
  (swap! registrations conj id))
