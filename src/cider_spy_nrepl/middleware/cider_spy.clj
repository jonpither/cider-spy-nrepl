(ns cider-spy-nrepl.middleware.cider-spy
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.tracker :as tracker]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]))

(defn- handle-summary
  "Handle the CIDER-SPY request for summary information."
  [msg]
  (when-let [session (sessions/session! msg)]
    (cider/update-session-for-summary-msg! session msg)
    (cider/update-spy-buffer-summary! session)))

(defn- handle-reset
  "Reset CIDER-SPY tracking."
  [msg]
  (when-let [session (sessions/session! msg)]
    (sessions/update! session dissoc :tracking)
    (cider/update-spy-buffer-summary! session)))

(defn- wrap-tracking
  "Wrap the handler to apply tracking and to update the CIDER SPY summary buffer."
  [handler msg]
  (let [result (handler msg)]
    (when-let [session (sessions/session! msg)]
      (tracker/track-msg! msg session)
      (cider/update-spy-buffer-summary! session))
    result))

(defn wrap-cider-spy
  "Cider Spy Middleware."
  [handler]
  (fn [{:keys [op] :as msg}]
    (case op
      "cider-spy-summary" (handle-summary msg)
      "cider-spy-reset" (handle-reset msg)
      (wrap-tracking handler msg))))

;; TODO figure out what this actually done.
(set-descriptor!
 #'wrap-cider-spy
 {:handles
  {"cider-spy-summary"
   {:doc "Return a summary of hacking information about the nrepl session."}
   "cider-spy-reset"
   {:doc "Reset all tracking information."}}})
