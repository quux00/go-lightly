(ns thornydev.go-lightly.core
  (:require [thornydev.go-lightly.macros :refer [with-channel-open]])
  (:use lamina.core)
  (:gen-class))

;; --- [ one ]--- ;;
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

;; --- [ two ]--- ;;

(defn boring-two []
  (let [ch (channel)]
    (enqueue ch 1)
    (println "Just read from channel:" @(read-channel ch))))

(defn two []
  (boring-two))

;; --- [ three ]--- ;;

(defn boring-three []
  (let [ch (channel)
        ft (future (loop [x 0]
                     (Thread/sleep (rand-int 1000))
                     (enqueue ch (str "boring! " x))
                     (recur (inc x))))]
    [ch ft]))

(defn three []
  (let [[ch ft] (boring-three)]
    (dotimes [_ 5]
      (println "You say:" @(read-channel ch)))
    (println "You're boring. I'm leaving.")
    (close ch)
    (future-cancel ft)))

(defn boring-four []
  (let [ch (channel)]
    (future (loop [x 0]
              (Thread/sleep (rand-int 1000))
              (enqueue ch (str "boring! " x))
              (when (not (closed? ch))
                (recur (inc x)))))
    ch))

(defn four []
  (let [ch (boring-four)]
    (dotimes [_ 5]
      (println "You say:" @(read-channel ch)))
    (close ch)
    (println "You're boring. I'm leaving.")))

(defn boring-five [msg]
  (let [ch (channel)]
    (future (loop [x 0]
              (Thread/sleep (rand-int 1000))
              (enqueue ch (str "boring! " msg " " x))
              (when (not (closed? ch))
                (recur (inc x)))))
    ch))

(defn five []
  (let [joe-ch (boring-five "Joe")
        ann-ch (boring-five "Ann")]
    (dotimes [_ 5]
      (println @(read-channel joe-ch))
      (println @(read-channel ann-ch)))
    (close joe-ch)
    (close ann-ch)
    (println "You're boring. I'm leaving.")))

(defn six []
  (with-channel-open [joe-ch (boring-five "Joe")
                      ann-ch (boring-five "Ann")]
    (dotimes [_ 5]
      (println @(read-channel joe-ch))
      (println @(read-channel ann-ch)))
    (println "You're boring. I'm leaving.")))

(defn -main [& args]
  (five)
  (shutdown-agents))
