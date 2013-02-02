(ns thornydev.go-lightly.examples.sleeping-barber.sandbox
  (:require [thornydev.go-lightly.core :as go]))


(def state {:v []})
(defn h2 [v state] (update-in state [:v] conj (str v :h2)))
(defn h1 [v state] (update-in state [:v] conj (str v :h1)))
(def ch1 (go/channel 5))
(def ch2 (go/channel 5))
(defn do-puts [ch]
  (dotimes [i 10] (Thread/sleep (rand-int 6000)) (go/put ch i)))
(def continue? (atom true))
(def tch (go/timeout-channel 8000))
(go/go (do-puts ch1))
(go/go (do-puts ch2))
(loop [s state]
  (when @continue?
    (println s)
    (->>
     (go/selectf ch1 #(h1 % s)
                 ch2 #(h2 % s)
                 tch (fn [_] (reset! continue? false) s))
     recur)))

(defn engage []
  (go/go (do-puts ch1))
  (go/go (do-puts ch2))
  (loop [i 8 s state]
    (println s)
    (when-not (zero? i)
      (->>
       (go/selectf ch1 #(h1 % s)
                   ch2 #(h2 % s)
                   (go/timeout-channel 1000) #(do (println %) s))
       (recur (dec i)))))
  )

(def continue? (atom true))
(def tch (timeout-channel 8000))
(go/go (do-puts ch1))
(go/go (do-puts ch2))
(loop [s state]
  (when @continue?
    (println s)
    (->>
     (go/selectf ch1 #(h1 % s)
                 ch2 #(h2 % s)
                 tch (fn [_] (reset! continue? false) s))
     recur)))


;; (loop [i 3 s state]
;;   (when-not (zero? i)
;;     (->> (go/selectf ch1 #(h1 % s)
;;                      ch2 #(h2 % s)
;;                      :default (fn [_] s))
;;          (recur (dec i)))))
