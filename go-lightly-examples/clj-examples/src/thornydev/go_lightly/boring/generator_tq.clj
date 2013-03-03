(ns thornydev.go-lightly.boring.generator-tq
  (:require [thornydev.go-lightly :refer [go go& stop]])
  (:import (java.util.concurrent LinkedTransferQueue)))


(defn- channel []
  (LinkedTransferQueue.))

(defn- boring [msg]
  (let [ch (channel)]
    (go (loop [i 0]
          (.transfer ch (str msg " " i))
          (Thread/sleep (rand-int 1000))
          (recur (inc i))))
    ch))

(defn single-generator []
  (let [ch (boring "boring!")]
    (dotimes [_ 5] (println "You say:" (.take ch))))
  (println "You're boring: I'm leaving.")
  (stop))

(defn multiple-generators []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 10]
      (println (.take joe))
      (println (.take ann))))
  (println "You're boring: I'm leaving.")
  (stop))



;; ---[ Use the fire-and-forget go& macro ]--- ;;

(defn- boring& [msg]
  (let [ch (channel)]
    (go& (loop [i 0]
           (.transfer ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))

(defn multiple-generators& []
  (let [joe (boring& "Joe&")
        ann (boring& "Ann&")]
    (dotimes [_ 10]
      (println (.take joe))
      (println (.take ann))))
  (println "You're boring: I'm leaving."))
