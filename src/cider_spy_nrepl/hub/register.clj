(ns cider-spy-nrepl.hub.register
  "Manage HUB registrations."
  (:require [cider-spy-nrepl.common :as common]
            [cider-spy-nrepl.ns-trail :as ns-trail])
  (:import (org.joda.time LocalDateTime)))

(def sessions (atom {}))

(def update! common/update-atom!)

(defn register!
  "Register the session.
   This will also update the session with session-id and alias."
  [session id alias]
  (update! session assoc :id id :alias alias)
  (update! sessions assoc id session))

(defn unregister!
  "Unregister the session."
  [session]
  (update! sessions dissoc (:id @session)))

(defn aliases
  "Return aliases of registered sessions."
  []
  (map (comp :alias deref) (vals @sessions)))

(defn session-from-alias [alias]
  (first (filter (comp (partial = alias) :alias deref)
                 (vals @sessions))))

(defn channels
  "Return channels of registered sessions."
  []
  (map (comp :channel deref) (vals @sessions)))

(defn users
  "Return a map of users and the location of where they currently are."
  []
  (into {}
        (for [[id s] @sessions]
          (let [alias (:alias @s)
                ns-trail (get-in @s [:tracking :ns-trail])
                nses (take 3 (ns-trail/top-nses (LocalDateTime.) ns-trail))]
            [id {:alias alias :nses nses}]))))
