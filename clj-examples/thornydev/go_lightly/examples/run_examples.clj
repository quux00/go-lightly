(ns thornydev.go-lightly.examples.run-examples
  (:require
   [thornydev.go-lightly.examples.boring.boringv1 :as v1]
   [thornydev.go-lightly.examples.boring.generator-kachayev :as genk]
   [thornydev.go-lightly.examples.boring.generator-sq :as gensq]
   [thornydev.go-lightly.examples.boring.generator-tq :as gentq]
   [thornydev.go-lightly.examples.boring.generator-lamina :as genlam]
   [thornydev.go-lightly.examples.boring.multiplex-kachayev :as mk]
   [thornydev.go-lightly.examples.boring.multiplex :as plex]
   [thornydev.go-lightly.examples.boring.multiplex-lamina :as plam]
   [thornydev.go-lightly.examples.boring.multiseq-sq :as ssq]
   [thornydev.go-lightly.examples.search.google :refer :all]
   [thornydev.go-lightly.examples.primes.conc-prime-sieve :refer [sieve-main]])
  (:gen-class))

(defn -main [& args]
  (doseq [arg args]
    (case (keyword (subs arg 1))
      ;; ---[ boring-generators ]--- ;;
      
      :gen-tq1 (gentq/single-generator)
      :gen-tq2 (gentq/multiple-generators)
      :gen-amp (gentq/multiple-generators&)

      :gen-sq1 (gensq/single-generator)
      :gen-sq2 (gensq/multiple-generators)
      :gen-lam1 (genlam/single-generator)
      :gen-lam2 (genlam/multiple-generators)
      
      :plex (plex/multiplex)
      :plex-lam (plam/multiplex)

      :seq-sq (ssq/multiseq)
      
      
      ;; --- [ kaychayev's code ] --- ;;
      :k11 (genk/k1-main1)
      :k12 (genk/k1-main2)
      :k13 (genk/k1-main3)
      :k14 (genk/k1-main4)
      :k15 (genk/k1-main5)
      :k21 (mk/k2-multiplex-any)
      :k22 (mk/k2-multiplex-join)
      
      ;; ---[ simple Pike go examples ]--- ;;
      :one (v1/one)
      :two (v1/two)
      :three (v1/three)
      :four (v1/four)
      :five (v1/five)
      :six (v1/six-two-separate-channels)
      :seven (v1/seven-fan-in)
      :eight (v1/eight-wait-channel)
      :nine (v1/nine-two-wait-channels)
      :ten (v1/ten-forked-wait-channel)

      ;; ---[ google search ]--- ;;
      :google-1 (google-main :one)
      :google-2f (google-main :twof)
      :google-2c (google-main :twoc)
      :google-2.1 (google-main :2.1)
      :google-3-alpha (google-main :3-alpha)
      :google-3 (google-main :three)

      ;; ---[ concurrency prime sieve ]--- ;;
      :primes (sieve-main)
      
      ;; CPU usages is about 4.5% when sleeps are set between
      ;; 10 microseconds up to (and including) 1 millisecond
      ;; 5 millis uses about 1% CPU
      ;; 10 millis uses about 0.7% CPU
      :sleep (do (println "starting")
                 (dotimes [i 14500] (Thread/sleep 0 10000)))

      (println "WARN: argument not recognized"))
    (println "------------------"))
  (shutdown-agents))
