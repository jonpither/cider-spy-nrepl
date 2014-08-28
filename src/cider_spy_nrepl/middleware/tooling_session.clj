(ns cider-spy-nrepl.middleware.tooling-session
  "A namespace to detect and filter out CIDER REPL tooling sessions.
   Otherwise the tooling session pollutes tracking, and makes it harder
   to tie NREPL sessions to users.
   This hacky strategy of looking at the contents of the evals is OK
   because the tooling session is being phased out of CIDER altogether
   as the tooling operations are moved into NREPL middleware.
   See the corresponding test ns for examples of CIDER tooling code.")

(def tooling-sessions (atom '#{}))

(defn tooling-msg? [{:keys [op code]}]
  (and (= "eval" op)
       (or
        (re-find #"\(clojure\.core\/apply clojure\.core\/require" code)
        (re-find #"\(ns clojure\.test\.mode" code)
        (re-find #"\(clojure\.core\/require \'complete\.core\)" code)
        (re-find #"\(require 'complete\.core\)" code)
        (re-find #"\(clojure\.core\/binding \[clojure\.core" code))))

(defn- new-tooling-session [{:keys [session] :as msg}]
;;  (println "Marking tooling session:" msg)
  ((swap! tooling-sessions conj session) session))

(defn tooling-session?
  "Determine if the session in this message is for tooling, or
   has historically been tagged as tooling.
   This fn uses an atom which may be updated."
  [{:keys [session] :as msg}]
  (or (@tooling-sessions session)
      (and (tooling-msg? msg)
           (new-tooling-session msg))))
