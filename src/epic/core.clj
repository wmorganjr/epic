(ns epic.core
  (:require [clojure.java.io :as io]
            [clojure.string  :as string]
            [cheshire.core   :as json]))

(def cards
  (delay (-> (io/resource "epic.json")
             (slurp)
             (json/decode true))))

(defn dist
  [n min max]
  (loop [ret []]
    (if (= n (count ret))
      ret
      (let [left (dec (- n (count ret)))
            sum  (reduce + ret)
            next (->> (range min (inc max))
                      (filter #(<= (* left min) (+ sum %) (* left max)))
                      (rand-nth))]
        (recur (conj ret next))))))

(def colors->alignment
  {["Black"] :evil
   ["White"] :good
   ["Blue"]  :sage
   ["Green"] :wild})

(def x (->> (select-keys @cards [:TYR :ONE])
            (vals)
            (mapcat :cards)
            (map (fn [card]
                   {:alignment (colors->alignment (:colors card))
                    :name      (:name card)}))
            (group-by :alignment)))

(defn generate
  []
  (for [[k v] (zipmap (shuffle [:good :evil :sage :wild])
                      (map + (repeat 15) (dist 4 -2 2)))
        card (->> (x k)
                  (shuffle)
                  (take v)
                  (sort-by (juxt :alignment :name))
                  (map :name))]
    card))

(println (string/join \newline (generate)))


