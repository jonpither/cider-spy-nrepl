(ns cider-spy-nrepl.hub.server-events
  (:require [clojure.tools.logging :as log]
            [cider-spy-nrepl.hub.register :as register])
  (:import [java.util UUID]
           [org.joda.time LocalDateTime]))

(defn- send-response! [ctx op & {:as msg}]
  (let [msg (assoc msg :op op)]
    (log/info "Sending response:" msg)
    (.write ctx (prn-str msg))
    (.flush ctx)))

(defn ^:dynamic broadcast-msg! [op & {:as msg}]
  (let [msg (assoc msg :op op)]
    (doseq [c (register/channels) :when c]
      (.writeAndFlush c (prn-str msg)))))

(defmulti process (fn [_ _ request] (-> request :op keyword)))

(defmethod process :default [_ _ request]
  (log/warn "Did not understand message" request))

(defmethod process :register [_ session {:keys [session-id alias] :as request}]
  (register/register! session session-id alias)
  (broadcast-msg! :registered :alias alias :registered (register/users)))

(defmethod process :unregister [_ session request]
  (register/unregister! session)
  (broadcast-msg! :unregistered :alias (:alias @session) :registered (register/users)))

(defmethod process :location [_ session {:keys [ns dt] :as request}]
  (register/update! session update-in [:tracking :ns-trail] conj {:dt dt :ns ns})
  (broadcast-msg! :location :alias (:alias @session) :registered (register/users)))

(defn unregister! [session]
  (process nil session {:op :unregister}))
