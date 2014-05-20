(ns cider-spy-nrepl.hub.client-events
  (:require [clojure.tools.logging :as log]
            [cider-spy-nrepl.middleware.cider :as cider]))

(defmulti process (fn [_ m] (-> m :op keyword)))

(defmethod process :default [s m]
  (println "Did not understand message from hub" m))

(defmethod process :registered [s {:keys [alias registered] :as msg}]
  (log/debug (format "Registered: %s" alias))
  (swap! s assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! s))

(defmethod process :unregistered [s {:keys [alias registered] :as msg}]
  (log/debug (format "Unregistered: %s" alias))
  (swap! s assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! s))

(defmethod process :location [s {:keys [alias registered] :as msg}]
  (log/debug (format "Location change: %s" alias))
  (swap! s assoc-in [:registrations] registered)
  (cider/update-spy-buffer-summary! s))
