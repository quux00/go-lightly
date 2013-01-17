(ns thornydev.go-lightly.examples.boring.generator
  (:require [thornydev.go-lightly.core :as go]))

;; ---[ Use the go macro that requires a stop ]--- ;;

(defn- boring [msg]
  (let [ch (go/channel)]
    (go/go (loop [i 0]
             (go/put ch (str msg " " i))
             (Thread/sleep (rand-int 1000))
             (recur (inc i))))
    ch))

(defn single-generator []
  (let [ch (boring "boring!")]
    (dotimes [_ 5] (println "You say:" (go/take ch))))
  (println "You're boring: I'm leaving.")
  (go/stop))

(defn multiple-generators []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 10]
      (println (go/take joe))
      (println (go/take ann))))
  (println "You're boring: I'm leaving.")
  (go/stop))



;; ---[ Use the fire-and-forget go& macro ]--- ;;

(defn- boring& [msg]
  (let [ch (go/channel)]
    (go/go& (loop [i 0]
           (go/put ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))

(defn multiple-generators& []
  (let [joe (boring& "Joe&")
        ann (boring& "Ann&")]
    (dotimes [_ 10]
      (println (go/take joe))
      (println (go/take ann))))
  (println "You're boring: I'm leaving."))





;; ---[ sandbox for learning LinkedTransferQueue ]--- ;;

(defn prf [& vals]
  (println (apply str (interpose " " (map #(if (nil? %) "nil" %) vals))))
  (flush))
