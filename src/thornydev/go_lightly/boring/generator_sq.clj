(ns thornydev.go-lightly.boring.generator-sq
  (:use thornydev.go-lightly.util))


;; ---[ Use a "sync-channel": Java SynchronousQueue ]--- ;;

(defn- boring [msg]
  (let [ch (sync-channel)]
    (go& (loop [i 0]
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
