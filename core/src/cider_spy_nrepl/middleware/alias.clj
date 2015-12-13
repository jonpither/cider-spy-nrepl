(ns cider-spy-nrepl.middleware.alias
  (:require [clojure.string :as s]))

(defn ^:dynamic alias-from-env []
  (let [user (System/getenv "USER")
        host (try
               (.getHostName (java.net.InetAddress/getLocalHost))
               (catch Exception e nil))]
    (or (not-empty (s/join "@" (remove nil? [user host])))
        "anonymous")))
