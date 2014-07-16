(ns cider-spy-nrepl.hub.client
  (:require [cider-spy-nrepl.hub.edn-codec :as edn-codec]
            [cider-spy-nrepl.hub.client-events :as client-events])
  (:import [io.netty.channel ChannelHandlerAdapter SimpleChannelInboundHandler ChannelInitializer]
           [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.channel.socket.nio NioSocketChannel]
           [io.netty.bootstrap Bootstrap]
           [java.net InetSocketAddress ConnectException]
           [io.netty.handler.codec.string StringDecoder]
           [io.netty.handler.codec.string StringEncoder]
           [io.netty.handler.codec DelimiterBasedFrameDecoder Delimiters]))

(defn simple-handler
  "Handle messages coming back from the CIDER-SPY hub."
  [session]
  (proxy [SimpleChannelInboundHandler] []
    (messageReceived [ctx request]
      (client-events/process session request)
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
                (.addLast "end" (edn-codec/make-decoder))
                (.addLast "string-encoder" (StringEncoder.))
                (.addLast "main handler" (simple-handler session))))))))
     group]))

;; TODO, some (.shutdownGracefully group) action
(defn ^:dynamic connect
  "Connect to CIDER-SPY-HUB.
   Returns a vector containing a client bootstrap, a group and a channel."
  [host port session]
  (let [[b group] (client-bootstrap session)]
    [b group (.channel (.sync (.connect b (InetSocketAddress. host port))))]))

(defn shutdown!
  "Shut down the netty Client Bootstrap
   Expects a vector containing a client bootstrap, group and channel.
   This operation can be run safely against a Client Bootstrap that is already shutdown."
  [[b g c]]
  (when [(.isOpen c)]
    (-> c (.close) (.sync)))
  (when-not [(.isShutdown g)]
    (.sync (.shutdownGracefully g))))

(defn send! [[_ _ c] msg]
  (.sync (.writeAndFlush c (prn-str msg))))
