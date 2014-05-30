(ns cider-spy-nrepl.hub.client-events
  (:require [clojure.tools.logging :as log]
            [cider-spy-nrepl.hub.register :as register]
            [cider-spy-nrepl.middleware.cider :as cider]))

(defmulti process (fn [session msg] (-> msg :op keyword)))

(defmethod process :default [session msg]
  (println "Did not understand message from hub" msg))

(defmethod process :registered [session {:keys [alias registered] :as msg}]
  (log/debug (format "Registered: %s" alias))
  (register/update! session assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :unregistered [session {:keys [alias registered] :as msg}]
  (log/debug (format "Unregistered: %s" alias))
  (register/update! session assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :location [session {:keys [alias registered] :as msg}]
  (log/debug (format "Location change: %s" alias))
  (register/update! session assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :message [s {:keys [message] :as msg}]
  (log/debug (format "Message received: %s" message))
  (cider/send-connected-msg! s (format "Message Received: \n\n%s\n\n" message)))
