(ns cider-spy-nrepl.nrepl-test-utils
  (:require [clojure.tools.nrepl.transport :as transport]
            [clojure.core.async :refer [alts!! chan close! go timeout >! buffer go-loop]]))

(defn messages-chan! [transport]
  (let [c (chan (buffer 100))]
    (go-loop []
      (when-let [v (transport/recv transport Long/MAX_VALUE)]
        (>! c v)
        (recur)))
    c))

(defn take-from-chan! [n seconds c]
  (let [s (atom '[])]
    (loop [n n]
      (when-let [v (and (pos? n) (first (alts!! [c (timeout seconds)])))]
        (swap! s conj v)
        (recur (dec n))))
    @s))
