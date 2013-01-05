(ns thornydev.go-lightly.examples.boring.multiplex-sq
  (:use thornydev.go-lightly.core)
  (:import (java.util.concurrent SynchronousQueue)))

;; ---[ Use a "sync-channel": Java SynchronousQueue ]--- ;;

(defn- boring [msg]
  (let [ch (SynchronousQueue.)]
    (go #(loop [i 0]
           (.put ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))


(defn- fan-in [& chans]
  (let [ch (SynchronousQueue.)]
    (doseq [in-chan chans]
      (go #(loop []
             (.put ch (.take in-chan))
             (recur))))
    ch))

(defn multiplex []
  (let [ch (fan-in (boring "Joe")
                   (boring "Ann"))]
    (dotimes [_ 10] (println (.take ch)))
    (println "You're both boring: I'm leaving.")
    (stop)))
