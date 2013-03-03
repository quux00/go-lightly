(ns thornydev.go-lightly.primes.conc-prime-sieve
  (:refer-clojure :exclude [take peek])
  (:require [thornydev.go-lightly :refer :all]))

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
    (put channel i)))

(defn filter-primes [cin cout prime]
  (loop [i (take cin)]
    (when-not (zero? (mod i prime))
      (put cout i))
    (recur (take cin))))

(defn sieve-main [& args]
  (let [chfirst (channel)]
    (go (generate chfirst))
    (loop [i (Integer/valueOf (or (first args) 10))
           ch chfirst]
      (when (pos? i)
        (let [prime (take ch)
              ch1 (channel)]
          (println prime)
          (go (filter-primes ch ch1 prime))
          (recur (dec i) ch1)))))
  (stop))
