(ns cider-spy-nrepl.hub.register
  "Manage HUB registrations.")

(def sessions (atom {}))

(defn register!
  "Register the session.
   This will also update the session with session-id and alias."
  [session id alias]
  (swap! session assoc :id id :alias alias)
  (swap! sessions assoc id session))

(defn unregister!
  "Unregister the session."
  [session]
  (swap! sessions dissoc (:id @session)))

(defn aliases
  "Return aliases of registered sessions."
  []
  (map (comp :alias deref) (vals @sessions)))
