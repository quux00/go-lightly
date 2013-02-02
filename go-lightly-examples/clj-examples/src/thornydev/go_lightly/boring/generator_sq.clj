(ns thornydev.go-lightly.boring.generator-sq
  (:require [thornydev.go-lightly.core :as go])
  (:import (java.util.concurrent SynchronousQueue)))


;; ---[ Use a "sync-channel": Java SynchronousQueue ]--- ;;

(defn- boring [msg]
  (let [ch (SynchronousQueue.)]
    (go/go& (loop [i 0]
          (.put ch (str msg " " i))
          (Thread/sleep (rand-int 1000))
          (recur (inc i))))
    ch))


(defn single-generator []
  (let [ch (boring "boring!")]
    (dotimes [_ 5] (println "You say:" (.take ch))))
  (println "You're boring: I'm leaving."))


(defn multiple-generators []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 10]
      (println (.take joe))
      (println (.take ann))))
  (println "You're boring: I'm leaving.")
  )
