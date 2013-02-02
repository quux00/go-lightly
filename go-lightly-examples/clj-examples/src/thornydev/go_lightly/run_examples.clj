(ns thornydev.go-lightly.examples.run-examples
  (:require
   [thornydev.go-lightly.examples.boring.boringv1 :as v1]
   [thornydev.go-lightly.examples.boring.generator-kachayev :as genk]
   [thornydev.go-lightly.examples.boring.generator-sq :as gensq]
   [thornydev.go-lightly.examples.boring.generator :as gengo]
   [thornydev.go-lightly.examples.boring.generator-lamina :as genlam]
   [thornydev.go-lightly.examples.boring.multiplex-kachayev :as mk]
   [thornydev.go-lightly.examples.boring.multiplex :as plex]
   [thornydev.go-lightly.examples.boring.multiplex-lamina :as plam]
   [thornydev.go-lightly.examples.boring.multiseq-sq :as ssq]
   [thornydev.go-lightly.examples.search.google-lamina :as googlam]
   [thornydev.go-lightly.examples.search.google :as goog]
   [thornydev.go-lightly.examples.primes.conc-prime-sieve :refer [sieve-main]]
   [thornydev.go-lightly.examples.webcrawler.webcrawler :as crawl]
   [thornydev.go-lightly.examples.whispers.chinese-whispers :as whisp]
   [thornydev.go-lightly.examples.load-balancer.balancer :as bal])
  (:gen-class))

(declare run-programs)

(defn run-programs [args]
  (doseq [arg args]
    (case (keyword (subs arg 1))
      ;; ---[ "boring" variations ]--- ;;
      :gen1 (gengo/single-generator)
      :gen2 (gengo/multiple-generators)
      :gen-amp (gengo/multiple-generators&)

      :gen-sq1 (gensq/single-generator)
      :gen-sq2 (gensq/multiple-generators)
      :gen-lam1 (genlam/single-generator)
      :gen-lam2 (genlam/multiple-generators)
      
      :plex (plex/multiplex)
      :plex-lam (plam/multiplex)

      :seq-sq (ssq/multiseq)
      
      ;; --- [ kachayev's code ] --- ;;
      :k11 (genk/k1-main1)
      :k12 (genk/k1-main2)
      :k13 (genk/k1-main3)
      :k14 (genk/k1-main4)
      :k15 (genk/k1-main5)
      :k21 (mk/k2-multiplex-any)
      :k22 (mk/k2-multiplex-join)
      
      ;; ---[ original simple Pike go examples before go-lightly library ]--- ;;
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
      ;; go-lightly version
      :goog1.0  (goog/google-main :goog1.0)
      :goog2.0  (goog/google-main :goog2.0)
      :goog2.1  (goog/google-main :goog2.1)
      :goog2.1b (goog/google-main :goog2.1b)
      :goog3.0  (goog/google-main :goog3.0)

      ;; (clunky) lamina version
      :googlam1.0 (googlam/google-main :googlam1.0)
      :googlam2.0f (googlam/google-main :googlam2.0f)
      :googlam2.0c (googlam/google-main :googlam2.0c)
      :googlam2.1 (googlam/google-main :googlam2.1)
      :googlam3-alpha (googlam/google-main :googlam3-alpha)
      :googlam3.0 (googlam/google-main :googlam3.0)
      :googlam3.0b (googlam/google-main :googlam3.0b)

      ;; ---[ concurrency prime sieve ]--- ;;
      :primes (sieve-main)

      (println "WARN: argument not recognized"))
    (println "------------------"))
  )

(defn -main [& args]
  (cond
    ;; ---[ webcrawler ]--- ;;
    ;; can take up to three optional args after :webcrawler
    ;;  arg1: number of crawler go threads (defaults to 1)
    ;;  arg2: duration (in millis) to run crawling (defaults to 2000)
    ;;  arg3: initial url to crawl (defaults to http://golang.org/ref/)
    ;; example: lein run :webcrawler 16 30000
   (= ":webcrawler" (first args)) (apply crawl/-main (rest args))

   ;; ---[ chinese-whispers ]--- ;;
   ;; after :whipers or :whispers-as-go, first arg is number of threads to start
   ;; if you give these a lot of threads it takes a while to shut down
   ;; after the value prints out
   (= ":whispers" (first args)) (apply whisp/whispers-main (rest args))
   (= ":whispers-as-go" (first args)) (apply whisp/whispers-main (rest args))

   ;; ---[ load-balancer ]--- ;;
   ;; after :balancer,
   ;;  arg1: number of worker go routines
   ;;  arg2: number of requests to process before shutting down
   (= ":balancer" (first args)) (apply bal/-main (rest args))
   :else (run-programs args))
  
  (shutdown-agents))
