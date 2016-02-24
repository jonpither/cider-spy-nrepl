(ns cider-spy-nrepl.middleware.cider-spy
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.tracker :as tracker]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [clojure.tools.nrepl.middleware.session]
            [cider-spy-nrepl.middleware.session-vars :refer [*cider-spy-transport* *session-started* *tracking*]])
  (:import (org.joda.time LocalDateTime)))

(defn- handle-summary
  "Handle the CIDER-SPY request for summary information."
  [{:keys [session] :as msg}]
  (try
    (cider/update-session-for-summary-msg! session msg)
    (cider/update-spy-buffer-summary! session)
    {:status :done}
    (catch Throwable t
      (println "Error occured servicing cider-spy-summary request")
      (.printStackTrace t)
      {:status :error})))

(defn- handle-reset
  "Reset CIDER-SPY tracking."
  [{:keys [session]}]
  (swap! session dissoc #'*tracking*)
  (cider/update-spy-buffer-summary! session)
  {:status :done})

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

(defn- update-session-for-cider-spy
  "We ensure the session has information cider-spy needs for asynchronous
   communication, such as a transport."
  [session {:keys [transport]}]
  (merge {#'*cider-spy-transport* transport
          #'*session-started* (LocalDateTime.)}
         session))

(def cider-spy--nrepl-ops {"cider-spy-summary" #'handle-summary
                           "cider-spy-reset" #'handle-reset})

(defn wrap-cider-spy
  "Cider Spy Middleware."
  [handler]
  (fn [{:keys [op session] :as msg}]
    ;; The session can sometimes be nil
    (if session
      (do
        (swap! session update-session-for-cider-spy msg)
        (if-let [cider-spy-handler (get cider-spy--nrepl-ops op)]
          (cider-spy-handler msg)
          (wrap-tracking msg handler)))
      (handler msg))))

(set-descriptor!
 #'wrap-cider-spy
 {:requires #{#'clojure.tools.nrepl.middleware.session/session}
  :expects  #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval}
  :handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})
