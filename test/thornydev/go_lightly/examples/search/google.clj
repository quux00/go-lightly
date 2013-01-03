(ns thornydev.go-lightly.examples.search.google
  (:require [thornydev.go-lightly.core :refer [with-channel-open]])
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

;; ---[ Google 1.0 Search ]--- ;;

(defn google-1 [query]
  (reduce #(conj % (%2 query)) [] [web image video]))

;; ---[ Google 2.0 Search ]--- ;;

(defn google-20f [query]
  (let [futs (doall (mapv #(future (% query)) [web image video]))]
    (reduce #(conj % (deref %2)) [] futs)))

(defn google-20c [query]
  (let [ch (channel)]
    (doseq [qf [web image video]] (future (enqueue ch (qf query))))
    (mapv (fn [_] @(read-channel ch)) (range 3))
    ))

;; ---[ Google 2.1 Search ]--- ;;

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

;; ---[ Google 3.0 Search ]--- ;;

;; Pike emphasizes that there are no locks, condition variables or callbacks
;; in his Go version.
;; In my version I (of course) have no locks. I only have condition variables
;; in the complex timeout logic, which I'm hoping that lamina has tools to
;; solve or at worst can be solved with a macro.
;; I do use one callback in the enqueue-first/-take1 fns in order to push
;; from the local channels onto the main channel.  I suspect Go is doing
;; the same thing, but it is hidden by their syntax.  Can lamina do the same?

(defn enqueue-first [main-chan query & replicas]
  (let [local-chan (channel)
        first-result (read-channel local-chan)]
    (doseq [rep replicas] (future (enqueue local-chan (rep query))))
    (on-realized first-result
                 #(enqueue main-chan %) ;; success callback
                 (fn [_] nil))          ;; error callback: do nothing
    ))

;; alternative to the enqueue-first that is (probably)
;; more idiomatic lamina usage
(defn enqueue-first-take1 [main-chain query & replicas]
  (let [local-chan (channel)
        first-result-chan (take* 1 local-chan)]
    (doseq [rep replicas] (future (enqueue local-chan (rep query))))
    (receive-all first-result-chan
                 #(enqueue main-chain %))  ;; callback handler
    ))

(defn google-3-alpha
  "Gets the first of web, image and video but with no timeout.
   Uses enqueue-first which uses on-realized callbacks."
  [query]
  (let [ch (channel)
        qfirst (partial enqueue-first-take1 ch "clojure")]
    (future (qfirst (fake-search "web1")   (fake-search "web2")))
    (future (qfirst (fake-search "image1") (fake-search "image2")))
    (future (qfirst (fake-search "video1") (fake-search "video2")))
    (vec
     (for [_ (range 3)] @(read-channel ch)))))

(defn google-3 [query]
  (let [ch (channel)
        qfirst (partial enqueue-first-take1 ch "clojure")
        timeout 80]
    ;; launch all the "go-routines" (futures)
    (future (qfirst (fake-search "web1")   (fake-search "web2")))
    (future (qfirst (fake-search "image1") (fake-search "image2")))
    (future (qfirst (fake-search "video1") (fake-search "video2")))

    ;; collect responses within a defined timeout window
    (let [start (now)]
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
  )

;; same as google-3, but this one uses read-channel* with a timeout param
;; rather than the lamina with-timeout fn
(defn google-3b [query]
  (let [ch (channel)
        qfirst (partial enqueue-first-take1 ch "clojure")
        timeout 80]
    ;; launch all the "go-routines" (futures)
    (future (qfirst (fake-search "web1")   (fake-search "web2")))
    (future (qfirst (fake-search "image1") (fake-search "image2")))
    (future (qfirst (fake-search "video1") (fake-search "video2")))

    ;; collect responses within a defined timeout window
    (let [start (now)]
      (loop [time-left timeout responses []]
        (if (= 3 (count responses))
          responses
          (if (<= time-left 0)
            (do (println "Timed out") responses)
            (let [timed-res-chan (read-channel* ch :timeout time-left)]
              (try
                @timed-res-chan
                (catch TimeoutException e (println "Timed out")))
              (if (timed-out? timed-res-chan)
                responses
                (recur (- timeout (since start)) (conj responses @timed-res-chan)))))))))  
  )

(defn get-google-fn [version]
  (case version
    :one google-1
    :twof google-20f
    :twoc google-20c
    :2.1 google-21
    :3-alpha google-3-alpha
    :three google-3
    :3b google-3b))

(defn google-main [version]
  (let [start (now)
        results ((get-google-fn version) "clojure")
        elapsed (since start)]
    (println results)
    (println elapsed "ms")))

