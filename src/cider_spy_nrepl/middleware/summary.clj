(ns cider-spy-nrepl.middleware.summary
  (:require [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [cider.nrepl.middleware.util.cljs :as cljs]
            [clojure.pprint]
            [cider-spy-nrepl.tracker]
            [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.summary-builder :as summary-builder])
  (:import [org.joda.time LocalDateTime Seconds]))


(defn summary-reply
  "Reply to request for summary information."
  [{:keys [transport hub-host hub-port hub-alias] :as msg}]
  (let [session (sessions/session! msg)]
    (hub-client/connect-to-hub! hub-host (Integer/parseInt hub-port) hub-alias session)
    (sessions/summary-msg! session msg)
    (cider/send-to-spy-buffer! session transport (summary-builder/summary @session))
    (transport/send transport (response-for msg :status :done))))

(defn- wrap-handler [handler {:keys [transport session] :as msg}]
  (let [result (handler msg)
        session (sessions/session! msg)]
    (when session
      (cider-spy-nrepl.tracker/track-msg! msg session)
      (let [{:keys [summary-msg]} @session]
        (when (Boolean/valueOf (:auto-refresh summary-msg))
          (cider/send-to-spy-buffer! session transport (summary-builder/summary @session)))))
    result))

(defn wrap-info
  "Middleware that looks up info for a symbol within the context of a particular namespace."
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "summary" op)
      (summary-reply msg)
      (wrap-handler handler msg))))

(set-descriptor!
 #'wrap-info
 (cljs/maybe-piggieback
  {:handles
   {"summary"
    {:doc "Return a summary of hacking information about the nrepl session."
     :returns {"status" "done"}}}}))
