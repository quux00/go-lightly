(ns thornydev.go-lightly.boring.select-kachayev
  (:require [lamina.core :as lam]))


(defn- boring [msg]
  (let [ch (lam/channel)]
    (future (loop [i 0]
              (lam/enqueue ch (str msg " " i))
              (Thread/sleep (rand-int 1000))
              (recur (inc i))))
    ch))


;; Adapted from gist from Alexey Kachayev:
;; https://gist.github.com/3146759#file-clojure-channels-3-select-clj

;; Unfortunately, this runs forever with no way to shut it down
(defn select-lamina []
  (let [joe (boring "Joe")
        ;; Will generate messages each 60 ms
        timer (lam/periodically 60 (fn [] "You're too slow!"))
        ;; All channels will be joined with this one
        select (lam/channel)]
    
    (doseq [[t ch] [["joe" joe] ["timer" timer]]]
      ;; Map message to tuple [type message]
      (lam/join (lam/map* (partial conj [t]) ch) select))

    ;; Read from channel until it's not closed (in blocking mode)
    (lam/receive-all select
                     (fn [[name msg]]
                       (println
                        (str msg
                             (case name
                               "joe"   " <== Message from Joe"
                               "timer" " <== Timeout")))))))
