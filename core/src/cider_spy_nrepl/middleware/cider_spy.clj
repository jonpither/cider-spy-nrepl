(ns cider-spy-nrepl.middleware.cider-spy
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.tracker :as tracker]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [clojure.tools.nrepl.middleware.load-file]
            [clojure.tools.nrepl.middleware.pr-values]
            [clojure.tools.nrepl.middleware.session]
            [cider-spy-nrepl.middleware.session-vars :refer [*cider-spy-transport* *session-started* *tracking* *summary-message-id*]]
            [cider-spy-nrepl.middleware.cider-spy-session])
  (:import (org.joda.time LocalDateTime)))

(defn- handle-summary
  "Handle the CIDER-SPY request for summary information."
  [{:keys [id session] :as msg}]
  (try
    (swap! session assoc #'*summary-message-id* id)
    (cider/update-spy-buffer-summary! session)
    (catch Throwable t
      (println "Error occured servicing cider-spy-summary request")
      (.printStackTrace t))))

(defn- handle-reset
  "Reset CIDER-SPY tracking."
  [{:keys [session]}]
  (swap! session dissoc #'*tracking*)
  (cider/update-spy-buffer-summary! session))

(defn- wrap-tracking
  "Wrap the handler to apply tracking and to update the CIDER SPY summary buffer."
  [{:keys [session] :as msg} handler]
  (let [result (handler msg)]
    (try
      (tracker/track-msg! msg session)
      (cider/update-spy-buffer-summary! session)
      (catch Throwable t
        (println "Error occured, oops")
        (.printStackTrace t)))
    result))

(def cider-spy--nrepl-ops {"cider-spy-summary" #'handle-summary
                           "cider-spy-reset" #'handle-reset})

(def ops-that-can-eval
  "Set of nREPL ops that can lead code being evaluated."
  #{"eval" "load-file" "refresh" "refresh-all" "refresh-clear" "undef"})

(defn wrap-cider-spy
  "Cider Spy Middleware."
  [handler]
  (fn [{:keys [op session] :as msg}]
    ;; The session can sometimes be nil
    (if session
      (if-let [cider-spy-handler (get cider-spy--nrepl-ops op)]
        (cider-spy-handler msg)
        (if (ops-that-can-eval op)
          (wrap-tracking msg handler)
          (handler msg)))
      (handler msg))))

(set-descriptor!
 #'wrap-cider-spy
 {:requires #{#'cider-spy-nrepl.middleware.cider-spy-session/wrap-cider-spy-session
              #'clojure.tools.nrepl.middleware.pr-values/pr-values}
  :expects  #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval
              #'clojure.tools.nrepl.middleware.load-file/wrap-load-file}
  :handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})

;; before go live:
;; Change to be a plugin, with all the middleware (the hub stuff doesn't run with out a URL specific, in effect turned off)
;; Remove interactions test and connections test
;; Go ahead with planned session changes
;; clj-refactor optimise requires throughout
