(defproject epic "0.1.0"
  :dependencies [[org.clojure/clojure "1.8.0"]                 
                 [compojure "1.5.1"]
                 [twilio-api "1.0.1"]
                 [ring/ring-json "0.4.0"]
                 [cheshire "5.6.3"]]
  :ring {:handler epic.server/app
         :nrepl {:start? true}}
  :plugins [[lein-ring "0.9.7"]])
