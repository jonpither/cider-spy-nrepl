(ns cider-spy-nrepl.connections-test
  (:require [cider-spy-nrepl.hub.register :as register]
            [cider-spy-nrepl.middleware.sessions :as middleware-sessions]
            [cider-spy-nrepl.test-utils :as test-utils]
            [clojure.core.async :refer [alts!! timeout]]
            [clojure.test :refer :all]))

;; TODO worried about never shutting down individual connections to hub
;; TODO test someone in a different session gets registered notice
;; TODO Manually test diff emacs CIDERs with diff aliases connect to same
;;      nrepl-server then on to the hub.
;; TODO fn on client-facade - other users (uses diff between session-ids)

(defn- assert-summary-msg-sent-to-cider-with-user-in [cider-chan & users]
  (let [[msg c] (alts!! [cider-chan (timeout 1000)])]
    (is (= cider-chan c))
    (when (= cider-chan c)
      (doseq [user users]
        (is (re-find (re-pattern user) (str msg)))))))

(defn- close-the-channel [[_ _ c]]
  (-> c .close .sync))

(deftest test-client-should-register-and-unregister
  (test-utils/spy-harness
   (let [cider-chan (test-utils/foo "session1" "jonnyboy")
         session1 (get @middleware-sessions/sessions "session1")]
     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")
     (is (= #{"jonnyboy"} (set (register/aliases))))
     (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1)))))

     (close-the-channel (:hub-client @session1))

     (Thread/sleep 500)

     (is (= #{} (set (register/aliases)))))))

(deftest test-two-registrations-and-unsubscribe
  (test-utils/spy-harness
   (let [cider-chan (test-utils/foo "session1" "jonnyboy")
         session1 (get @middleware-sessions/sessions "session1")]

     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

     (let [cider-chan (test-utils/foo "session2" "frank")
           session2 (get @middleware-sessions/sessions "session2")]

       ;; Ensure frank registered    ;; Ensure jonnyboy registered
       (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy" "frank")
       (is (= #{"jonnyboy" "frank"}
              (set (map (comp :alias val) (:registrations @session1)))))
       (is (= #{"jonnyboy" "frank"}
              (set (map (comp :alias val) (:registrations @session2)))))

       (close-the-channel (:hub-client @session2)))

     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")
     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

     (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1)))))
     (is (= #{"jonnyboy"} (set (register/aliases)))))))

(deftest test-two-registrations-on-different-servers
  (test-utils/spy-harness
   (let [cider-chan (test-utils/foo "session1" "jonnyboy")
         session1 (get @middleware-sessions/sessions "session1")]

     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

     (reset! register/sessions {})
     (let [cider-chan (test-utils/foo "session2" "frank")
           session2 (get @middleware-sessions/sessions "session2")]

       (assert-summary-msg-sent-to-cider-with-user-in cider-chan "frank")

       (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1)))))
       (is (= #{"frank"} (set (map (comp :alias val) (:registrations @session2)))))
       (close-the-channel (:hub-client @session2)))

     (Thread/sleep 500)

     (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1))))))))
