(ns thornydev.go-lightly.examples.boring.generator-tq
  (:require [thornydev.go-lightly.core :refer :all]))

;; ---[ Use the go macro that requires a stop ]--- ;;

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





;; ---[ sandbox for learning LinkedTransferQueue ]--- ;;

(defn prf [& vals]
  (println (apply str (interpose " " (map #(if (nil? %) "nil" %) vals))))
  (flush))

(defn engage []
  (let [ch (channel)]
    (prf "before")
    (prf "size:" (.size ch))
    (prf "peek:" (.peek ch))
    (prf "poll:" (.poll ch))
    
    (let [fut (future (do (.transfer ch 22)
                          (prf ">> transferred 22")
                          (.transfer ch 33)
                          (prf ">> transferred 33")))]
      (Thread/sleep 2422)
      (prf "after")
      (prf "size:" (.size ch))
      (prf "peek:" (.peek ch))
      (prf "poll:" (.poll ch))
      (Thread/sleep 2422)
      (prf "size:" (.size ch))
      (prf "peek:" (.peek ch))
      (prf "poll:" (.poll ch))
      (Thread/sleep 2422)
      (prf "size:" (.size ch))
      (prf "peek:" (.peek ch))
      (prf "poll:" (.poll ch)))))
