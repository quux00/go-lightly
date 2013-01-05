(ns thornydev.go-lightly.examples.boring.select-lamina
  (:require [thornydev.go-lightly.core :refer [go& with-channel-open]]
            [lamina.core :as lam]))


(defn- boring [msg]
  (let [ch (lam/channel)]
    (go& (loop [i 0]
           (lam/enqueue ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))


;; Adapted from gist from Alexey Kachayev:
;; https://gist.github.com/3146759#file-clojure-channels-3-select-clj

(defn select-lamina []
  (let [joe (boring "Joe")
        ;; Will generate messages each 60 ms
        timer (lam/periodically 60 (fn [] "You're too slow!"))
        ;; All channels will be joined with this one
        select (lam/channel)]
    
    (doseq [[t ch] [["joe" joe] ["timer" timer]]]
      ;; Map message to tuple [type message]
      (lam/join (lam/map* (partial conj [t]) ch) select))

    ;; TODO: need to use (read-channel to cycle through one at a time?
    ;;       or should we create a lam-select fn/macro ?
    ;;       or is join the main replacement for select and the rest
    ;;       is just looping?
    (lam/receive-all select
                     (fn [[name msg]]
                       (println
                        (str msg
                             (case name
                               "joe"   " <== Message from Joe"
                               "timer" " <== Timeout")))))
    (future-cancel joe)
    (future-cancel timer))
  (shutdown-agents)
  )
