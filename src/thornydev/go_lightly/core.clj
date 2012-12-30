(ns thornydev.go-lightly.core
  (:require [thornydev.go-lightly.boring :refer :all]
            [thornydev.go-lightly.google :refer :all])
  (:gen-class))

(defn -main [& args]
  (doseq [arg args]
    (case (keyword (subs arg 1))
      :one (one)
      :two (two)
      :three (three)
      :four (four)
      :five (five)
      :six (six-two-separate-channels)
      :seven (seven-fan-in)
      :eight (eight-wait-channel)
      :nine (nine-two-wait-channels)
      :ten (ten-forked-wait-channel)
      :google-1 (google-main :one)
      :google-2f (google-main :twof)
      :google-2c (google-main :twoc)
      :google-2.1 (google-main :2.1)
      :google-2-alpha (google-main :3-alpha)
      (println "WARN: argument not recognized"))
    (println "------------------"))
  (shutdown-agents))
