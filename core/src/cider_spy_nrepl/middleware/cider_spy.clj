(ns cider-spy-nrepl.middleware.cider-spy
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.tracker :as tracker]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [clojure.tools.nrepl.middleware.session]))

(defn- handle-summary
  "Handle the CIDER-SPY request for summary information."
  [msg]
  (try
    (when-let [session (sessions/session! msg)]
      (cider/update-session-for-summary-msg! session msg)
      (cider/update-spy-buffer-summary! session)
      {:status :done})
    (catch Throwable t
      (println "Error occured servicing cider-spy-summary request")
      (.printStackTrace t)
      {:status :error})))

(defn- handle-reset
  "Reset CIDER-SPY tracking."
  [msg]
  (when-let [session (sessions/session! msg)]
    (swap! session dissoc :tracking)
    (cider/update-spy-buffer-summary! session)
    {:status :done}))

(defn- wrap-tracking
  "Wrap the handler to apply tracking and to update the CIDER SPY summary buffer."
  [msg handler]
  (let [result (handler msg)]
    (try
      (when-let [session (sessions/session! msg)]
        (tracker/track-msg! msg session)
        (cider/update-spy-buffer-summary! session))
      (catch Throwable t
        (println "Error occured, oops")
        (.printStackTrace t)))
    result))

(def cider-spy--nrepl-ops {"cider-spy-summary" #'handle-summary
                           "cider-spy-reset" #'handle-reset})

(defn wrap-cider-spy
  "Cider Spy Middleware."
  [handler]
  (fn [{:keys [op] :as msg}]
    (if-let [cider-spy-handler (get cider-spy--nrepl-ops op)]
      (cider-spy-handler msg)
      (wrap-tracking msg handler))))

(set-descriptor!
 #'wrap-cider-spy
 {:requires #{#'clojure.tools.nrepl.middleware.session/session}
  :expects  #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval}
  :handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})
