(ns thornydev.go-lightly.boring.multiplex-lamina
  (:use [thornydev.go-lightly.util]
        [lamina.core]))

(defn- boring [msg]
  (let [ch (channel)]
    (go #(loop [i 0]
           (enqueue ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))


;; option 1: create (count chans) threads to
;; to read from each input channel
(defn- fan-in [& chans]
  (let [ch (channel)]
    (doseq [in-chan chans]
      (go #(loop []
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
  (let [ch (fan-in-join (boring "Joe")
                        (boring "Ann"))]
    (dotimes [_ 10] (println @(read-channel ch)))
    (println "You're both boring: I'm leaving.")
    (stop)))
