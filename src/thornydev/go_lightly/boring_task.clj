(ns thornydev.go-lightly.boring-task
  (:require [thornydev.go-lightly.macros :refer [with-channel-open]])
  (:use [lamina.core]
        [lamina.executor]))

(defn- boring-one
  "First Pike example with go channels"
  []
  (loop [x 0]
    (Thread/sleep (rand-int 1000))
    (println "boring!" x)
    (recur (inc x))))

(defn task-one []
  (let [tsk (task (boring-one))]
    (println "I'm listening")
    (Thread/sleep 2000)
    (println "You're boring, I'm leaving!")
    ))
