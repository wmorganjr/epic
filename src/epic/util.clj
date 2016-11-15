(ns epic.util)

(defn random-uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn unfrequencies
  [freqs]
  (for [[k v] freqs
        x (repeat v k)]
    x))