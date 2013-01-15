(ns thornydev.go-lightly.examples.whispers.chinese-whispers
  (:require [thornydev.go-lightly.core :as go]))

;; (def ^:const n 100000)

(defn whisper [left right]
  ;; (->> right
  ;;      go/take
  ;;      inc
  ;;      (go/put left))
  ;; (->> (go/take right)
  ;;      inc
  ;;      (go/put left))
  (go/put left (inc (go/take right)))
  )

(defn whispers-main [nthreads]
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

;; (defn whispers-loop []
;;   (let [leftmost (go/channel)
;;         rightmost ] )
;;   )


(defn whispers-test []
  (let [leftmost (go/channel)
        alice    (go/channel)
        bob      (go/channel)
        charlie  (go/channel)
        dani     (go/channel)
        rightmost (go/channel)]
    (println "DEBUG 1")
    (go/go (go/put rightmost 1))
    (println "DEBUG 2")
    (go/go (whisper dani rightmost))
    (go/go (whisper charlie dani))
    (go/go (whisper bob charlie))
    (go/go (whisper alice bob))
    (go/go (whisper leftmost alice))
    (println "DEBUG 3")
    (println (go/take leftmost))
    (println "DEBUG 4")
    )
  )


(defn whispers-test1 []
  (let [leftmost (go/channel)
        rightmost (go/channel)]
    (println "DEBUG 1")
    (go/go (go/put rightmost 1))
    (println "DEBUG 2")
    (go/go (whisper leftmost rightmost))
    (println "DEBUG 3")
    (println (go/take leftmost))
    (println "DEBUG 4")
    )
  )
