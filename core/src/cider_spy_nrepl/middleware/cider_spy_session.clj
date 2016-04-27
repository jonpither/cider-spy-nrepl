(ns cider-spy-nrepl.middleware.cider-spy-session
  (:require [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.session]
            [clojure.tools.nrepl.middleware.pr-values])
   (:import (org.joda.time LocalDateTime)))

(defn- update-session-for-cider-spy
  "We ensure the session has information cider-spy needs for asynchronous
   communication, such as a transport."
  [session {:keys [transport]}]
  (or (and (session #'*cider-spy-session*) session)
      (assoc session #'*cider-spy-session* (atom {#'*cider-spy-transport* transport
                                                  #'*session-started* (LocalDateTime.)}))))

(defn wrap-cider-spy-session
  "Cider Spy Session Middleware."
  [handler]
  (fn [{:keys [op session] :as msg}]
    ;; The session can sometimes be nil
    (when session
      (swap! session update-session-for-cider-spy msg))
    (handler msg)))

(set-descriptor!
 #'wrap-cider-spy-session
 {:requires #{"session";; #'clojure.tools.nrepl.middleware.session/session
              "pr-values" ;;#'clojure.tools.nrepl.middleware.pr-values/pr-values
              }
  :handles {"cider-spy-session" {:doc "See the cider-spy README"
                                 :returns {} :requires {}}}})
