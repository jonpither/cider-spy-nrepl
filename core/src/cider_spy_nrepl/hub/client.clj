(ns cider-spy-nrepl.hub.client
  (:require [cider-spy-nrepl.hub.edn-utils :as edn-utils]
            [cider-spy-nrepl.middleware.session-vars :refer [*hub-client*]]
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
  "Wraps handle messages coming back from the CIDER-SPY hub."
  [handler]
  (proxy [SimpleChannelInboundHandler] []
    (messageReceived [ctx request]
      (try
        (handler request)
        (catch Throwable t
          (println "Error occuring processing CIDER-SPY-HUB message. Check for compatibility.")
          (log/error t)))
      (.flush ctx))))

(defn- client-bootstrap
  "Create a NETTY client bootstrap."
  [handler]
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
                (.addLast "main handler" (simple-handler handler))))))))
     group]))

;; TODO, some (.shutdownGracefully group) action
(defn ^:dynamic connect
  "Connect to CIDER-SPY-HUB.
   Returns a vector containing a client bootstrap, a group and a channel."
  [host port handler]
  (let [[b group] (client-bootstrap handler)]
    [b group (-> b
                 (.connect (InetSocketAddress. host port))
                 .sync
                 .channel)]))

(defn safe-connect
  "Connect to the hub.
   If a connection cannot be returned, then nil will return"
  [host port handler]
  (try
    (connect host port handler)
    (catch java.net.SocketException e
      nil)))

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

(defn send-async!
  [session msg]
  (assert (:op msg))
  (when-let [bootstrap (@session #'*hub-client*)]
    (future
      (send! bootstrap msg))))
