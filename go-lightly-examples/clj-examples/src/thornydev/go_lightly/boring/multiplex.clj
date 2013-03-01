(ns thornydev.go-lightly.boring.multiplex
  (:require [thornydev.go-lightly :as go]))

(defn- boring [msg]
  (let [ch (go/channel)]
    (go/go (loop [i 0]
             (.put ch (str msg " " i))
             (Thread/sleep (rand-int 1000))
             (recur (inc i))))
    ch))


(defn- fan-in [& chans]
  (let [ch (go/channel)]
    (doseq [in-chan chans]
      (go/go (loop []
               (.put ch (.take in-chan))
               (recur))))
    ch))

(defn multiplex []
  (let [ch (fan-in (boring "Joe")
                   (boring "Ann"))]
    (dotimes [_ 10] (println (.take ch)))
    (println "You're both boring: I'm leaving.")
    (go/stop)))
