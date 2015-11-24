(ns cider-spy-nrepl.middleware.cider-spy
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.tracker :as tracker]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]))

(defn- handle-summary
  "Handle the CIDER-SPY request for summary information."
  [msg]
  (try
    (when-let [session (sessions/session! msg)]
      (println session)
      (cider/update-session-for-summary-msg! session msg)
      (cider/update-spy-buffer-summary! session))
    (catch Throwable t
      (println "Error occured servicing cider-spy-summary request")
      (.printStackTrace t))))

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

(def cider-spy--nrepl-ops {"cider-spy-summary" #'handle-summary
                           "cider-spy-reset" #'handle-reset})

(defn wrap-cider-spy
  "Cider Spy Middleware."
  [handler]
  (fn [{:keys [op] :as msg}]
    (println "Servicing" op "with" (get cider-spy--nrepl-ops op))
    (if-let [cider-spy-handler (get cider-spy--nrepl-ops op)]
      (cider-spy-handler msg)
      (wrap-tracking handler msg))))

(set-descriptor!
 #'wrap-cider-spy
 {:handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})
