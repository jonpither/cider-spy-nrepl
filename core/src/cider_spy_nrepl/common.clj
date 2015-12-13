(ns cider-spy-nrepl.common)

(defn update-atom!
  "Updates the atom with the given function."
  [atom f & args]
  (swap! atom #(apply f % args)))
