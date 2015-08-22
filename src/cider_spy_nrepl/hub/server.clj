(ns cider-spy-nrepl.hub.server
  (:require [cider-spy-nrepl.hub.edn-codec :as edn-codec]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [clojure.tools.logging :as log])
  (:import (io.netty.bootstrap ServerBootstrap)
           (io.netty.channel ChannelHandlerContext ChannelInitializer
                             ChannelOption SimpleChannelInboundHandler)
           (io.netty.channel.nio NioEventLoopGroup)
           (io.netty.channel.socket.nio NioServerSocketChannel)
           (io.netty.handler.codec DelimiterBasedFrameDecoder
                                   Delimiters)
           (io.netty.handler.codec.string StringDecoder StringEncoder)))

(defn- server-request [ctx session request]
  (log/info "Server got request" (prn-str request))
  (server-events/process ctx session request))

(defn- server-close-session [ctx session]
  (log/info "Client Disconnected")
  (server-events/unregister! session))

(defn- simple-handler [ch]
  (let [session (atom {:channel ch})]
    (proxy [SimpleChannelInboundHandler] []
      (messageReceived [^ChannelHandlerContext ctx request]
        (server-request ctx session request))
      (channelInactive [ctx]
        (server-close-session ctx session)))))

(defn start-netty-server
  "Returns a vector consisting of a channel, boss group and worker group"
  [& {:keys [port]}]
  (let [boss-group (NioEventLoopGroup.)
        worker-group (NioEventLoopGroup.)
        b (ServerBootstrap.)]
    [(-> b
         (.group boss-group worker-group)
         (.channel NioServerSocketChannel)
         (.childHandler
          (proxy [ChannelInitializer] []
            (initChannel [ch]
              (log/info "Initializing Netty Channel.")
              (let [pipeline (.pipeline ch)]
                (doto pipeline
                  (.addLast "framer" (DelimiterBasedFrameDecoder. 8192 (Delimiters/lineDelimiter)))
                  (.addLast "string-decoder" (StringDecoder.))
                  (.addLast "end" (edn-codec/make-decoder))
                  (.addLast "string-encoder" (StringEncoder.))
                  (.addLast "handler" (simple-handler ch))
                  )))))
         (.option ChannelOption/SO_BACKLOG (int 128))
         (.childOption ChannelOption/SO_KEEPALIVE true)
         (.bind port))
     boss-group worker-group]))

(defn shutdown
  "Shut down the netty Server Bootstrap
   Expects a vector containing a server bootstrap, boss group and worker group."
  [[b bg wg]]
  (.sync (.awaitUninterruptibly b))
  (-> b (.channel) (.close) (.sync))
  (.sync (.shutdownGracefully wg))
  (.sync (.shutdownGracefully bg)))

(defn -main [& args]
  (let [port (or (first args) "7771")]
    (println (format "Starting CIDER-SPY HUB Server on %s." port))
    (let [[b] (start-netty-server :port (Integer/parseInt port))]
      (.sync b))))
