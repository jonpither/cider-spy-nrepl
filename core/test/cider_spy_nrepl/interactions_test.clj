(ns cider-spy-nrepl.interactions-test
  (:require [cider-spy-nrepl.middleware.cider-spy :as spy-middleware]
            [cider-spy-nrepl.middleware.cider-spy-hub :as hub-middleware]
            [cider-spy-nrepl.test-utils :as test-utils]
            [cider-spy-nrepl.middleware.session-vars :refer [*desired-alias*]]
            [clojure.test :refer :all]))

(deftest alias-should-bubble-to-cider
  (test-utils/spy-harness
   (let [nrepl-session (atom {#'*desired-alias* "Jon"} :meta {:id 1})
         cider-chan (test-utils/foo nrepl-session)]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (test-utils/cider-msg cider-chan))))

     ((hub-middleware/wrap-cider-spy-hub nil)
      {:op "cider-spy-hub-alias"
       :alias "Jon2"
       :session nrepl-session})

     (is (= {:1 {:alias "Jon2" :nses []}} (:devs (test-utils/cider-msg cider-chan)))))))

(deftest user-registrations
  (test-utils/spy-harness
   (let [nrepl-session (atom {#'*desired-alias* "Jon"} :meta {:id 1})
         nrepl-session-2 (atom {#'*desired-alias* "Dave"} :meta {:id 2})
         cider-chan1 (test-utils/foo nrepl-session)]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (test-utils/cider-msg cider-chan1))))
     (let [cider-chan2 (test-utils/foo nrepl-session-2)]
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (test-utils/cider-msg cider-chan1))))
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (test-utils/cider-msg cider-chan2)))))

     ((hub-middleware/wrap-cider-spy-hub nil)
      {:op "cider-spy-hub-disconnect"
       :session nrepl-session-2})

     (is (= {:1 {:alias "Jon" :nses []}} (:devs (test-utils/cider-msg cider-chan1)))))))

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

(deftest send-messages
  (test-utils/spy-harness
   (let [nrepl-session (atom {#'*desired-alias* "Jon"} :meta {:id 1})
         nrepl-session-2 (atom {#'*desired-alias* "Dave"} :meta {:id 2})
         cider-chan1 (test-utils/foo nrepl-session)]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (test-utils/cider-msg cider-chan1))))
     (let [cider-chan2 (test-utils/foo nrepl-session-2)]
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (test-utils/cider-msg cider-chan1))))
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (test-utils/cider-msg cider-chan2))))

       ((hub-middleware/wrap-cider-spy-hub nil)
        {:op "cider-spy-hub-send-msg"
         :recipient "Dave"
         :from "Jon"
         :message "Hows it going?"
         :session nrepl-session})

       (is (= {:msg "Hows it going?" :from "Jon"}
              (select-keys (test-utils/raw-cider-msg cider-chan2) [:msg :from])))

       ((hub-middleware/wrap-cider-spy-hub nil)
        {:op "cider-spy-hub-send-msg"
         :recipient "Jon"
         :from "Dave"
         :message "Not bad dude."
         :session nrepl-session-2})

       (is (= {:msg "Not bad dude." :from "Dave"}
              (select-keys (test-utils/raw-cider-msg cider-chan1) [:msg :from])))))))

(deftest test-single-alias
  (test-utils/spy-harness
   (let [nrepl-session (atom {#'*desired-alias* "Jon"} :meta {:id 1})
         nrepl-session-2 (atom {#'*desired-alias* "Jon"} :meta {:id 2})
         cider-chan1 (test-utils/foo nrepl-session)
         cider-chan2 (test-utils/foo nrepl-session-2)]
     (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Jon~2" :nses []}}
            (:devs (test-utils/cider-msg cider-chan2)))))))
