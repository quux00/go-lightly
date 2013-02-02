(ns thornydev.go-lightly.boring.select-basic
  (:require [thornydev.go-lightly.core :as go]))

(defn- boring [msg]
  (let [ch (go/channel)]
    (go/go (loop [i 0]
             (.transfer ch (str msg " " i))
             (Thread/sleep (rand-int 1000))
             (recur (inc i))))
    ch))

;; simple example using go/select to choose
;; the next available message - repeat 5 times
(defn select-between-Joe-and-Ann []
  (let [joe-ch (boring "Joe")
        ann-ch (boring "Ann")]
    (dotimes [_ 5]
      (let [msg (go/select joe-ch ann-ch)]
        (println msg))))
  (go/stop))


;; use select-timeout to specify a "per round" timeout of 1 sec, 
;; *not* a timeout for the cyclic operation as a whole
(defn select-timeout-per-round []
  (let [ch (boring "Joe")]
    (loop []
      (let [msg (go/select-timeout 750 ch)]
        (if (= msg :go-lightly/timeout)
          (println "You're too slow.")
          (do (println msg)
              (recur))))))
  (go/stop))

;; use a timeout-channel to constrain the conversation
;; to a max of 2500 milliseconds (plus some slop time)
(defn select-timeout-whole-conversation-v1 []
  (let [joe-ch (boring "Joe")
        ann-ch (boring "Ann")
        timed-ch (go/timeout-channel 2000)]
    (loop []
      (let [msg (go/select joe-ch ann-ch timed-ch)]
        (if (= msg :go-lightly/timeout)
          (println "You talk too much!")
          (do (println msg)
              (recur))))))
  (go/stop))


(defn select-timeout-whole-conversation-v2 []
  (let [joe-ch (boring "Joe")
        ann-ch (boring "Ann")]
    (go/with-timeout 2000
      (loop []
        (println (go/select joe-ch ann-ch))
        (recur))
      (println "You talk too much!")))
  (go/stop))
