(ns thornydev.go-lightly.examples.boring.generator-lamina
  (:require
   [thornydev.go-lightly.core :refer [go&]]
   [thornydev.go-lightly.examples.util :refer [with-channel-open]]
   [lamina.core :refer [channel enqueue close
                        closed? read-channel]]))

(defn- boring [msg]
  (let [ch (channel)]
    (go& (loop [i 0]
           (when-not (closed? ch)
             (let [status (enqueue ch (str msg " " i))]
               (when-not (= :lamina/closed! status)
                 (Thread/sleep (rand-int 1000))
                 (recur (inc i)))))))
    ch))

(defn single-generator []
  (with-channel-open [ch (boring "boring!")]
    (dotimes [_ 5] (println "You say:" @(read-channel ch))))
  (println "You're boring: I'm leaving."))

(defn multiple-generators []
  (with-channel-open [joe (boring "Joe")
                      ann (boring "Ann")]
    (dotimes [_ 10]
      (println @(read-channel joe))
      (println @(read-channel ann))))
  (println "You're boring: I'm leaving."))
