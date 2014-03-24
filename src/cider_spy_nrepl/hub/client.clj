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
  []
  (proxy [SimpleChannelInboundHandler] []
    (messageReceived [ctx request]
      (client-events/process request)
      (.flush ctx))))

(defn- client-bootstrap
  "Create a NETTY client bootstrap."
  []
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
                (.addLast "main handler" (simple-handler))))))))
     group]))

;; TODO, some (.shutdownGracefully group) action
(defn connect
  "Connect to CIDER-SPY-HUB.
   Returns a vector containing a client bootstrap, a group and a channel."
  [host port]
  (try
    (let [[b group] (client-bootstrap)]
      [b group (.channel (.sync (.connect b (InetSocketAddress. host port))))])
    (catch ConnectException e (println (format "Could not connect to %s:%s, sorry." host port)))))

(defn shutdown!
  "Shut down the netty Server Bootstrap
   Expects a vector containing a server bootstrap, boss group and worker group."
  [[b g c]]
  (-> c (.close) (.sync))
  (.sync (.shutdownGracefully g)))

(defn send! [[_ _ c] msg]
  (.sync (.writeAndFlush c (prn-str msg))))
