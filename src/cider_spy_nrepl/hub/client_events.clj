(ns cider-spy-nrepl.hub.client-events
  (:require [clojure.tools.logging :as log]))

(defmulti process (fn [_ m] (-> m :op keyword)))

(defmethod process :default [s m]
  (println "Did not understand message from hub" m))

(defmethod process :registered [s {:keys [alias registered] :as msg}]
  (swap! s assoc-in [:registrations] registered)
  (log/info (format "Registered: %s" alias)))

(defmethod process :unregistered [s {:keys [alias registered] :as msg}]
  (swap! s assoc-in [:registrations] registered)
  (log/info (format "Unregistered: %s" alias)))
