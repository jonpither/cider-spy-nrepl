(ns cider-spy-nrepl.hub.edn-utils
  (:require [clojure.edn :as edn]
            [cognitect.transit :as transit])
  (:import (io.netty.handler.codec MessageToMessageDecoder MessageToMessageEncoder)
           (io.netty.buffer ByteBuf)
           (io.netty.buffer ByteBufInputStream)
           (java.io ByteArrayOutputStream)))

(defn transit-decoder []
  (proxy [MessageToMessageDecoder] []
    (decode [ctx ^ByteBuf msg ^java.util.List out]
      (let [reader (transit/reader (ByteBufInputStream. msg) :json)]
        (.add out (transit/read reader))))))

(defn- msg->transit-str [msg]
  (let [out (ByteArrayOutputStream. 4096)
            writer (transit/writer out :json)]
    (transit/write writer msg)
    (.toString out)))

(defn transit-encoder []
  (proxy [MessageToMessageEncoder] []
    (encode [ctx msg ^java.util.List out]
      (let [msg (msg->transit-str msg)]
        (.add out (str msg "\n"))))))
