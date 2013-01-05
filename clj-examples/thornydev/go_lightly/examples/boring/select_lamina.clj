(ns thornydev.go-lightly.examples.boring.select-lamina
  (:require [thornydev.go-lightly.core :as go]
            [lamina.core :refer [close] :as lam])
  (:import (java.util.concurrent TimeoutException)))


(defn- boring [msg]
  (let [ch (lam/channel)]
    (go/go& (loop [i 0]
           (lam/enqueue ch (str msg " " i))
           (Thread/sleep (rand-int 1000))
           (recur (inc i))))
    ch))

(defn- fan-in [& chans]
  (let [out-chan (lam/channel)]
    (doseq [in-chan chans]
      (lam/join in-chan out-chan))
    out-chan))

;; Ideas here adapted from:
;; https://gist.github.com/3146759#file-clojure-channels-4-timeouts-clj


;; here we use a general purpose with-timeout macro
;; from the go-lightly project
;; Note: lamina has its own with-timeout macro, but that
;;       is for doing a single read from an individual ResultChannel
;;       not a general purpose timeout operation
(defn select-lamina-timeout-whole-conversation-v1 []
  (go/with-channel-open [joe (boring "Joe")
                         ann (boring "Ann")
                         ch (fan-in joe ann)]
    (go/with-timeout 1000
      (doseq [msg (lam/channel->lazy-seq (lam/take* 100 ch))]
        (println msg))))
  (println "You talk too much."))


;; do whole-conversation timeout by using periodically
;; to pump a timeout message into the downstream channel
(defn select-lamina-timeout-whole-conversation-v2 []
  (go/with-channel-open
    [joe (boring "Joe")
     ann (boring "Ann")
     timer (lam/periodically 1000 (fn [] :too-slow))
     ch (fan-in joe ann timer)]

    (loop [msg @(lam/read-channel ch)]
      (when-not (= msg :too-slow)
        (println msg)
        (recur @(lam/read-channel ch))))
    (println "You talk too much.")))


(defn select-lamina-timeout-per-round-v1 []
  (go/with-channel-open [joe (boring "Joe")
                         ann (boring "Ann")
                         ch (fan-in joe ann)] 
    (try
      (dotimes [_ 100]
        (println @(lam/read-channel* ch :timeout 500)))
      (catch TimeoutException e
        (println "You're boring: I'm leaving.")))))

(defn select-lamina-timeout-per-round-v2 []
  (go/with-channel-open [joe (boring "Joe")
                         ann (boring "Ann")
                         ch (fan-in joe ann)] 
    ;; 2nd param is a timeout arg (in millis)
    ;; each msg retrieval will wait up to 500 millis 
    (doseq [msg (lam/channel->lazy-seq (lam/take* 100 ch) 500)]
      (println msg)))
  (println "You're boring: I'm leaving."))
