(ns thornydev.go-lightly.examples.boring.multiplex-kachayev
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

;; (2) Fan-in

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

;; "Hand-made" one
(defn- fan-in-manual
  [input1 input2] 
  (let [ch (channel) pusher (partial enqueue ch)] 
    (doseq [x [input1 input2]] (receive-all x pusher)) ch))

;; Or any count of inputs instead of just 2
(defn- fan-in-any
  [& inputs] 
  (let [ch (channel) pusher (partial enqueue ch)] 
    (doseq [x inputs] (receive-all x pusher)) ch))

;; Or more "clojurian" approach with join
(defn- fan-in-join
  [& inputs] 
  (let [ch (channel)] (doseq [x inputs] (join x ch)) ch))

(defn k2-multiplex-any []
  (let [ch (apply fan-in-any (map boring ["Joe" "Ann"]))] 
    (receive-all (take* 10 ch) println))
  (println "You're both boring: I'm leaving."))

(defn k2-multiplex-join []
  (let [ch (apply fan-in-join (map boring ["Joe" "Ann"]))] 
    (receive-all (take* 10 ch) println))
  (println "You're both boring: I'm leaving."))

;; Or any times something will be pushed to channel
;; (let [ch (apply fan-in (map boring ["Joe" "Ann"]))] (receive-all ch println))
