(ns cider-spy-nrepl.middleware.sessions
  (:import [org.joda.time LocalDateTime]))

(def sessions (atom {}))

(defn- new-session [{:keys [session transport]}]
  (atom {:id session :transport transport :session-started (LocalDateTime.)}))

(defn session!
  "Return the session for the given msg.
   If a session does not exist then one will be created."
  [{:keys [session] :as msg}]
  (when session
    (or (get @sessions session)
        (get (swap! sessions assoc session (new-session msg)) session))))
