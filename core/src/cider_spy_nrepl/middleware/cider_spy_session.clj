(ns cider-spy-nrepl.middleware.cider-spy-session
  (:require [cider-spy-nrepl.middleware.session-vars :refer [*cider-spy-transport* *session-started* *tracking* *summary-message-id*]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.session])
   (:import (org.joda.time LocalDateTime)))

(defn- update-session-for-cider-spy
  "We ensure the session has information cider-spy needs for asynchronous
   communication, such as a transport."
  [session {:keys [transport]}]
  (merge {#'*cider-spy-transport* transport
          #'*session-started* (LocalDateTime.)}
         session))

(defn wrap-cider-spy-session
  "Cider Spy Session Middleware."
  [handler]
  (fn [{:keys [op session] :as msg}]
    ;; The session can sometimes be nil
    (when session
      (swap! session update-session-for-cider-spy msg)
      (println "CSS" op (System/identityHashCode session) (@session #'*session-started*)))
    (handler msg)))

(set-descriptor!
 #'wrap-cider-spy-session
 {:requires #{#'clojure.tools.nrepl.middleware.session/session}
  :handles {"cider-spy-session" {:doc "See the cider-spy README"
                                 :returns {} :requires {}}}})
