(ns cider-spy-nrepl.middleware.favourite
  (import [org.joda.time LocalDateTime Seconds]))

;; 100 < 15
;; 70 < 30
;; 50 < 1hr
;; 30 < 4hr
;; 10 > 4hr

;; time (i.e. where is in bucket, 15 mins, 30 mins, 1 hr, 4hr)
;; number of hits

(defn- time-weight [seconds]
  (let [minutes (/ seconds 60)]
    (cond (< minutes 15) 100
          (< minutes 30) 70
          (< minutes 60) 50
          (< minutes 240) 30
          :else 10)))

(def now (LocalDateTime. 2010 1 1 0 30 0))

(defn score [most-recent-visit]
  (let [;;now (LocalDateTime.)
        seconds (.getSeconds (Seconds/secondsBetween most-recent-visit now))]
    (double (/ (time-weight seconds) 100))))

(score (LocalDateTime. 2010 1 1 0 0 30))

(def test-data
  [{:dt (LocalDateTime. 2010 1 1 0 5 0) :ns "boo"}
   {:dt (LocalDateTime. 2010 1 1 0 26 0) :ns "boo"}
   {:dt (LocalDateTime. 2010 1 1 0 27 0) :ns "boo"}
   {:dt (LocalDateTime. 2010 1 1 0 27 0) :ns "boo2"}])

(apply merge-with + (map #(hash-map (:ns %) (score (:dt %))) test-data))

;; We score each visit and add them up, thn order to get the top
