(ns thornydev.go-lightly.examples.primes.conc-prime-sieve
  (:import (java.util.concurrent SynchronousQueue)))

;; A Clojure implementation of the Concurrent Primary Sieve
;; example in Go, using Java SynchronousQueue's
;; => could not use a Lamina channel, bcs it uses
;; ConcurrentLinkedQueue, which is unbounded and non-blocking
;; from: http://play.golang.org/p/9U22NfrXeq

(def inventory (atom []))

(defn go [func]
  (let [fut (future (func))]
    (swap! inventory conj fut)))

(defn shutdown []
  (doseq [f @inventory] (future-cancel f))
  (shutdown-agents))

(defn sync-channel [] (SynchronousQueue.))

(defn generate
  "send the sequence 2,3,4 ... to the Queue q"
  [^SynchronousQueue q]
  (doseq [i (iterate inc 2)]
    (.put q i)))


(defn filter-primes [^SynchronousQueue qin ^SynchronousQueue qout prime]
  (loop [i (.take qin)]
    (when-not (zero? (mod i prime))
      (.put qout i))
    (recur (.take qin))))


(defn sieve-main [& args]
  (let [^SynchronousQueue qfirst (sync-channel)]
    (go #(generate qfirst))
    (loop [i 10 q qfirst]
      (when (pos? i)
        (let [prime (.take q)
              ^SynchronousQueue q1 (sync-channel)]
          (println prime)
          (go #(filter-primes q q1 prime))
          (recur (dec i) q1)))))
  (shutdown))
