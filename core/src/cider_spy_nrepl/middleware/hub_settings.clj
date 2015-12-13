(ns cider-spy-nrepl.middleware.hub-settings
  (:require [clojure.java.io :as io]))

(defn- read-env-file []
  (let [env-file (io/file ".cider-spy-hub.clj")]
    (when (.exists env-file)
      (read-string (slurp env-file)))))

(defn ^:dynamic hub-host-and-port []
  (let [{:keys [cider-spy-hub-host cider-spy-hub-port]}
        (read-env-file)]
    [cider-spy-hub-host cider-spy-hub-port]))
