(ns thornydev.go-lightly.examples.boring.select-basic
  (:require [thornydev.go-lightly.core :as go])
  (:use lamina.core))

(defn- boring [msg]
  (let [ch (go/go-channel)]
    (go/go (loop [i 0]
             (.transfer ch (str msg " " i))
             (Thread/sleep (rand-int 1000))
             (recur (inc i))))
    ch))

(defn select-basic []
  (let [ch (boring "Joe")]
    (loop []
      ;; this specifies a "per round" timeout of 1 sec, 
      ;; *not* a timeout for the cyclic operation as a whole
      (let [msg (go/select-timeout 750 ch)]
        (if (= msg :go-lightly/timeout)
          (println "You're too slow.")
          (do (println msg)
              (recur)))))))
