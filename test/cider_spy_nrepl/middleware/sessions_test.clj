(ns cider-spy-nrepl.middleware.sessions-test
  (:require [cider-spy-nrepl.middleware.sessions :refer :all]
            [clojure.test :refer :all]))

(deftest foo-test
  (let [nrepl-session (atom {})
        cider-spy-session (session! {:session nrepl-session})]
    (swap! cider-spy-session assoc :a :c)
    (is (= :c (:a @(session! {:session nrepl-session}))))))
