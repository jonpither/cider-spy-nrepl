(ns cider-spy-nrepl.hub.server-events
  (:require [cider-spy-nrepl.hub.register :as register]
            [clojure.tools.logging :as log]))

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

(defmethod process :register [_ session {:keys [session-id alias]}]
  (register/register! session session-id alias)
  (send-to-nrepl (:channel @session) {:op :connected
                                      :alias (:alias @session)})
  (broadcast-msg! :registered
                  :alias alias
                  :registered (register/users)))

(defmethod process :unregister [_ session _]
  (register/unregister! session)
  (broadcast-msg! :unregistered
                  :alias (:alias @session)
                  :registered (register/users)))

(defmethod process :location [_ session {:keys [ns dt]}]
  (register/update! session
                    update-in [:tracking :ns-trail]
                    conj {:dt dt :ns ns})
  (broadcast-msg! :location
                  :alias (:alias @session)
                  :registered (register/users)))

(defmethod process :message [_ _ {:keys [message from recipient]}]
  (if-let [recipient-session (register/session-from-alias recipient)]
    (do
      (log/info "Delivering message from" from "to" (:alias @recipient-session))
      (send-to-nrepl (:channel @recipient-session)
                     {:op :message
                      :message message
                      :from from
                      :recipient recipient}))
    (log/warn "Message from" from "to unregistered user" recipient)))

(defmethod process :watch-repl [_ session {:keys [target]}]
  (if-let [target-session (register/session-from-alias target)]
    (do
      (log/info "Sending REPL watch request to" (:alias @target-session))
      (register/update! target-session update :watching-sessions #(set (cons (:id @session) %)))
      (send-to-nrepl (:channel @target-session) {:op :watch-repl}))
    (log/warn "Attempt to watch unregistered user" target)))

(defn unregister! [session]
  (process nil session {:op :unregister}))