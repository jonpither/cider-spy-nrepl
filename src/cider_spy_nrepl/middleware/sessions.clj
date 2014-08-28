(ns cider-spy-nrepl.middleware.sessions
  (:require [cider-spy-nrepl.middleware.alias :as alias]
            [cider-spy-nrepl.middleware.tooling-session :as tooling])
  (:import [org.joda.time LocalDateTime]))

(def sessions (atom {}))

(defn- new-session [{:keys [session transport]}]
  (atom {:id session
         :transport transport
         :session-started (LocalDateTime.)
         :hub-alias (alias/alias-from-env)}))

(defn- register-new-session [{:keys [session] :as msg}]
  (locking sessions
    (or (get @sessions session)
        (do
;;          (println "New session" msg)
          (get (swap! sessions assoc session (new-session msg)) session)))))

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
  (when (string? session)
    (or (get @sessions session)
        (and (not (tooling/tooling-session? msg))
             (register-new-session msg)))))

(defn update!
  "Updates the session with the given function."
  [session f & args]
  (swap! session #(apply f % args)))
