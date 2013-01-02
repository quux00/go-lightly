(ns thornydev.go-lightly.boring.boringv1
  (:require [thornydev.go-lightly.util :refer [with-channel-open]])
  (:use lamina.core))

;; --- [ one ]--- ;;
(defn- boring-one
  "First Pike example with go channels"
  []
  (loop [x 0]
    (Thread/sleep (rand-int 1000))
    (println "boring!" x)
    (recur (inc x))))

(defn one []
  (let [bfut (future (boring-one))]
    (println "I'm listening")
    (Thread/sleep 2000)
    (println "You're boring, I'm leaving.")
    (future-cancel bfut)))

;; --- [ two ]--- ;;

(defn- boring-two []
  (let [ch (channel)]
    (enqueue ch 1)
    (println "Just read from channel:" @(read-channel ch))))

(defn two []
  (boring-two))

;; --- [ three ]--- ;;

(defn- boring-three []
  (let [ch (channel)
        ft (future (loop [x 0]
                     (Thread/sleep (rand-int 1000))
                     (enqueue ch (str "boring! " x))
                     (recur (inc x))))]
    [ch ft]))

(defn three []
  (let [[ch ft] (boring-three)]
    (dotimes [_ 5]
      (println "You say:" @(read-channel ch)))
    (println "You're boring. I'm leaving.")
    (close ch)
    (future-cancel ft)))

;; ---[ four ]--- ;;

(defn- boring-four []
  (let [ch (channel)]
    (future (loop [x 0]
              (Thread/sleep (rand-int 1000))
              (enqueue ch (str "boring! " x))
              (when (not (closed? ch))
                (recur (inc x)))))
    ch))

(defn four []
  (let [ch (boring-four)]
    (dotimes [_ 5]
      (println "You say:" @(read-channel ch)))
    (close ch)
    (println "You're boring. I'm leaving.")))

;; ---[ five ]--- ;;

(defn- boring-five [msg]
  (let [ch (channel)]
    (future (loop [x 0]
              (Thread/sleep (rand-int 1000))
              (enqueue ch (str "boring! " msg " " x))
              (when (not (closed? ch))
                (recur (inc x)))))
    ch))

(defn five []
  (let [joe-ch (boring-five "Joe")
        ann-ch (boring-five "Ann")]
    (dotimes [_ 5]
      (println @(read-channel joe-ch))
      (println @(read-channel ann-ch)))
    (close joe-ch)
    (close ann-ch)
    (println "You're boring. I'm leaving.")))

;; ---[ six ]--- ;;

(defn six-two-separate-channels []
  (with-channel-open [joe-ch (boring-five "Joe")
                      ann-ch (boring-five "Ann")]
    (dotimes [_ 5]
      (println @(read-channel joe-ch))
      (println @(read-channel ann-ch)))
    (println "You're boring. I'm leaving.")))

;; ---[ seven ]--- ;;

(defn- seven-boring [name ch]
  (loop [x 0]
    (Thread/sleep (rand-int 1000))
    (enqueue ch (str name ": " x))
    (when (not (closed? ch))
      (recur (inc x)))))

(defn seven-fan-in
  "want the producers to be able to independently push onto
   the same channel at their own pace"
  []
  (with-channel-open [ch (channel)]
    (future (seven-boring "Joe" ch))
    (future (seven-boring "Ann" ch))
    (dotimes [_ 10]
      (println @(read-channel ch)))))

;; ---[ eight ]--- ;;

(defn- eight-boring [name msg-ch wait-ch]
  (loop [x 0]
    (Thread/sleep (rand-int 1000))
    (enqueue msg-ch (str name ": " x))
    (when (not (closed? msg-ch))
      (when @(read-channel wait-ch)
        (recur (inc x)))))
  )

(defn eight-wait-channel []
  (with-channel-open [msg-ch (channel)
                      wait-ch (channel)]
    (future (eight-boring "Joe" msg-ch wait-ch))
    (future (eight-boring "Ann" msg-ch wait-ch))
    (dotimes [_ 5]
      (println @(read-channel msg-ch))
      (println @(read-channel msg-ch))
      ;; TODO: this is dangerous, one might get two trues in a row rather than alternating
      ;;       better to fork the channel, one for Joe and one for Ann?  How do that?
      (dotimes [_ 2] (enqueue wait-ch true)))
    (dotimes [_ 2] (enqueue wait-ch false))))

(defn nine-two-wait-channels []
  (with-channel-open [msg-ch (channel)
                      joe-wait-ch (channel)
                      ann-wait-ch (channel)]
    (future (eight-boring "Joe" msg-ch joe-wait-ch))
    (future (eight-boring "Ann" msg-ch ann-wait-ch))
    (dotimes [_ 5]
      (println @(read-channel msg-ch))
      (println @(read-channel msg-ch))
      ;; now only have to enqueue "true" once, not twice
      ;; since the fork will take care of delivering it twice
      (enqueue joe-wait-ch true)
      (enqueue ann-wait-ch true))
    (enqueue joe-wait-ch false)
    (enqueue ann-wait-ch false)))


(defn ten-forked-wait-channel []
  (with-channel-open [msg-ch (channel)
                      wait-ch (channel)]
    (future (eight-boring "Joe" msg-ch wait-ch))
    (future (eight-boring "Ann" msg-ch (fork wait-ch)))  ;; give Ann a fork of the channel
    (dotimes [_ 5]
      (println @(read-channel msg-ch))
      (println @(read-channel msg-ch))
      ;; now only have to enqueue "true" once, not twice
      ;; since the fork will take care of delivering it twice
      (enqueue wait-ch true))
    (enqueue wait-ch false)))

