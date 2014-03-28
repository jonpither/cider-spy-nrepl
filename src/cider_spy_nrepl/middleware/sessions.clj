(ns cider-spy-nrepl.middleware.sessions
  (:import [org.joda.time LocalDateTime]))

(def sessions (atom {}))

(defn- new-session [id]
  (atom {:id id :session-started (LocalDateTime.)}))

(defn session!
  "Return the session for the given msg.
   If a session does not exist then one will be created."
  [{:keys [session]}]
  (when session
    (when-not (get @sessions session)
      (swap! sessions assoc session (new-session session)))

    (get @sessions session)))

(defn summary-msg!
  "Update the session with summary message.
   We need to keep a hold of this."
  [session summary-msg]
  (swap! session assoc :summary-msg summary-msg))
