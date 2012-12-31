(ns thornydev.go-lightly.core
  (:require [thornydev.go-lightly.boring :refer :all]
            [thornydev.go-lightly.kachayev1 :refer :all]
            [thornydev.go-lightly.goboring-generator :as gen]
            [thornydev.go-lightly.goboring-generator-lamina :as genlam]
            [thornydev.go-lightly.google :refer :all]
            [thornydev.go-lightly.conc-prime-sieve :refer [sieve-main]])
  (:gen-class))

(defn -main [& args]
  (doseq [arg args]
    (case (keyword (subs arg 1))
      :gen1 (gen/single-generator)
      :gen2 (gen/multiple-generators)
      :gen1lam (genlam/single-generator)
      :gen2lam (genlam/multiple-generators)
      
      ;; ---[ simple Pike go examples ]--- ;;
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

      ;; --- [ kaychayev's code ] --- ;;
      :k1 (k1-main1)
      :k2 (k1-main2)
      :k3 (k1-main3)
      :k4 (k1-main4)
      :k5 (k1-main5)
      
      ;; ---[ google search ]--- ;;
      :google-1 (google-main :one)
      :google-2f (google-main :twof)
      :google-2c (google-main :twoc)
      :google-2.1 (google-main :2.1)
      :google-3-alpha (google-main :3-alpha)
      :google-3 (google-main :three)

      ;; ---[ concurrency prime sieve ]--- ;;
      :primes (sieve-main)
      
      (println "WARN: argument not recognized"))
    (println "------------------"))
  (shutdown-agents))
