(ns cider-spy-nrepl.tracker
  (:import [org.joda.time LocalDateTime]))

(def messages (atom '())) ;; Used for debugging

(def files-loaded (atom '{}))
(def trail-atom (atom '()))
(def commands-atom (atom '{}))
(def session-started (LocalDateTime.))

(defn- safe-inc [v]
  (if v (inc v) 1))

(defn- track-namespace
  "Add message to supplied tracking.
   The user namespace is ignored."
  [trail {:keys [ns] :as msg}]
  (if (and ns (not= "user" (:ns msg)) (not= (:ns msg) (-> trail first :ns)))
    (conj trail {:dt (LocalDateTime.) :ns ns})
    trail))

;; TODO this is downright dangerous..
(defn- track-command
  "Add message to supplied tracking."
  [command-frequencies {:keys [code] :as msg}]
  (let [forms (and code
                   (not (re-find #"^\(try\n?\s*\(:arglists\n?\s*\(clojure\.core/meta" code))
                   (not (re-find #"^\(try\n?\s*\(eval\n?\s*\(quote\n?\s*\(clojure.repl/doc" code))
                   (not (re-find #"^\(defn? " code))
                   (read-string (format "(%s)" code)))]
    (if (= (count forms) 1)
      (update-in command-frequencies [(first forms)] safe-inc)
      command-frequencies)))

;; TODO Need to extract the namespace out of the file being loaded to make this more useful
(defn- track-load-file [files-loaded {:keys [op file-path] :as msg}]
  (if (= "load-file" op)
    (update-in files-loaded [file-path] safe-inc)
    files-loaded))

(defn track-msg! [msg]
  (swap! messages conj msg)
  (swap! trail-atom track-namespace msg)
  (swap! commands-atom track-command msg)
  (swap! files-loaded track-load-file msg))
