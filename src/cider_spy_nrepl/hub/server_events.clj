(ns cider-spy-nrepl.hub.server-events
  (:require [clojure.tools.logging :as log]
            [cider-spy-nrepl.hub.register :as register])
  (:import [java.util UUID]
           [org.joda.time LocalDateTime]))

(defn- send-to-nrepl [c msg]
  (when c
    (.writeAndFlush c (prn-str msg))))

(defn- broadcast-msg! [op & {:as msg}]
  (let [msg (assoc msg :op op)]
    (doseq [c (register/channels) :when c]
      (send-to-nrepl c msg))))

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

(defmethod process :message [_ session {:keys [message recipient]}]
  (if (@register/sessions recipient)
    (do
      (log/info "Message from" (:alias @session) "to" (:alias @(@register/sessions recipient)))
      (send-to-nrepl (:channel @(@register/sessions recipient))
                     {:op :message :message message :from (:alias @session)}))
    (log/warn "Message from" (:alias @session) (:id @session) "to unregistered user" recipient)))

(defn unregister! [session]
  (process nil session {:op :unregister}))
