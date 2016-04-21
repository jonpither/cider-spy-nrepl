(ns cider-spy-nrepl.middleware.cider-spy-hub-close
  (:require [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            clojure.tools.nrepl.middleware.session))

(defn- look-up-client-in-session [id]
  (when-let [session (@(var-get #'clojure.tools.nrepl.middleware.session/sessions) id)]
    (@session #'*hub-client*)))

(defn wrap-close
  [handler]
  (fn [{:keys [op session] :as msg}]
    (when-let [hub-client (and session (= op "close") (look-up-client-in-session session))]
      (-> hub-client last .close))
    (handler msg)))

(set-descriptor!
 #'wrap-close
 {:expects #{"close"}
  :handles {"cider-spy-hub-close" {:doc "See the cider-spy-hub README"
                                   :returns {} :requires {}}}})
