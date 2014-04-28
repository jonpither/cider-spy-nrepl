(ns cider-spy-nrepl.hub.server-events
  (:require [clojure.tools.logging :as log]
            [cider-spy-nrepl.hub.register :as register])
  (:import [java.util UUID]))

(defn- send-response! [ctx op & {:as msg}]
  (let [msg (assoc msg :op op)]
    (log/info "Sending response:" msg)
    (.write ctx (prn-str msg))
    (.flush ctx)))

(defn ^:dynamic broadcast-msg! [op & {:as msg}]
  (let [msg (assoc msg :op op)]
    (doseq [c (map (comp :channel deref) (vals @register/sessions)) :when c]
      (.writeAndFlush c (prn-str msg)))))

(defmulti process (fn [_ _ request] (-> request :op keyword)))

(defmethod process :default [_ _ m]
  (log/warn "Did not understand message" m))

(defmethod process :register [_ session {:keys [session-id alias]}]
  (register/register! session session-id alias)
  (broadcast-msg! :registered :alias alias :registered (register/users)))

(defmethod process :unregister [_ session _]
  (register/unregister! session)
  (broadcast-msg! :unregistered :alias (:alias @session) :registered (register/users)))

(defmethod process :location [_ session {:keys [ns dt]}]
  (register/update-location! session ns dt)
  (broadcast-msg! :location :alias (:alias @session) :registered (register/users)))

(defn unregister! [session]
  (process nil session {:op :unregister}))
