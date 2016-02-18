(ns cider-spy-nrepl.hub.client
  (:require [cider-spy-nrepl.hub.edn-utils :as edn-utils]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [clojure.tools.logging :as log])
  (:import (io.netty.bootstrap Bootstrap)
           (io.netty.channel ChannelInitializer
                             SimpleChannelInboundHandler)
           (io.netty.channel.nio NioEventLoopGroup)
           (io.netty.channel.socket.nio NioSocketChannel)
           (io.netty.handler.codec DelimiterBasedFrameDecoder
                                   Delimiters)
           (io.netty.handler.codec.string StringDecoder StringEncoder)
           (java.net InetSocketAddress)))

(defn simple-handler
  "Handle messages coming back from the CIDER-SPY hub."
  [session]
  (proxy [SimpleChannelInboundHandler] []
    (messageReceived [ctx request]
      (try
        (client-events/process session request)
        (catch Throwable t
          (println "Error occuring processing CIDER-SPY-HUB message. Check for compatibility.")
          (log/error t)))
      (.flush ctx))))

(defn- client-bootstrap
  "Create a NETTY client bootstrap."
  [session]
  (let [group (NioEventLoopGroup.)]
    [(doto (Bootstrap.)
       (.group group)
       (.channel NioSocketChannel)
       (.handler
        (proxy [ChannelInitializer] []
          (initChannel [ch]
            (let [pipeline (.pipeline ch)]
              (doto pipeline
                (.addLast "framer" (DelimiterBasedFrameDecoder. 8192 (Delimiters/lineDelimiter)))
                (.addLast "string-decoder" (StringDecoder.))
                (.addLast "end" (edn-utils/make-decoder))
                (.addLast "string-encoder" (StringEncoder.))
                (.addLast "main handler" (simple-handler session))))))))
     group]))

;; TODO, some (.shutdownGracefully group) action
(defn ^:dynamic connect
  "Connect to CIDER-SPY-HUB.
   Returns a vector containing a client bootstrap, a group and a channel."
  [host port session]
  (let [[b group] (client-bootstrap session)]
    [b group (-> b
                 (.connect (InetSocketAddress. host port))
                 .sync
                 .channel)]))

(defn shutdown!
  "Shut down the netty Client Bootstrap
   Expects a vector containing a client bootstrap, group and channel.
   This operation can be run safely against a Client Bootstrap that is already shutdown."
  [[_ g c]]
  (when (.isOpen c)
    (-> c .close .sync))
  (when-not (.isShutdown g)
    (-> g .shutdownGracefully .sync)))

(defn send! [[_ _ c] msg]
  (-> c
      (.writeAndFlush (prn-str msg))
      .sync))
