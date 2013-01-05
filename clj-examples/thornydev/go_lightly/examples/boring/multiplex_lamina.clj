(ns thornydev.go-lightly.examples.boring.multiplex-lamina
  (:require [thornydev.go-lightly.core :refer [go stop with-channel-open]]
            [lamina.core :refer :all]))

;; Note: if you wanted to make this a pure lamina
;; implementation (no go-lightly), then you would have
;; to manually create a daemon-thread here or create
;; a future and cancel it or use the Java Executor framework
;; to launch and shutdown the "go routines"
(defn- boring [msg]
  (let [ch (channel)]
    (go (loop [i 0]
          (enqueue ch (str msg " " i))
          (Thread/sleep (rand-int 1000))
          (recur (inc i))))
    ch))


;; option 1: create (count chans) threads to
;; to read from each input channel
(defn- fan-in [& chans]
  (let [ch (channel)]
    (doseq [in-chan chans]
      (go (loop []
            (enqueue ch @(read-channel in-chan))
            (recur))))
    ch))


;; option 2: use lamina's join fn to pump events
;; from all input channels to a single output channel
(defn- fan-in-join [& chans]
  (let [out-chan (channel)]
    (doseq [in-chan chans]
      (join in-chan out-chan))
    out-chan))


(defn multiplex []
  (with-channel-open [joe (boring "Joe")
                      ann (boring "Ann")
                      ch (fan-in-join joe ann)]
    (dotimes [_ 10] (println @(read-channel ch)))
    (println "You're both boring: I'm leaving.")
    (stop)))
