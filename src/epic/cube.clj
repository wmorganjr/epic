(ns epic.cube
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [epic.util :as util]))

(def cards
  (->> (io/resource "epic.json")
       (slurp)
       (json/decode)
       (vals)
       (mapcat #(% "cards"))))

(defn cube-pool
  [set-names]
  (let [set-names (set set-names)]
    (for [card (filter #(set-names (% "Set Name")) cards) 
          copy (repeat (case (card "Gem Color (cube rarity)")
                         "Common" 3
                         "Rare" 1)
                       card)]
      copy)))

(defn cube-packs
  [{:keys [set-names player-count pack-size]}]
  (->> (cube-pool set-names)
       (shuffle)
       (take (* player-count pack-size 3))
       (map #(% "name"))
       (partition pack-size)
       (map frequencies)
       (partition 3)))

(defn new-draft
  [{:keys [player-count set-names pack-size] :as config}]
  (let [pool (cube-pool set-names)]
    (if (< (count pool) (* player-count pack-size 3))
      (throw (Exception. "Not enough cards in pool to start draft."))))
  {:config config
   :packs  (cube-packs config)
   :seats  (zipmap (repeatedly player-count util/random-uuid) (range))
   :picks  (vec (repeat player-count []))})

(defn upstream
  [draft seat-number n]
  (rem (+ (- seat-number n) (* (count (:seats draft)) 55))
       (count (:seats draft))))

(defn remove-picks-from-pack
  [pack picks]
  (merge-with - pack
                (frequencies picks)))

(defn initial-pack-contents
  [draft seat round]
  (nth (nth (:packs draft) seat) round))

(defn picks-from-pack
  [draft starting-seat round]
  (->> (:picks draft)
       (map #(vec (drop (* (:pack-size (:config draft)) round) %)))
       ((if (even? round) identity reverse))
       (cycle)
       (drop (if (even? round)
               starting-seat
               (- (dec (:player-count (:config draft))) starting-seat)))
       (map #(get %2 %1) (range))
       (take-while some?)))

(defn cards-still-in-pack
  [draft seat]
  (let [pick-count (count (get-in draft [:picks seat]))
        round      (int (/ pick-count (:pack-size (:config draft))))
        pack-picks (rem pick-count (:pack-size (:config draft)))
        pack-seat  (upstream draft seat (* pack-picks (if (even? round) 1 -1)))
        picks      (picks-from-pack draft pack-seat round)]
    (if (= (count picks) pack-picks)
      (remove-picks-from-pack (initial-pack-contents draft pack-seat round)
                              picks))))

(defn add-pick
  [draft seat card]
  (update-in draft [:picks seat] conj card))

(defn picks
  [draft seat]
  (get-in draft [:picks seat]))

