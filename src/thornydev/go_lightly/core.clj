(ns thornydev.go-lightly.core
  (:use lamina.core)
  (:gen-class))

(defn boring-one
  "First Pike example with go channels"
  []
  (loop [x 0]
    (Thread/sleep (rand-int 1000))
    (println "boring!" x)
    (recur (inc x))))

(defn one []
  (let [bfut (future (boring-one))]
    (println "I'm listening")
    (Thread/sleep 2000)
    (println "You're boring, I'm leaving.")
    (future-cancel bfut)))

(defn boring-two []
  (let [ch (channel)]
    (enqueue ch 1)
    (println "Just read from channel:" @(read-channel ch))))

(defn two []
  (boring-two))

(defn -main [& args]
  (one)
  (two)
  (shutdown-agents)
  )

