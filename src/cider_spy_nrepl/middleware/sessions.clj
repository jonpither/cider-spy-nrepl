(ns cider-spy-nrepl.middleware.sessions
  (:require [cider-spy-nrepl.middleware.alias :as alias])
  (:import (org.joda.time LocalDateTime)))

(defn- new-session [{:keys [transport session]}]
  (atom {:id (:id (meta session))
         :transport transport
         :session-started (LocalDateTime.)
         :hub-alias (alias/alias-from-env)}))

(defn cider-spy-session [nrepl-session]
  (::session @nrepl-session))

(defn session!
  "Return the session for the given msg.
   If a session does not exist then one will be created.

   Note: if the msg does not contain a `session` attribute then a
   session cannot be created. This is often the case for certain
   nREPL middleware operations such clone and describe.

   Nil will also be returned if the msg concerns CIDER tooling
   operations. We want to ignore these, see the tooling-session
   ns for more details.

   Calling code should therefore deal with a nil return value."
  [{:keys [session] :as msg}]
  (when session
    (or (cider-spy-session session)
        (::session (swap! session assoc ::session (new-session msg))))))
