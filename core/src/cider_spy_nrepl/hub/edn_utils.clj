(ns cider-spy-nrepl.hub.edn-utils
  (:require [clojure.edn :as edn])
  (:import (io.netty.handler.codec MessageToMessageDecoder)))

(defn make-decoder []
  (proxy [MessageToMessageDecoder] []
    (decode [ctx ^String msg ^java.util.List out]
      (.add out (edn/read-string msg)))))
