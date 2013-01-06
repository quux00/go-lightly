(ns thornydev.go-lightly.examples.primes.conc-prime-sieve
  (:require [thornydev.go-lightly.core :as go]))

;; A Clojure implementation of the Concurrent Primary Sieve
;; example in Go, using the go-lightly library
;; Note: could not use a Lamina channel for this particular
;;       implementation, bcs it uses ConcurrentLinkedQueue,
;;       which is unbounded and non-blocking

;; Based on Go implementation at:
;; http://play.golang.org/p/9U22NfrXeq

(defn generate
  "send the sequence 2,3,4 ... to the channel"
  [channel]
  (doseq [i (iterate inc 2)]
    (.transfer channel i)))
 

(defn filter-primes [cin cout prime]
  (loop [i (.take cin)]
    (when-not (zero? (mod i prime))
      (.transfer cout i))
    (recur (.take cin))))

(defn sieve-main [& args]
  (let [chfirst (go/go-channel)]
    (go/go (generate chfirst))
    (loop [i 10 ch chfirst]
      (when (pos? i)
        (let [prime (.take ch)
              ch1 (go/go-channel)]
          (println prime)
          (go/go (filter-primes ch ch1 prime))
          (recur (dec i) ch1)))))
  (go/shutdown))
