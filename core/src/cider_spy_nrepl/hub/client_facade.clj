(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.middleware.session-vars :refer [*hub-client*]]))

(defn- send!
  [bootstrap op msg]
  (when bootstrap
    (future
      (hubc/send! bootstrap (assoc msg :op op)))))

(defn register [session alias]
  (let [{:keys [id]} (meta session)]
    (send! (@session #'*hub-client*) :register {:alias alias :session-id id})))

(defn connect
  "Connect to the hub.
   If a connection cannot be returned, then nil will be passed through to the callback."
  [session host port]
  (try
    (hubc/connect host port session)
    (catch java.net.SocketException e
      nil)))

(defn update-location
  "Update the location of where this developer is on the hub.
   A location is a namespace and a timestamp."
  [session ns ts]
  (send! (@session #'*hub-client*) :location {:ns ns :dt (.toDate ts)}))

(defn send-msg [session recipient message]
  (send! (@session #'*hub-client*) :message {:message message
                                             :recipient recipient}))

(defn watch-repl [session target]
  (send! (@session #'*hub-client*) :watch-repl {:target target}))

(defn multi-repl-eval [session target msg]
  (send! (@session #'*hub-client*) :multi-repl-eval {:target target :msg msg}))

(defn multi-repl-eval-output [session originator msg]
  (send! (@session #'*hub-client*) :multi-repl-eval-out {:originator originator :msg msg}))

(defn forward-repl-output [session msg]
  (send! (@session #'*hub-client*) :repl-out {:msg msg}))

(defn forward-repl-eval [session code]
  (send! (@session #'*hub-client*) :repl-eval {:code code}))
