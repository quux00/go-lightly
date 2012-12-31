(ns thornydev.go-lightly.goboring-generator-lamina
  (:use [thornydev.go-lightly.util]
        [lamina.core]))

(defn- boring [msg]
  (let [ch (channel)]
    (go #(loop [i 0]
           (enqueue ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))

(defn single-generator []
  (let [ch (boring "boring!")]
    (dotimes [_ 5] (println "You say:" @(read-channel ch))))
  (println "You're boring: I'm leaving.")
  (stop))


(defn multiple-generators []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 10]
      (println @(read-channel joe))
      (println @(read-channel ann))))
  (println "You're boring: I'm leaving.")
  (stop))
