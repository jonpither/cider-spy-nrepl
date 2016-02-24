(ns cider-spy-nrepl.connections-test
  (:require [cider-spy-nrepl.hub.register :as register]
            [cider-spy-nrepl.test-utils :as test-utils]
            [clojure.core.async :refer [alts!! timeout]]
            [cider-spy-nrepl.middleware.session-vars :refer [*hub-client* *registrations* *desired-alias*]]
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
   (let [nrepl-session (atom {#'*desired-alias* "jonnyboy"} :meta {:id 1})
         cider-chan (test-utils/foo nrepl-session)]
     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")
     (is (= #{"jonnyboy"} (set (register/aliases))))
     (is (= #{"jonnyboy"} (set (map (comp :alias val) (@nrepl-session #'*registrations*)))))

     (close-the-channel (@nrepl-session #'*hub-client*))

     (Thread/sleep 500)

     (is (= #{} (set (register/aliases)))))))

(deftest test-two-registrations-and-unsubscribe
  (test-utils/spy-harness
   (let [session1 (atom {#'*desired-alias* "jonnyboy"} :meta {:id 1})
         cider-chan (test-utils/foo session1)]

     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

     (let [session2 (atom {#'*desired-alias* "frank"} :meta {:id 2})
           cider-chan (test-utils/foo session2)]

       ;; Ensure frank registered    ;; Ensure jonnyboy registered
       (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy" "frank")
       (is (= #{"jonnyboy" "frank"}
              (set (map (comp :alias val) (@session1 #'*registrations*)))))
       (is (= #{"jonnyboy" "frank"}
              (set (map (comp :alias val) (@session2 #'*registrations*)))))

       (close-the-channel (@session2 #'*hub-client*)))

     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")
     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

     (is (= #{"jonnyboy"} (set (map (comp :alias val) (@session1 #'*registrations*)))))
     (is (= #{"jonnyboy"} (set (register/aliases)))))))

(deftest test-two-registrations-on-different-servers
  (test-utils/spy-harness
   (let [session1 (atom {#'*desired-alias* "jonnyboy"} :meta {:id 1})
         cider-chan (test-utils/foo session1)]

     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

     (reset! register/sessions {})
     (let [session2 (atom {#'*desired-alias* "frank"} :meta {:id 2})
           cider-chan (test-utils/foo session2)]

       (assert-summary-msg-sent-to-cider-with-user-in cider-chan "frank")

       (is (= #{"jonnyboy"} (set (map (comp :alias val) (@session1 #'*registrations*)))))
       (is (= #{"frank"} (set (map (comp :alias val) (@session2 #'*registrations*)))))
       (close-the-channel (@session2 #'*hub-client*)))

     (Thread/sleep 500)

     (is (= #{"jonnyboy"} (set (map (comp :alias val) (@session1 #'*registrations*))))))))
