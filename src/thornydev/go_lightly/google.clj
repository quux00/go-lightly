(ns thornydev.go-lightly.google
  (:require [thornydev.go-lightly.macros :refer [with-channel-open]])
  (:use lamina.core)
  (:import (java.util.concurrent TimeoutException)))

(defn now []
  (System/currentTimeMillis))

(defn since [start]
  (- (now) start))

(defn fake-search [kind]
  (fn [query]
    (Thread/sleep (rand-int 100))
    (format "%s result for '%s'\n" kind query)))

(def web   (fake-search "web"))
(def image (fake-search "image"))
(def video (fake-search "video"))

(defn google-1 [query]
  (reduce #(conj % (%2 query)) [] [web image video]))

(defn google-20f [query]
  (let [futs (doall (mapv #(future (% query)) [web image video]))]
    (reduce #(conj % (deref %2)) [] futs)))

(defn google-20c [query]
  (let [ch (channel)]
    (doseq [qf [web image video]] (future (enqueue ch (qf query))))
    (mapv (fn [_] @(read-channel ch)) (range 3))
    ))

;; function in progress => not finished, nor right
(defn google-21 [query]
  (let [ch (channel)
        timeout 80]
    (doseq [qf [web image video]] (future (enqueue ch (qf query))))
    (let [start (now)]
      (try
        (loop [time-left 80 responses []]
          (if (= 3 (count responses))
            responses
            (if (<= time-left 0)
              (do (println "Timed out") responses)
              (let [response @(with-timeout time-left (read-channel ch))]
                
                )
              )
            )
          )
        (catch TimeoutException e (println "Timed out"))
        )
      )
    )
  )

(defn get-google-fn [version]
  (case version
    :one google-1
    :twof google-20f
    :twoc google-20c
    :2.1 google-21))

(defn google-main [version]
  (let [start (now)
        results ((get-google-fn version) "clojure")
        elapsed (since start)]
    (println results)
    (println elapsed "ms")))







