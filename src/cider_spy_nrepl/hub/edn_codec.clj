(ns cider-spy-nrepl.hub.edn-codec
  (:require [clojure.edn :as edn])
  (:import (io.netty.handler.codec MessageToMessageDecoder)))

(defn make-decoder []
  (proxy [MessageToMessageDecoder] []
    (decode [ctx ^String msg ^java.util.List out]
      (.add out (edn/read-string msg)))))
