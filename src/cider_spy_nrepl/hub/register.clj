(ns cider-spy-nrepl.hub.register
  "Manage HUB registrations."
  (:require [cider-spy-nrepl.ns-trail :as ns-trail])
  (:import (org.joda.time LocalDateTime)))

(def sessions (atom {}))

(defn register!
  "Register the session.
   This will also update the session with session-id and alias."
  [session id alias]
  (swap! session assoc :id id :alias alias)
  (swap! sessions assoc id session))

(defn unregister!
  "Unregister the session."
  [session]
  (swap! sessions dissoc (:id @session)))

(defn update!
  "Updates the session with the given function."
  [session f & args]
  (swap! session #(apply f % args)))

(defn aliases
  "Return aliases of registered sessions."
  []
  (map (comp :alias deref) (vals @sessions)))

(defn session-from-alias [alias]
  (first (filter (comp (partial = alias) :alias deref) (vals @sessions))))

(defn channels
  "Return channels of registered sessions."
  []
  (map (comp :channel deref) (vals @sessions)))

(defn users
  "Return a map of users and the location of where they currently are."
  []
  (into {}
        (for [[id s] @sessions :let [s @s]]
          [id {:alias (:alias s)
               :nses (take 3 (ns-trail/top-nses (LocalDateTime.)
                                                (-> s :tracking :ns-trail)))}])))
