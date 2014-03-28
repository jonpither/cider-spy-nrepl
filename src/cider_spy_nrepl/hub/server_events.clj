(ns cider-spy-nrepl.hub.server-events
  (:require [clojure.tools.logging :as log]
            [cider-spy-nrepl.hub.register :as register])
  (:import [java.util UUID]))

(defn- send-response! [ctx op & {:as msg}]
  (let [msg (assoc msg :op op)]
    (log/info "Sending response:" msg)
    (.write ctx (prn-str msg))
    (.flush ctx)))

(defmulti process (fn [_ _ request] (-> request :op keyword )))

(defmethod process :default [_ _ m]
  (log/warn "Did not understand message" m))

(defmethod process :register [ctx session {:keys [session-id alias]}]
  (register/register! session session-id alias)
  (send-response! ctx :registered :alias alias :registered (set (register/aliases))))

(defn unregister! [session]
  (register/unregister! session))
