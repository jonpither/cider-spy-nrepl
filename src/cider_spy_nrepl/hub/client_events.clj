(ns cider-spy-nrepl.hub.client-events)

(defmulti process (fn [_ m] (-> m :op keyword)))

(defmethod process :default [s m]
  (println "Did not understand message from hub" m))

(defmethod process :registered [s {:keys [alias registered] :as msg}]
  (swap! s assoc-in [:registrations] registered)
  (println (format "Registered: %s" alias)))
