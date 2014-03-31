(ns cider-spy-nrepl.middleware.cider
  (:require [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.nrepl.misc :refer [response-for]]))

(defn send-to-spy-buffer!
  "Send this string back to the users CIDER SPY buffer."
  [session transport str]
  (transport/send transport (response-for (:summary-msg @session) :value str)))
