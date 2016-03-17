(ns cider-spy-nrepl.interactions-test
  (:require [cider-spy-nrepl.middleware.cider-spy :as spy-middleware]
            [cider-spy-nrepl.middleware.cider-spy-hub :as hub-middleware]
            [cider-spy-nrepl.test-utils :as test-utils]
            [cider-spy-nrepl.middleware.session-vars :refer [*desired-alias*]]
            [clojure.test :refer :all]))

(deftest dev-locations
  (test-utils/spy-harness
   (let [nrepl-session (atom {#'*desired-alias* "Jon"} :meta {:id 1})
         cider-chan (test-utils/foo nrepl-session)]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (test-utils/cider-msg cider-chan))))

     ((spy-middleware/wrap-cider-spy (constantly nil))
      {:op "load-file"
       :file "(ns foo.bar) (println \"hi\")"
       :session nrepl-session})

     (is (= {:1 {:alias "Jon" :nses []}}
            (:devs (test-utils/cider-msg cider-chan))))
     (is (= {:1 {:alias "Jon" :nses ["foo.bar"]}}
            (:devs (test-utils/cider-msg cider-chan)))))))
