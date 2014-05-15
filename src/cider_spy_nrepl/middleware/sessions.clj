(ns cider-spy-nrepl.middleware.sessions
  (:require [cider-spy-nrepl.middleware.alias :as alias])
  (:import [org.joda.time LocalDateTime]))

(def sessions (atom {}))

(defn- new-session [{:keys [session transport]}]
  (atom {:id session :transport transport
         :session-started (LocalDateTime.)
         :hub-alias (alias/alias-from-env)}))

(defn session!
  "Return the session for the given msg.
   If a session does not exist then one will be created.

   Note: if the msg does not contain a `session` attribute then a
   session cannot be created. This is often the case for certain
   nREPL middleware operations such clone and describe.

   Calling code should therefore deal with a nil return value."
  [{:keys [session] :as msg}]
  (when session
    (or (get @sessions session)
        (get (swap! sessions assoc session (new-session msg)) session))))
