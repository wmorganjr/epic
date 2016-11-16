(ns epic.server
  (:require [clojure.string :as string]
            [epic.cube      :as cube]
            [epic.util      :as util]))

(require '[ring.middleware.json :refer [wrap-json-response]]
         '[ring.util.response :refer [response]]) 

(require '[compojure.core :refer :all])
(require '[compojure.route :as route])

(require '[ring.middleware.params :refer [wrap-params]])
(require '[ring.middleware.keyword-params :refer [wrap-keyword-params]])

(defonce state
  (atom {:drafts {}}))

(def req->config
  (let [defaults {"set-names" "Set 1,Uprising,Tyrants"
                  "pack-size" "12"
                  "player-count" "8"}]
    (fn [req]
      (-> (merge defaults (:params req))
        (update :player-names string/split #",")
        (update :player-names shuffle)
        (update :set-names string/split #",")
        (update :player-count #(Integer/parseInt %))
        (update :pack-size #(Integer/parseInt %))
        (select-keys [:set-names :player-count :pack-size :player-names])))))

(defn new-draft!
  [req]
  (let [draft-id (util/random-uuid)
        draft    (cube/new-draft (req->config req))]
    (if (> (count (:drafts @state)) 1000)
      (throw (Exception. "This is why we can't have nice things"))
      (do (swap! state assoc-in [:drafts draft-id] draft)
          (response {:draft-id draft-id
                     :players  (for [[player [seat-id _]] (zipmap (:player-names (:config draft))
                                                                  (:seats draft))]
                                 {:player player
                                  :seat seat-id
                                  :url (format "http://epicdraft.club/index.html?draftId=%s&seatId=%s" draft-id seat-id)})
                     :seats    (:seats draft)})))))

(defn list-drafts
  [_]
  (response {:drafts (keys (:drafts @state))}))

(defn current-pack
  [req]
  (let [{:keys [draft-id seat-id]} (:params req)]
    (if-let [seat (get-in @state [:drafts draft-id :seats seat-id])]
      (-> (cube/cards-still-in-pack (get-in @state [:drafts draft-id]) seat)
          (util/unfrequencies)
          (sort)
          (response))
      "No match")))
  
(defn make-pick!
  [req]
  (let [{:keys [draft-id seat-id card]} (:params req)]
    (swap! state (fn [s]
                   (if-let [seat (get-in s [:drafts draft-id :seats seat-id])]
                     (let [pack (cube/cards-still-in-pack (get-in s [:drafts draft-id]) seat)]
                       (if-let [number (get pack card)]
                         (if (pos? number)
                           (update-in s [:drafts draft-id] cube/add-pick seat card)
                           s)
                         s))
                     s)))))

(defn get-picks
  [req]
  (let [{:keys [draft-id seat-id]} (:params req)]
    (if-let [seat (get-in @state [:drafts draft-id :seats seat-id])]
      (response (get-in @state [:drafts draft-id :picks seat])))))

(defn status
  [req]
  (let [{:keys [draft-id seat-id]} (:params req)]
    (if-let [seat (get-in @state [:drafts draft-id :seats seat-id])]
      (response
        {:picks (get-in @state [:drafts draft-id :picks seat])
         :pack  (-> (cube/cards-still-in-pack (get-in @state [:drafts draft-id]) seat)
                    (util/unfrequencies)
                    (sort))
         :table {:config (get-in @state [:drafts draft-id :config])
                 :seat  seat
                 :picks (map count (get-in @state [:drafts draft-id :picks]))
                 :time  (System/currentTimeMillis)}}))))

(defroutes my-routes
  (POST "/drafts/new" [] new-draft!)
  (GET "/drafts/list" [] list-drafts)
  (GET "/drafts/:draft-id/seats/:seat-id/pack" [] current-pack)
  (GET "/drafts/:draft-id/seats/:seat-id/picks" [] get-picks)
  (GET "/drafts/:draft-id/seats/:seat-id/status" [] status)
  (POST "/drafts/:draft-id/seats/:seat-id/pick" [] make-pick!)
  (route/resources "/"))

(def app
  (-> my-routes
      (wrap-json-response)
      (wrap-keyword-params)
      (wrap-params)))


