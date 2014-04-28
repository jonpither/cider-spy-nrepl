(ns cider-spy-nrepl.hub.register
  "Manage HUB registrations."
  (:require [cider-spy-nrepl.ns-trail :as ns-trail])
  (:import [org.joda.time LocalDateTime]))

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

(defn update-location!
  "Update a users location."
  [session ns dt]
  (swap! session update-in [:tracking :ns-trail] conj {:dt (LocalDateTime. dt) :ns ns}))

(defn aliases
  "Return aliases of registered sessions."
  []
  (map (comp :alias deref) (vals @sessions)))

(defn users
  "Return a map of users and the location of where they currently are."
  []
  (into {}
        (for [[id s] @sessions :let [s @s]]
          [(:alias s) (take 3 (ns-trail/top-nses (LocalDateTime.) (-> s :tracking :ns-trail)))])))
