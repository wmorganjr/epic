(ns epic.server
  (:require [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.json           :refer [wrap-json-response]]
            [ring.util.response             :refer [response redirect]]
            [compojure.route                :as route]
            [compojure.core                 :refer :all]
            [clojure.string                 :as string]
            [twilio.core                    :as twilio]
            [epic.cube                      :as cube]
            [epic.util                      :as util]))

(defonce state
  (atom {:drafts {}}))

(def req->config
  (let [defaults {"set-names" "Set 1,Uprising,Tyrants"
                  "pack-size" "12"}]
    (fn [req]
      (let [names (-> (:params req)
                      (:player-names)
                      (string/split #",")
                      (shuffle))]
        (-> (merge defaults
                   (:params req)
                   {:player-names names
                    :player-count (count names)})
            (update :set-names string/split #",")
            (update :pack-size #(Integer/parseInt %))
            (select-keys [:set-names :player-count :pack-size :player-names]))))))

(defn twilio-enabled?
  []
  (and (System/getenv "TWILIO_FROM_NUMBER")
       (System/getenv "TWILIO_TO_NUMBER")
       (System/getenv "TWILIO_SID")
       (System/getenv "TWILIO_AUTH_TOKEN")))

(defn text!
  [draft]
  (when (twilio-enabled?)
    (twilio/with-auth (System/getenv "TWILIO_SID")
                      (System/getenv "TWILIO_AUTH_TOKEN")
      (twilio/send-sms
        {:From (System/getenv "TWILIO_FROM_NUMBER")
         :To   (System/getenv "TWILIO_TO_NUMBER")
         :Body (format "A new %s-player draft just started"
                       (:player-count (:config draft)))}))))

(defn new-draft!
  [req]
  (let [draft-id (util/random-uuid)
        draft    (cube/new-draft (req->config req))]
    (if (> (count (:drafts @state)) 1000)
      (throw (Exception. "This is why we can't have nice things"))
      (do (swap! state assoc-in [:drafts draft-id] draft)
          (text! draft)
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

(defn telemetry
  [req]
  (if (= (:password (:params req)) (System/getenv "TELEMETRY_PASSWORD"))
    (response @state)))

(defroutes my-routes
  (POST "/drafts/new" [] new-draft!)
  (GET "/drafts/list" [] list-drafts)
  (GET "/drafts/:draft-id/seats/:seat-id/pack" [] current-pack)
  (GET "/drafts/:draft-id/seats/:seat-id/picks" [] get-picks)
  (GET "/drafts/:draft-id/seats/:seat-id/status" [] status)
  (GET "/zz/telemetry" [] telemetry)
  (POST "/drafts/:draft-id/seats/:seat-id/pick" [] make-pick!)
  (route/resources "/")
  (GET "/" [] (redirect "/about.html")))

(def app
  (-> my-routes
      (wrap-json-response)
      (wrap-keyword-params)
      (wrap-params)))
