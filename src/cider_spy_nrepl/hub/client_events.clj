(ns cider-spy-nrepl.hub.client-events
  (:require [cider-spy-nrepl.hub.register :as register]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.logging :as log]))

(defmulti process (fn [_ msg] (-> msg :op keyword)))

(defmethod process :default [_ msg]
  (println "Did not understand message from hub" msg))

(defmethod process :connected [session {:keys [alias]}]
  (log/debug (format "Connected on hub as: %s" alias))
  (cider/send-connected-on-hub-msg! session alias))

(defmethod process :registered [session {:keys [alias registered]}]
  (log/debug (format "Registered: %s" alias))
  (register/update! session assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :unregistered [session {:keys [alias registered]}]
  (log/debug (format "Unregistered: %s" alias))
  (register/update! session assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :location [session {:keys [alias registered]}]
  (log/debug (format "Location change: %s" alias))
  (register/update! session assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :message [s {:keys [message from recipient]}]
  (log/debug (format "Message received from %s to %s: %s" from recipient message))
  (cider/send-received-msg! s from recipient message))
