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

(defn timed-out?
  "This is a hack to parse the toString output of a timed ResultChannel
   to see if it has errored by timeout"
  [result-chan]
  (>= (.indexOf (str result-chan) ":lamina/timeout!") 0))

;; clunky implementation on multiple levels
;; TODO: need to study lamina code further to find a better way
(defn google-21 [query]
  (let [ch (channel)
        timeout 80]
    ;; launch all the "go-routines" (futures)
    (doseq [qf [web image video]] (future (enqueue ch (qf query))))

    ;; collect responses within a defined timeout window
    (let [start (now)] ()
      (loop [time-left timeout responses []]
        (if (= 3 (count responses))
          responses
          (if (<= time-left 0)
            (do (println "Timed out") responses)
            (let [timed-chan (with-timeout time-left (read-channel ch))]
              (try
                @timed-chan
                (catch TimeoutException e (println "Timed out")))
              (if (timed-out? timed-chan)
                responses
                (recur (- timeout (since start)) (conj responses @timed-chan))))))))))

(defn first-responder [query & replicas]
  (let [ch (channel)
        first-result (read-channel ch)]
    (doseq [rep replicas] (future (enqueue ch (rep query))))
    ;; TODO: what do here?
    )
  )

(defn enqueue-first [main-chan query & replicas]
  (let [local-chan (channel)
        first-result (read-channel local-chan)]
    (doseq [rep replicas] (future (enqueue local-chan (rep query))))
    (on-realized first-result
                 #(enqueue main-chan %) ;; success callback
                 (fn [_] nil))          ;; error callback: do nothing
    ))

;; try to solve this with (take* 1) for the next impl
(defn google-3-alpha
  "Gets the first of web, image and video but with no timeout.
   Uses enqueue-first which uses on-realized callbacks."
  [query]
  (let [ch (channel)]
    (future (enqueue-first ch "clojure" (fake-search "web1")   (fake-search "web2")))
    (future (enqueue-first ch "clojure" (fake-search "image1") (fake-search "image2")))
    (future (enqueue-first ch "clojure" (fake-search "video1") (fake-search "video2")))
    (vec
     (for [_ (range 3)] @(read-channel ch)))))

(comment
  (defn google-3 [query]
    (let [ch (channel)
          timeout 80]
      ;; launch all the "go-routines" (futures)
      (future (enqueue-first ch "clojure" (fake-search "web1")   (fake-search "web2")))
      (future (enqueue-first ch "clojure" (fake-search "image1") (fake-search "image2")))
      (future (enqueue-first ch "clojure" (fake-search "video1") (fake-search "video2")))

      ;; collect responses within a defined timeout window
      (let [start (now)] ()
           (loop [time-left timeout responses []]
             (if (= 3 (count responses))
               responses
               (if (<= time-left 0)
                 (do (println "Timed out") responses)
                 (let [timed-chan (with-timeout time-left (read-channel ch))]
                   (try
                     @timed-chan
                     (catch TimeoutException e (println "Timed out")))
                   (if (timed-out? timed-chan)
                     responses
                     (recur (- timeout (since start)) (conj responses @timed-chan)))))))))  
    ))

(defn get-google-fn [version]
  (case version
    :one google-1
    :twof google-20f
    :twoc google-20c
    :2.1 google-21
    :3-alpha google-3-alpha))

(defn google-main [version]
  (let [start (now)
        results ((get-google-fn version) "clojure")
        elapsed (since start)]
    (println results)
    (println elapsed "ms")))

