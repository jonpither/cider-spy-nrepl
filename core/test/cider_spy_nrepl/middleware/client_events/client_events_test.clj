(ns cider-spy-nrepl.middleware.client-events.client-events-test
  (:require [cider-spy-nrepl.hub.client-events :refer :all]
            [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.test :refer :all]))

(deftest test-sequencing
  (testing "Vanilla case"
    (let [session (atom {})
          outgoing (atom [])]
      (swap! session assoc-in [*watched-messages* :jon "id" 1] {:cs-sequence 1})
      (send-out-unsent-messages-if-in-order! session "id" :jon (fn [m] (swap! outgoing conj m)))
      (is (= [1] (map :cs-sequence @outgoing)))))

  (testing "Out of sequence msg gets held"
    (let [session (atom {})
          outgoing (atom [])]
      (swap! session assoc-in [*watched-messages* :jon "id" 3] {:cs-sequence 3})
      (send-out-unsent-messages-if-in-order! session "id" :jon (fn [m] (swap! outgoing conj m)))
      (is (= (nil? (seq @outgoing))))

      (swap! session assoc-in [*watched-messages* :jon "id" 2] {:cs-sequence 2})
      (send-out-unsent-messages-if-in-order! session "id" :jon (fn [m] (swap! outgoing conj m)))
      (is (= (nil? (seq @outgoing))))

      (swap! session assoc-in [*watched-messages* :jon "id" 1] {:cs-sequence 1})
      (send-out-unsent-messages-if-in-order! session "id" :jon (fn [m] (swap! outgoing conj m)))
      (is (= [1 2 3] (map :cs-sequence @outgoing))))))
