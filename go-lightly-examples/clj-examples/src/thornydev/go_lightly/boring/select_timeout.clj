(ns thornydev.go-lightly.boring.select-timeout
  (:refer-clojure :exclude [peek take])  
  (:require [thornydev.go-lightly :refer
             [go stop put take select select-timeout
              channel timeout-channel with-timeout]]))

(defn- boring [msg]
  (let [ch (channel)]
    (go (loop [i 0]
          (put ch (str msg " " i))
          (Thread/sleep (rand-int 1000))
          (recur (inc i))))
    ch))

;; simple example using select to choose
;; the next available message - repeat 5 times
(defn select-between-Joe-and-Ann []
  (let [joe-ch (boring "Joe")
        ann-ch (boring "Ann")]
    (dotimes [_ 5]
      (let [msg (select joe-ch ann-ch)]
        (println msg))))
  (stop))


;; use select-timeout to specify a "per round" timeout of 1 sec, 
;; *not* a timeout for the cyclic operation as a whole
(defn select-timeout-per-round []
  (let [ch (boring "Joe")]
    (loop []
      (let [msg (select-timeout 750 ch)]
        (if msg
          (do (println msg)
              (recur))
          (println "You're too slow.")))))
  (stop))

;; use a timeout-channel to constrain the conversation
;; to a max of 2500 milliseconds (plus some slop time)
(defn select-timeout-whole-conversation-v1 []
  (let [joe-ch (boring "Joe")
        ann-ch (boring "Ann")
        timed-ch (timeout-channel 2000)]
    (loop []
      (let [msg (select joe-ch ann-ch timed-ch)]
        (if (= msg :go-lightly/timeout)
          (println "You talk too much!")
          (do (println msg)
              (recur))))))
  (stop))


(defn select-timeout-whole-conversation-v2 []
  (let [joe-ch (boring "Joe")
        ann-ch (boring "Ann")]
    (with-timeout 2000
      (loop []
        (println (select joe-ch ann-ch))
        (recur))
      (println "You talk too much!")))
  (stop))
