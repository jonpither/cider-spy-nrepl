(ns cider-spy-nrepl.ns-trail
  (import [org.joda.time LocalDateTime Seconds]))

(defn- time-weight [seconds]
  (let [minutes (/ seconds 60)]
    (cond (< minutes 15) 100
          (< minutes 30) 70
          (< minutes 60) 50
          (< minutes 240) 30
          :else 10)))

(defn- score [now most-recent-visit]
  (let [seconds (.getSeconds (Seconds/secondsBetween (LocalDateTime. most-recent-visit) now))]
    (double (/ (time-weight seconds) 100))))

(defn top-nses [now ns-trail]
  (->> ns-trail
       (map #(hash-map (:ns %) (score now (:dt %))))
       (apply merge-with +)
       (sort-by val)
       (reverse)
       (map key)))
