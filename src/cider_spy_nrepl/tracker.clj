(ns cider-spy-nrepl.tracker
  (:import [org.joda.time LocalDateTime]))

(defn- safe-inc [v]
  (if v (inc v) 1))

(defn track-namespace
  "Add message to supplied tracking.
   The user namespace is ignored."
  [trail {:keys [ns] :as msg}]
  (if (and ns (not= "user" (:ns msg)) (not= (:ns msg) (-> trail first :ns)))
    (conj trail {:dt (LocalDateTime.) :ns ns})
    trail))

(defn track-command
  "Add message to supplied tracking."
  [command-frequencies {:keys [code op] :as msg}]
  (if (and code (not= "load-file" op)
           (not (re-find #"^\(try\n?\s*\(:arglists\n?\s*\(clojure\.core/meta" code))
           (not (re-find #"^\(try\n?\s*\(eval\n?\s*\(quote\n?\s*\(clojure.repl/doc" code))
           (not (re-find #"^\(defn?-? " code))
           (read-string (format "(%s)" code)))
    (update-in command-frequencies [code] safe-inc)
    command-frequencies))

;; TODO Need to extract the namespace out of the file being loaded to make this more useful
(defn- track-load-file [files-loaded {:keys [op file-path] :as msg}]
  (if (= "load-file" op)
    (update-in files-loaded [file-path] safe-inc)
    files-loaded))

(def trackers
  [[:files-loaded #'track-load-file {}]
   [:ns-trail #'track-namespace '()]
   [:commands #'track-command {}]
   [:messages #'conj '()]])

(defn- apply-trackers [session msg]
  (reduce (fn [s [k f init-val]] (update-in s [k] #(f (or %1 init-val) msg)))
          session trackers))

(defn track-msg! [msg session]
  (swap! session apply-trackers msg))
