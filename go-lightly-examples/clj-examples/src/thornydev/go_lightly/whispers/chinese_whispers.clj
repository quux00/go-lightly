(ns thornydev.go-lightly.whispers.chinese-whispers
  (:require [thornydev.go-lightly :as go]))

(defn whisper [left right]
  (go/put left (inc (go/take right))))

(defn whispers-main
  "build from left to right - all constructed before starts"
  [nthreads]
  (let [n (Integer/valueOf nthreads)
        leftmost (go/channel)
        rightmost (loop [left leftmost i (dec n)]
                    (let [right (go/channel)]
                      (go/go (whisper left right))
                      (if (zero? i)
                        right
                        (recur right (dec i)))))]
    (go/go (go/put rightmost 1))
    (println (go/take leftmost)))
  (go/stop))

(defn whispers-as-you-go
  "build from right to left - construct the go routines as you need
   them and let the threads finish to be reused by other go routines"
  [nthreads]
  (let [n (Integer/valueOf nthreads)
        rightmost (go/channel)
        _         (go/go (go/put rightmost 1))
        leftmost (loop [right rightmost i (dec n)]
                   (let [left (go/channel)]
                     (go/go& (whisper left right))
                     (if (zero? i)
                       left
                       (recur left (dec i)))))]
    (println (go/take leftmost)))
  (go/stop))
