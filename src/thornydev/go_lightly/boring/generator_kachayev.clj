(ns thornydev.go-lightly.boring.generator-kachayev
  (:use lamina.core))
;; Channels-driven concurrency with Clojure
;; Clojure variant for code examples from this gist:
;; https://gist.github.com/3124594
;; Primarily taken from Rob Pike's talk on Google I/O 2012:
;; http://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be
;;
;; Concurrency is the key to designing high performance network services. 
;; Clojure provides several concurrency primitives, like futures/promises, atom, agent etc.
;; There is no implementation for "Go channels" in core, but we can use
;; 3rd-party library Lamina to do the same things.
;; 
;; https://github.com/ztellman/lamina
;;
;; I should also mention, that this is not a simple copy of syntax/semantic notations
;; from Go, I tried to create "clojure-like" variant of how to do the same things (with
;; functional approach of data transformation from initial to desired state).

;; (1) Generator: function that returns the channel

(use 'lamina.core)

(defn- boring 
  [name] 
  (let [ch (channel)] 
    ;; future will run separately from main control flow
    (future 
      ;; emit string message five times with random delay
      (dotimes [_ 5] 
        (let [after (int (rand 500))] 
          (Thread/sleep after) 
          (enqueue ch (str name ": I'm boring after " after)))))
    ;; return the channel to caller
    ch))

(defn k1-main1 []
  ;; With single instance
  (let [joe (boring "Joe")] 
    (doseq [msg (channel->lazy-seq (take* 5 joe))] (println msg)))
  (println "You're boring: I'm leaving."))

(defn k1-main2 []
  ;; Process all messages from channel
  ;; Please, note this notation is asynchronous, so...
  (let [joe (boring "Joe")] (receive-all joe println))
  ;; you will see this message first :)
  (println "You're boring: I'm leaving."))

(defn k1-main3 []
  ;; More instances...
  ;; Actually, this is little bit tricky and it's definitely other
  ;; mechanism than we use in Go for this example. It's more 
  ;; likely what we do in "#2 Fan-in" code examples.
  (let [joe (boring "Joe") ann (boring "Ann") chs (channel)] 
    (doseq [ch [joe ann]] (join ch chs))
    (receive-all chs println))
  (println "You're boring: I'm leaving."))

(defn k1-main4 []
  ;; More instances...
  ;; Read from one channel, than - from second
  (let [joe (boring "Joe") ann (boring "Ann")] 
    (loop []
      (doseq [ch [joe ann]]
        ;; TODO: Fix checking for channel closing (this is wrong way)
        (when-not (closed? ch) (println @(read-channel ch))))
      (recur)))
  )

(defn k1-main5 []
  ;; Read from one channel, than - from second
  ;; Several improvements in order to stop execution, 
  ;; when both channels are closed (without any information
  ;; about total count of messages)
  (let [joe (boring "Joe") ann (boring "Ann") run (atom 2)] 
    (loop []
      (doseq [ch [joe ann]]
        ;; TODO: Fix checking for channel closing (this is wrong way)
        (if (closed? ch) 
          (swap! run dec)
          (println @(read-channel ch))))
      (if (> @run  0) (recur))))
  (println "You're boring: I'm leaving."))
